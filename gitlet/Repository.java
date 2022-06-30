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
    private static final String AUTHOR = "Adrian Serbanescu";
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The branches directory saves the heads of each branch we create. */
    public static final File BRANCHES = join(GITLET_DIR, "branches");
    /** This dir persists each commit object. */
    public static final File COMMITS = join(GITLET_DIR, "commits");
    /** This dir persists all versions of the repository's files. */
    public static final File FILES = join(GITLET_DIR, "files");
    /** This file keeps track of which commit is currently active. */
    private static final File HEAD = join(GITLET_DIR, "HEAD");
    private static final File ACTIVE_BRANCH = join(BRANCHES, "current");
    private static final File STAGING_AREA = join(GITLET_DIR, "INDEX");

    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println(
                    "A Gitlet version-control system already"
                            + "exists in the current directory.");
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
                null,
                null);

        File newCommitDir = join(COMMITS, initCommit.getId().substring(0, 4));
        newCommitDir.mkdir();
        File newCommit = join(newCommitDir, initCommit.getId().substring(4));
        File branch = join(BRANCHES, "master");
        writeObject(newCommit, initCommit);
        writeContents(branch, initCommit.getId());
        writeContents(HEAD, initCommit.getId());
        writeContents(ACTIVE_BRANCH, "master");
        writeObject(STAGING_AREA, new StagingArea());
    }

    public static void add(String fileName) {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        List<String> currentFiles = plainFilenamesIn(CWD);
        Commit currentCommit = checkOutCommit(readContentsAsString(HEAD));
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
            System.out.println("File does not exist.");
            System.exit(0);
        }
    }

    public static void commit(String message) {
        if (message.length() == 0) {
            System.out.println("Please enter a commit message.");
            System.exit(0);
        }
        commit(message, null);
    }

    private static void commit(String message, String mergedInCommitID) {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        if (stagingArea.map.isEmpty()) {
            System.out.println("No changes added to the commit.");
            System.exit(0);
        }
        Commit parentCommit = checkOutCommit(readContentsAsString(HEAD));
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
                parentCommit.getId(),
                mergedInCommitID,
                newCommitFiles
        );
        File commit = createCommitFile(newCommit.getId());
        File branch = join(BRANCHES, readContentsAsString(ACTIVE_BRANCH));
        writeObject(commit, newCommit);
        writeContents(branch, newCommit.getId());
        writeContents(HEAD, newCommit.getId());
        stagingArea.map.clear();
        writeObject(STAGING_AREA, stagingArea);
    }

    public static void rm(String fileName) {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        Commit currentCommit = checkOutCommit(readContentsAsString(HEAD));
        HashMap<String, String> commitedFiles = currentCommit.getFiles();
        if (commitedFiles == null || !commitedFiles.containsKey(fileName)) {
            if (!stagingArea.map.containsKey(fileName)) {
                System.out.println("No reason to remove the file.");
                System.exit(0);
            } else {
                stagingArea.map.remove(fileName);
                writeObject(STAGING_AREA, stagingArea);
                System.exit(1);
            }

        }
        stagingArea.map.put(fileName, null);
        restrictedDelete(join(CWD, fileName));
        writeObject(STAGING_AREA, stagingArea);
    }

    public static void log() {
        Commit currentCommit = checkOutCommit(readContentsAsString(HEAD));
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
        HashSet<String> commits = getAllCommits();
        for (String commit: commits) {
            Commit com = checkOutCommit(commit);
            com.print();
        }
    }

    public static void find(String message) {
        HashSet<String> commits = getAllCommits();
        int found = 0;
        for (String commit : commits) {
            Commit com = checkOutCommit(commit);
            if (message.equals(com.getMessage())) {
                System.out.println(commit);
                found++;
            }
        }
        if (found == 0) {
            System.out.println("Found no commit with that message.");
        }
    }

    // Helper method to get a set of all the commits.
    private static HashSet<String> getAllCommits() {
        HashSet<String> set = new HashSet<>();
        List<String> commitDirs = fileNamesIn(COMMITS);
        assert commitDirs != null;
        for (String commitDir : commitDirs) {
            File dir = join(COMMITS, commitDir);
            List<String> commits = plainFilenamesIn(dir);
            assert commits != null;
            for (String commit: commits) {
                set.add(commitDir + commit);
            }
        }
        return set;
    }

    public static void status() {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        Commit currentCommit = checkOutCommit(readContentsAsString(HEAD));
        HashMap<String, String> commitedFiles = currentCommit.getFiles();
        List<String> branches = plainFilenamesIn(BRANCHES);
        String currentBranch = readContentsAsString(join(BRANCHES, "current"));
        List<String> currentFiles = plainFilenamesIn(CWD);

        StringBuilder stagedFiles = new StringBuilder();
        StringBuilder removedFiles = new StringBuilder();
        StringBuilder modificationsNotStaged = new StringBuilder();
        StringBuilder untrackedFiles = new StringBuilder();
        StringBuilder branchFiles = new StringBuilder();

        assert branches != null;
        for (String branch : branches) {
            if (!branch.equals("current")) {
                if (branch.equals(currentBranch)) {
                    branchFiles.append("*" + branch).append("\n");
                } else {
                    branchFiles.append(branch).append("\n");
                }
            }
        }
        if (commitedFiles != null) {
            for (Map.Entry<String, String> set : commitedFiles.entrySet()) {
                String fileName = set.getKey();
                String commitedSha = set.getValue();
                if ((currentFiles == null || !currentFiles.contains(fileName))
                        && !stagingArea.map.containsKey(fileName)) {
                    modificationsNotStaged.append(fileName)
                            .append(" (deleted)").append("\n");
                } else if (currentFiles != null
                        && currentFiles.contains(fileName)
                        && !stagingArea.map.containsKey(fileName)
                        && !commitedSha.equals(
                        sha1(readContentsAsString(join(CWD, fileName)))
                )) {
                    modificationsNotStaged.append(fileName)
                            .append(" (modified)").append("\n");
                }
            }
        }
        if (currentFiles != null) {
            for (String fileName : currentFiles) {
                if (!stagingArea.map.containsKey(fileName)
                    && (commitedFiles == null
                        || !commitedFiles.containsKey(fileName))) {
                    untrackedFiles.append(fileName).append("\n");
                }
            }
        }

        for (Map.Entry<String, String> set: stagingArea.map.entrySet()) {
            String fileName = set.getKey();
            if (set.getValue() == null) {
                removedFiles.append(fileName).append("\n");
            } else {
                stagedFiles.append(fileName).append("\n");
            }
        }
        System.out.println("=== Branches ===");
        System.out.println(branchFiles);
        System.out.println("=== Staged Files ===");
        System.out.println(stagedFiles);
        System.out.println("=== Removed Files ===");
        System.out.println(removedFiles);
        System.out.println("=== Modifications Not Staged For Commit ===");
        System.out.println(modificationsNotStaged);
        System.out.println("=== Untracked Files ===");
        System.out.println(untrackedFiles);
    }

    public static void checkOutFileInHead(String fileName) {
        String headCommit = readContentsAsString(HEAD);
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
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
    }

    public static void checkOutBranch(String branchName) {
        if (readContentsAsString(ACTIVE_BRANCH).equals(branchName)) {
            System.out.println("No need to checkout the current branch");
            System.exit(0);
        }
        File branch = join(BRANCHES, branchName);
        if (branch.exists()) {
            String branchID = readContentsAsString(branch);
            switchActiveCommit(branchID);
            writeContents(join(BRANCHES, "current"), branchName);
        } else {
            System.out.println("No such branch exists.");
            System.exit(0);
        }
    }


    public static void branch(String branchName) {
        File branch = join(BRANCHES, branchName);
        if (branch.exists()) {
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        }
        writeContents(branch, readContentsAsString(HEAD));
    }

    public static void rmBranch(String branchName) {
        String currentBranch = readContentsAsString(ACTIVE_BRANCH);
        if (currentBranch.equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }
        File branch = join(BRANCHES, branchName);
        if (branch.exists()) {
            branch.delete();
        } else {
            System.out.println("A branch with that"
                    + " name does not exist.");
            System.exit(0);
        }
    }

    public static void reset(String commitId) {
        switchActiveCommit(commitId);
    }

    public static void merge(String branchName) {
        String currentBranch = readContentsAsString(ACTIVE_BRANCH);
        String currentBranchID = readContentsAsString(HEAD);

        if (branchName.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        }
        File branch = join(BRANCHES, branchName);
        if (!branch.exists()) {
            System.out.println("A branch with that"
                    + " name does not exist.");
            System.exit(0);
        }
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        if (!stagingArea.map.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        }

        String branchID = readContentsAsString(branch);
        checkUntracked(branchID);
        String splitPointID = splitPointID(branchID);

        if (branchID.equals(splitPointID)) {
            System.out.println("Given branch is an ancestor"
                    + " of the current branch.");
            System.exit(0);
        }
        if (splitPointID.equals(currentBranchID)) {
            switchActiveCommit(branchID);
            writeContents(join(BRANCHES, "current"), currentBranch);
            writeContents(join(BRANCHES, currentBranch), splitPointID);
            join(BRANCHES, branchName).delete();
            System.out.println("Current branch fast-forwarded.");
            System.exit(1);
        }

        merge(currentBranchID, branchID, splitPointID);
        commit("Merged " + branchName + " into " + currentBranch, branchID);
    }

    //Helper method for merge.
    private static void merge(String active, String given, String split) {
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        HashMap<String, String> activeF = checkOutCommit(active).getFiles();
        HashMap<String, String> givenF = checkOutCommit(given).getFiles();
        HashMap<String, String> splitF = checkOutCommit(split).getFiles();

        for (Map.Entry<String, String> set : activeF.entrySet()) {
            String fileName = set.getKey();
            String activeFsha = set.getValue();
            String splitFsha = splitF != null ? splitF.get(fileName) : null;

            if (activeFsha.equals(splitFsha) && !givenF.containsKey(fileName)) {
                stagingArea.map.put(fileName, null);
            }
        }

        for (Map.Entry<String, String> set : givenF.entrySet()) {
            String fileName = set.getKey();
            String givenFsha = set.getValue();
            String activeFsha = activeF.get(fileName);
            String splitFsha = splitF != null ? splitF.get(fileName) : null;

            if (splitFsha == null) {
                if (activeFsha == null) {
                    checkOutFileInCommit(given, fileName);
                    String content = readContentsAsString(join(FILES, givenFsha));
                    stagingArea.map.put(fileName, content);
                }
            } else {
                if (!givenFsha.equals(splitFsha)
                        && activeFsha.equals(splitFsha)) {
                    checkOutFileInCommit(given, fileName);
                    String content = readContentsAsString(join(FILES, givenFsha));
                    stagingArea.map.put(fileName, content);
                } else if (!givenFsha.equals(splitFsha)
                        && !activeFsha.equals(givenFsha)) {
                    File aFile = join(FILES, activeFsha);
                    File gFile = join(FILES, givenFsha);
                    StringBuilder fileContents = new StringBuilder();
                    fileContents.append("<<<<<<< HEAD\n");
                    fileContents.append(readContentsAsString(aFile));
                    fileContents.append("=======\n");
                    fileContents.append(readContentsAsString(gFile));
                    fileContents.append(">>>>>>>");
                    writeContents(join(CWD, fileName), fileContents.toString());
                    stagingArea.map.put(fileName, fileContents.toString());
                    System.out.println("Encountered a merge conflict.");
                }
            }
        }

        writeObject(STAGING_AREA, stagingArea);
    }

    // Helper method to find and return split point.
    private static String splitPointID(String commitID) {
        HashSet<String> ancestors = new HashSet<>();
        String headID = readContentsAsString(HEAD);
        return bfs(ancestors, commitID, headID);
    }

    // Helper method to traverse the commits tree and return split point.
    private static String bfs(HashSet<String> set,
                              String branch1,
                              String branch2) {
        if (branch1 != null && branch2 != null) {
            if (branch1.equals(branch2)) {
                return branch1;
            }
        }
        if (branch1 != null && set.contains(branch1)) {
            return branch1;
        }
        if (branch2 != null && set.contains(branch2)) {
            return branch2;
        }
        set.add(branch1);
        set.add(branch2);

        Commit c1 = checkOutCommit(branch1);
        Commit c2 = checkOutCommit(branch2);

        String parent1 = c1 != null ? c1.getParent() : null;
        String parent2 = c2 != null ? c2.getParent() : null;
        return bfs(set, parent1, parent2);
    }

    // Helper method to check out a commit.
    private static Commit checkOutCommit(String sha1) {
        if (sha1 == null) {
            return null;
        }
        if (sha1.length() < 4) {
            System.out.println("Commit id is too short.");
            System.exit(0);
        }
        File commitDir = join(COMMITS, sha1.substring(0, 4));
        File commitFile = join(commitDir, sha1.substring(4));
        if (sha1.length() < 40) {
            if (!commitDir.exists()) {
                System.out.println("No commit with that id exists.");
                System.exit(0);
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
            System.out.println("No commit with that id exists.");
            System.exit(0);
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

    // Helper method that replaces active CWD files with the version
    // of files in the provided commit.
    private static void switchActiveCommit(String commitID) {
        HashMap<String, String> activeCommitFiles =
                checkOutCommit(readContentsAsString(HEAD)).getFiles();
        HashMap<String, String> replaceFiles =
                checkOutCommit(commitID).getFiles();
        checkUntracked(commitID);
        writeContents(HEAD, commitID);
        if (replaceFiles != null) {
            for (Map.Entry<String, String> set : replaceFiles.entrySet()) {
                File file = join(CWD, set.getKey());
                File blob = join(FILES, set.getValue());
                writeContents(file, readContentsAsString(blob));
            }
        }
        if (activeCommitFiles != null) {
            for (Map.Entry<String, String> set: activeCommitFiles.entrySet()) {
                if (replaceFiles == null
                        || !replaceFiles.containsKey(set.getKey())) {
                    restrictedDelete(join(CWD, set.getKey()));
                }
            }
        }
    }

    // Helper method to check untracked files.
    private static void checkUntracked(String givenCommit) {
        HashMap<String, String> activeCommitFiles =
                checkOutCommit(readContentsAsString(HEAD)).getFiles();
        HashMap<String, String> givenFiles =
                checkOutCommit(givenCommit).getFiles();
        List<String> currentFiles = plainFilenamesIn(CWD);
        if (currentFiles != null) {
            for (String file : currentFiles) {
                if ((activeCommitFiles == null
                        || !activeCommitFiles.containsKey(file))
                        && (givenFiles != null
                        && givenFiles.containsKey(file))) {
                    System.out.println("There is an untracked file in the"
                            + " way; delete it, or add and commit it first.");
                    System.exit(0);
                }
            }
        }
    }

    // Helper class for staging.
    // Perhaps I should experiment with singleton Class!!!
    public static class StagingArea implements Serializable {
        private HashMap<String, String> map;
        public StagingArea() {
            map = new HashMap<>();
        }
    }
}
