package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
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

        File newCommitDir = join(COMMITS, initCommit.id.substring(0, 4));
        newCommitDir.mkdir();
        File newCommit = join(newCommitDir, initCommit.id.substring(4));
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
        File commit = createCommitFile(newCommit.id);
        File branch = join(BRANCHES, readContentsAsString(activeBranch));
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
        commit.print();
        String parentId = commit.getParent();
        if (parentId != null) {
            Commit parent = checkOutCommit(parentId);
            log(parent);
        }
    }

    public static void globalLog() {
        List<String> commits = plainFilenamesIn(COMMITS);
        assert commits != null;
        for (String commit : commits) {
            Commit com = checkOutCommit(commit);
            com.print();
        }
    }

    public static void find(String message) {
        List<String> commits = plainFilenamesIn(COMMITS);
        int found = 0;
        assert commits != null;
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
        assert branches != null;
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

    public static void checkOutFileInHead(String fileName) {
        String headCommit = readContentsAsString(head);
        checkOutFileInCommit(headCommit, fileName);
    }

    public static void checkOutFileInCommit(String sha1, String fileName) {
        HashMap<String, String> commitFiles =
                checkOutCommit(sha1).getFiles();
        if (commitFiles.containsKey(fileName)) {
            File file = join(CWD, fileName);
            File commitedFile = join(FILES, commitFiles.get(fileName));
            writeContents(file, readContentsAsString(commitedFile));
        } else {
            throw new GitletException("File does not exist in that commit.");
        }
    }

    public static void checkOutBranch(String branchName) {
        if (readContentsAsString(activeBranch).equals(branchName)) {
            throw new GitletException("No need to checkout the current branch");
        }
        File branch = join(BRANCHES, branchName);
        if (branch.exists()) {
            String branchID = readContentsAsString(branch);
            switchActiveCommit(branchID);
            writeContents(join(BRANCHES, "current"), branchName);
        } else {
            throw new GitletException("No such branch exists.");
        }
    }

    // Helper method that replaces active CWD files with the version
    // of files in the provided commit.
    private static void switchActiveCommit(String commitID) {
        HashMap<String, String> activeCommitFiles =
                checkOutCommit(readContentsAsString(head)).getFiles();
        HashMap<String, String> replaceFiles =
                checkOutCommit(commitID).getFiles();
        List<String> currentFiles = plainFilenamesIn(CWD);
        for (String file : currentFiles) {
            if (!activeCommitFiles.containsKey(file) &&
                    replaceFiles.containsKey(file)) {
                throw new GitletException("There is an untracked file in the" +
                        " way; delete it, or add and commit it first.");
            }
        }
        writeContents(head, commitID);
        for (Map.Entry<String, String> set : replaceFiles.entrySet()) {
            File file = join(CWD, set.getKey());
            writeContents(file, set.getValue());
            activeCommitFiles.remove(set.getKey());
        }
        for (Map.Entry<String, String> set: activeCommitFiles.entrySet()) {
            restrictedDelete(join(CWD, set.getKey()));
        }
    }

    public static void branch(String branchName) {
        List<String> currentBranches = plainFilenamesIn(BRANCHES);
        assert currentBranches != null;
        if (currentBranches.contains(branchName)) {
            throw new GitletException("A branch with that name already exists.");
        }
        File branch = join(BRANCHES, branchName);
        writeContents(branch, readContentsAsString(head));
        writeContents(activeBranch, branchName);
    }

    public static void rm_branch(String branchName) {
        String currentBranch = readContentsAsString(join(BRANCHES, "current"));
        if (currentBranch.equals(branchName)) {
            throw new GitletException("Cannot remove the current branch.");
        }
        File branch = join(BRANCHES, branchName);
        if (branch.exists()) {
            restrictedDelete(branch);
        } else {
            throw new GitletException("A branch with that" +
                    " name does not exist.");
        }
    }

    public static void reset(String commitId) {
        switchActiveCommit(commitId);
    }

    public static void merge(String branchName) {
        String currentBranch = readContentsAsString(activeBranch);
        if (branchName.equals(currentBranch)) {
            throw new GitletException("Cannot merge a branch with itself");
        }
        File branch = join(BRANCHES, branchName);
        if (!branch.exists()) {
            throw new GitletException("A branch with that" +
                    " name does not exist.");
        }
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        if (!stagingArea.map.isEmpty()) {
            throw new GitletException("You have uncommitted changes.");
        }

        String branchID = readContentsAsString(branch);
        String splitPointID = splitPointID(branchID);

        if (branchID.equals(splitPointID)) {
            System.out.println("Given branch is an ancestor" +
                    " of the current branch.");
            System.exit(1);
        }
        if (splitPointID.equals(readContentsAsString(head))) {
            switchActiveCommit(branchID);
            writeContents(join(BRANCHES, "current"), currentBranch);
            writeContents(join(BRANCHES, currentBranch), splitPointID);
            restrictedDelete(join(BRANCHES, branchName));
            System.out.println("Current branch fast-forwarded.");
            System.exit(1);
        }
    }

    // Helper method to find and return split point.
    private static String splitPointID(String commitID) {
        HashSet<String> ancestors = new HashSet<>();
        String headID = readContentsAsString(head);
        return BFS(ancestors, commitID, headID);
    }

    // Helper method to traverse the commits tree and return split point.
    private static String BFS(HashSet<String> set,
                              String branch1,
                              String branch2) {
        if (set.contains(branch1) && branch1 != null) {
            return branch1;
        }
        if (set.contains(branch2) && branch2 != null) {
            return branch2;
        }
        set.add(branch1);
        set.add(branch2);

        Commit c1 = checkOutCommit(branch1);
        Commit c2 = checkOutCommit(branch2);

        String parent1 = c1 != null ? c1.getParent() : null;
        String parent2 = c2 != null ? c2.getParent() : null;
        return BFS(set, parent1, parent2);
    }

    // Helper method to check out a commit.
    private static Commit checkOutCommit(String sha1) {
        if (sha1 == null) {
            return null;
        }
        File commitDir = join(COMMITS, sha1.substring(0, 4));
        File commitFile = join(commitDir, sha1.substring(4));
        if (sha1.length() < 40) {
            if (!commitDir.exists()) {
                throw new GitletException("No commit with that id exists.");
            }
            List<String> commits = plainFilenamesIn(commitDir);
            assert commits != null;
            for (String sha : commits) {
                if (sha.startsWith(sha1.substring(4))) {
                    commitFile = join(commitDir, sha);
                }
            }
        }
        if (!commitFile.exists()) {
            throw new GitletException("No commit with that id exists.");
        }
        return readObject(commitFile, Commit.class);
    }

    //Helper method to create commit file.
    private static File createCommitFile(String sha1) {
        File commitDir = join(COMMITS, sha1.substring(0, 4));
        if (!commitDir.exists()) {
            commitDir.mkdir();
        }
        return join(commitDir, sha1.substring(4));
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
