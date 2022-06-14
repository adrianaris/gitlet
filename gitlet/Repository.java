package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static gitlet.Utils.*;


/**
 * This class is the core of gitlet repository.
 * It defines the functionality of each command that Main calls.
 * It orchestrates between Main class and the underlying DS of
 * gitlet's structure.
 *
 *  @author Adrian Serbanescu
 */
public class Repository {
    public static String AUTHOR = "Adrian Serbanescu";
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The branches directory saves the heads of each branch we create. */
    public static final File BRANCHES = join(GITLET_DIR, "branches");
    /** This dir persists each commit object. */
    public static final File COMMITS = join(GITLET_DIR, "commits");
    /** This dir persists all versions of the repository's files. */
    public static final File FILES = join(COMMITS, "files");
    /** This file keeps track of which commit is currently active. */
    public static File head = join(GITLET_DIR, "HEAD");
    public static File activeBranch = join(BRANCHES, "current");
    public static File STAGING_AREA = join(GITLET_DIR, "INDEX");

    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println(
                    "A Gitlet version-control system already" +
                            "exists in the current directory.");
            System.exit(0);
        }
        GITLET_DIR.mkdir();
        COMMITS.mkdir();
        BRANCHES.mkdir();
        FILES.mkdir();
        Commit initCommit = new Commit(
                "initial commit",
                AUTHOR,
                null,
                null);

        File newCommit = join(COMMITS, initCommit.id);
        File branch = join(BRANCHES, "master");
        writeObject(newCommit, initCommit);
        writeContents(branch, initCommit.id);
        writeContents(head, initCommit.id);
        writeContents(activeBranch, "master");
        writeObject(STAGING_AREA, new StagingArea());
    }

    public static void add(String fileName) {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        List<String> currentFiles = plainFilenamesIn(CWD);
        Commit currentCommit = checkOutCommit(readContentsAsString(head));
        HashMap<String, String> currentCommitFileMap =
                currentCommit.getFiles();
        if (currentFiles == null) {
            throw new GitletException("CWD is empty");
        }
        if (currentFiles.contains(fileName)) {
            File file = join(CWD, fileName);
            String contents = readContentsAsString(file);
            String sha1 = sha1(contents);
            String commitedSHA = currentCommitFileMap == null
                    ? null
                    : currentCommitFileMap.get(fileName);
            if (!sha1.equals(commitedSHA)) {
                stagingArea.map.put(fileName, contents);
                writeObject(STAGING_AREA, stagingArea);
            }
        } else {
            throw new GitletException("File does not exist.");
        }
    }

    public static void commit(String message) {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        if (stagingArea.map.isEmpty()) {
            throw new GitletException("No changes added to the commit.");
        }
        Commit parentCommit = checkOutCommit(readContentsAsString(head));
        HashMap<String, String> newCommitFiles = parentCommit.isEmpty()
                ? new HashMap<>()
                : parentCommit.getFiles();
        for (Map.Entry<String, String> set : stagingArea.map.entrySet()) {
            String contents = set.getValue();
            String fileName = set.getKey();
            if (contents == null) {
                newCommitFiles.remove(fileName);
                restrictedDelete(join(CWD, fileName));
            } else {
                String sha = sha1(contents);
                File shaFile = join(FILES, sha);
                writeContents(shaFile, contents);
                newCommitFiles.put(fileName, sha);
            }
        }
        Commit newCommit = new Commit(
            message,
            AUTHOR,
            parentCommit.id,
            newCommitFiles
        );
        File commit = join(COMMITS, newCommit.id);
        File branch = join(BRANCHES, readContentsAsString(activeBranch));
        System.out.println(newCommit.id);
        System.out.println(parentCommit.id);
        writeObject(commit, newCommit);
        writeContents(branch, newCommit.id);
        writeContents(head, newCommit.id);
        stagingArea.map.clear();
        writeObject(STAGING_AREA, stagingArea);
    }

    public static void rm(String fileName) {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        Commit currentCommit = checkOutCommit(readContentsAsString(head));
        HashMap<String, String> commitedFiles = currentCommit.getFiles();
        if (!stagingArea.map.containsKey(fileName)
                && !commitedFiles.containsKey(fileName)) {
            throw new GitletException("No reason to remove the file.");
        }
        stagingArea.map.put(fileName, null);
        writeObject(STAGING_AREA, stagingArea);
    }

    public static void log() {
        Commit currentCommit = checkOutCommit(readContentsAsString(head));
        log(currentCommit);
    }

    private static void log(Commit commit) {
        if (commit == null) {
            System.exit(1);
        }
        commit.print();
        String parentId = commit.getParent();
        Commit parent = checkOutCommit(parentId);
        log(parent);
    }

    public static void globalLog() {
        List<String> commits = plainFilenamesIn(COMMITS);
        for (String commit : commits) {
            Commit com = checkOutCommit(commit);
            com.print();
        }
    }

    public static void find(String message) {
        List<String> commits = plainFilenamesIn(COMMITS);
        int found = 0;
        for (String commit : commits) {
            Commit com = checkOutCommit(commit);
            if (message.equals(com.getMessage())) {
                System.out.println(commit);
            }
            found++;
        }
        if (found == 0) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void status() {
        if (!GITLET_DIR.exists()) {
            throw new GitletException("No gitlet version control exists" +
                    " in the current directory.");
        }
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        Commit currentCommit = checkOutCommit(readContentsAsString(head));
        HashMap<String, String> commitedFiles = currentCommit.getFiles();
        List<String> branches = plainFilenamesIn(BRANCHES);
        String currentBranch = readContentsAsString(join(BRANCHES, "current"));
        List<String> currentFiles = plainFilenamesIn(CWD);

        StringBuilder stagedFiles = new StringBuilder();
        StringBuilder removedFiles = new StringBuilder();
        StringBuilder modificationsNotStaged = new StringBuilder();
        StringBuilder untrackedFiles = new StringBuilder();

        for (Map.Entry<String, String> set: stagingArea.map.entrySet()) {
            String fileName = set.getKey();
            if (set.getValue() == null) {
                removedFiles.append(fileName).append("\n");
            } else {
                stagedFiles.append(fileName).append("\n");
            }
            if (currentFiles != null && !currentFiles.contains(fileName)) {
                modificationsNotStaged.append(fileName)
                        .append(" (deleted)").append("\n");
            } else if (currentFiles != null
                    && currentFiles.contains(fileName)
                    && !sha1(set.getValue()).equals(
                            sha1(readContentsAsString(
                                            join(CWD, fileName))))) {
                modificationsNotStaged.append(fileName)
                        .append(" (modified)").append("\n");
            }
        }
        assert currentFiles != null;
        for (String fileName : currentFiles) {
            if (commitedFiles == null
                    || !commitedFiles.containsKey(fileName)
                    && !stagingArea.map.containsKey(fileName)) {
                untrackedFiles.append(fileName).append("\n");
            } else if (commitedFiles.containsKey(fileName)
                    && !stagingArea.map.containsKey(fileName)
                    && !commitedFiles.get(fileName).equals(
                            sha1(readContentsAsString(
                                            join(CWD, fileName))))) {
                modificationsNotStaged.append(fileName)
                        .append(" (modified)").append("\n");
            }
        }
        System.out.println("=== Branches ===");
        for (String branch : branches) {
            if (!branch.equals("current")) {
                if (branch.equals(currentBranch)) {
                    System.out.println("*" + branch);
                } else {
                    System.out.println(branch);
                }
            }
        }
        System.out.println();
        System.out.println("=== Staged Files ===");
        System.out.println(stagedFiles);
        System.out.println("=== Removed Files ");
        System.out.println(removedFiles);
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println(modificationsNotStaged);
        System.out.println("=== Untracked Files ===");
        System.out.println(untrackedFiles);
    }

    public static void checkOutFileInCommit(String sha1, String fileName) {
        String commitID = sha1;
        if (sha1.length() < 40) {
            commitID = checkSha(sha1);
        }
        HashMap<String, String> commitFiles =
                checkOutCommit(commitID).getFiles();
        if (commitFiles.containsKey(fileName)) {
            File file = join(CWD, fileName);
            writeContents(file, commitFiles.get(fileName));
        } else {
            throw new GitletException("File does not exist in that commit.");
        }
    }

    public static void checkOutBranch(String branchName) {
        List<String> branches = plainFilenamesIn(BRANCHES);
        assert branches != null;
        if (branches.contains(branchName)) {
            HashMap<String, String> currentFiles =
                    checkOutCommit(readContentsAsString(head)).getFiles();
            String branchID = readContentsAsString(join(BRANCHES, branchName));
            HashMap<String, String> branchFiles =
                    checkOutCommit(branchID).getFiles();
            writeContents(head, branchID);
            for (Map.Entry<String, String> set : branchFiles.entrySet()) {
                File file = join(CWD, set.getKey());
                writeContents(file, set.getValue());
                currentFiles.remove(set.getKey());
            }
            for (Map.Entry<String, String> set: currentFiles.entrySet()) {
                join(CWD, set.getKey()).delete();
            }
        }
    }

    // Helper method to check-out a commit.
    private static Commit checkOutCommit(String sha1) {
        if (sha1 == null) {
            return null;
        }
        File commitFile = join(COMMITS, sha1);
        if (!commitFile.exists()) {
            throw new GitletException("Commit doesn't exist");
        }
        return readObject(commitFile, Commit.class);
    }

    // Helper method for incomplete commit IDs.
    private static String checkSha(String sha1) {
        String commitID = sha1;
        List<String> commits = plainFilenamesIn(COMMITS);
        for (String sha : commits) {
            if (commitID == sha.substring(0, commitID.length())) {
                return sha;
            }
        }
        throw new GitletException("No commit with that id exists");
    }

    // Helper class for staging.
    // Perhaps I should experiment with singleton Class!!!
    public static class StagingArea implements Serializable {
        public HashMap<String, String> map;
        public StagingArea() {
            map = new HashMap<>();
        }
    }
}
