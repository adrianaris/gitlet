package gitlet;


import java.io.File;
import java.io.Serializable;
import java.util.*;

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
                new HashMap<>());

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
        HashMap<String, String> commitFiles =
                currentCommit.getFiles();

        if (stagingArea.map.containsKey(fileName)
                && stagingArea.map.get(fileName) == null) {
            stagingArea.map.remove(fileName);
            checkOutFileInCommit(currentCommit.getId(), fileName);
            writeObject(STAGING_AREA, stagingArea);
            System.exit(0);
        }

        if (currentFiles == null) {
            System.out.println("CWD is empty");
            System.exit(0);
        } else if (!currentFiles.contains(fileName)) {
            System.out.println("File does not exist.");
            System.exit(0);
        }

        File file = join(CWD, fileName);
        String contents = readContentsAsString(file);
        String sha1 = sha1(contents);
        String commitedSHA = commitFiles.get(fileName);
        if (!sha1.equals(commitedSHA)) {
            stagingArea.map.put(fileName, contents);
            writeObject(STAGING_AREA, stagingArea);
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
        HashMap<String, String> newCommitFiles =  parentCommit.getFiles();
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
        Commit activeCommit = checkOutCommit(readContentsAsString(HEAD));
        boolean untracked = !activeCommit.getFiles().containsKey(fileName);
        if (untracked) { //check if file is untracked so that we don't rm it
            if (!stagingArea.map.containsKey(fileName)) {
                System.out.println("No reason to remove the file.");
                System.exit(0);
            } else { //should the file had been staged for addition remove it
                stagingArea.map.remove(fileName);
                writeObject(STAGING_AREA, stagingArea);
            }
        } else {
            stagingArea.map.put(fileName, null);
            restrictedDelete(join(CWD, fileName));
            writeObject(STAGING_AREA, stagingArea);
        }
    }

    public static void log() {
        Commit currentCommit = checkOutCommit(readContentsAsString(HEAD));
        log(currentCommit);
    }

    /**
     * Takes a Commit object as argument, traverses all the parents and at each
     * step prints it.
     */
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
                    branchFiles.append("*").append(branch).append("\n");
                } else {
                    branchFiles.append(branch).append("\n");
                }
            }
        }
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
        HashSet<String> untracked = checkUntracked();
        for (String file : untracked) {
            untrackedFiles.append(file).append("\n");
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
            checkUntrackedFile(branchID);
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
        StagingArea sa = readObject(STAGING_AREA, StagingArea.class);
        sa.map.clear();
        writeObject(STAGING_AREA, sa);
        checkUntrackedFile(commitId);
        switchActiveCommit(commitId);
        File activeBranch = join(BRANCHES, readContentsAsString(ACTIVE_BRANCH));
        writeContents(activeBranch, commitId);
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
        checkUntrackedFile(branchID);
        String splitPointID = splitPointID(branchID);

        if (branchID.equals(splitPointID)) {
            System.out.println("Given branch is an ancestor"
                    + " of the current branch.");
            System.exit(0);
        }
        if (splitPointID.equals(currentBranchID)) {
            checkOutBranch(branchName);
            writeContents(join(BRANCHES, "current"), currentBranch);
            writeContents(join(BRANCHES, currentBranch), splitPointID);
            join(BRANCHES, branchName).delete();
            System.out.println("Current branch fast-forwarded.");
            System.exit(0);
        }

        boolean conflict = merge(currentBranchID, branchID, splitPointID);
        commit("Merged " + branchName + " into " + currentBranch + ".",
                branchID);
        if (conflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    //Helper method for merge.
    private static boolean merge(String active, String given, String split) {
        boolean conflict = false;
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        HashMap<String, String> activeF = checkOutCommit(active).getFiles();
        HashMap<String, String> givenF = checkOutCommit(given).getFiles();
        HashMap<String, String> splitF = checkOutCommit(split).getFiles();
        HashSet<String> allFiles = new HashSet<>();
        allFiles.addAll(activeF.keySet());
        allFiles.addAll(givenF.keySet());
        allFiles.addAll(splitF.keySet());
        for (String fileName: allFiles) {
            String aSha = activeF.get(fileName);
            String gSha = givenF.get(fileName);
            String sSha = splitF.get(fileName);
            String aC = aSha != null
                    ? readContentsAsString(join(FILES, aSha))
                    : "";
            String gC = gSha != null
                    ? readContentsAsString(join(FILES, gSha))
                    : "";
            String cC = "<<<<<<< HEAD\n" + aC + "=======\n" + gC + ">>>>>>>\n";

            /**
             * Any files that have been modified in the given branch since the
             * split point, but not modified in the current branch since the
             * split point should be changed to their versions in the given
             * branch.
             */
            if (aSha != null && gSha != null && sSha != null
                && !gSha.equals(sSha) && aSha.equals(sSha)) {
                checkOutFileInCommit(given, fileName);
                stagingArea.map.put(fileName, gC);
            /**
             * Any files that were not present at the split point and are
             * present only in the given branch should be checked out and
             * staged.
             **/
            } else if (aSha == null && sSha == null) {
                checkOutFileInCommit(given, fileName);
                stagingArea.map.put(fileName, gC);
            /**
             * Any files present at the split point, unmodified in the current
             * branch, and absent in the given branch should be removed.
             **/
            } else if (gSha == null && aSha != null && aSha.equals(sSha)) {
                stagingArea.map.put(fileName, null);
            /**
             * Any files modified in different ways in the current and given
             * branches are in conflict. “Modified in different ways” can
             * mean that the contents of both are changed and different from
             * other, or the contents of one are changed and the other file is
             * deleted, or the file was absent at the split point and has
             * different contents in the given and current branches. In this
             * case, replace the contents of the conflicted file with
             * cC (conflict content).
             */
            } else if ((aSha != null && gSha != null && !aSha.equals(gSha)
                    && !gSha.equals(sSha))
                    || (gSha != null && sSha != null
                    && !gSha.equals(sSha) && aSha == null)
                    || (aSha != null && sSha != null
                    && !aSha.equals(sSha) && gSha == null)) {
                writeContents(join(CWD, fileName), cC);
                stagingArea.map.put(fileName, cC);
                conflict = true;
            }
        }

        writeObject(STAGING_AREA, stagingArea);
        return conflict;
    }
//    private static boolean merge(String active, String given, String split) {
//        boolean conflict = false;
//        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
//        HashMap<String, String> activeF = checkOutCommit(active).getFiles();
//        HashMap<String, String> givenF = checkOutCommit(given).getFiles();
//        HashMap<String, String> splitF = checkOutCommit(split).getFiles();
//
//        for (Map.Entry<String, String> set : activeF.entrySet()) {
//            String fileName = set.getKey();
//            String activeFsha = set.getValue();
//            String splitFsha = splitF.get(fileName);
//
//            if (activeFsha.equals(splitFsha) && !givenF.containsKey(fileName)) {
//                stagingArea.map.put(fileName, null);
//            }
//        }
//
//        for (Map.Entry<String, String> set : givenF.entrySet()) {
//            String fileName = set.getKey();
//            String givenFsha = set.getValue();
//            String activeFsha = activeF.get(fileName);
//            String splitFsha = splitF.get(fileName);
//
//            if (splitFsha == null) {
//                if (activeFsha == null) {
//                    checkOutFileInCommit(given, fileName);
//                    String content = readContentsAsString(join(FILES, givenFsha));
//                    stagingArea.map.put(fileName, content);
//                } else if (!activeFsha.equals(givenFsha)) {
//                    String fileContents =
//                            createConflictFile(activeFsha, givenFsha);
//                    writeContents(join(CWD, fileName), fileContents);
//                    stagingArea.map.put(fileName, fileContents);
//                    conflict = true;
//                }
//            } else {
//                if (!givenFsha.equals(splitFsha)
//                        && activeFsha.equals(splitFsha)) {
//                    checkOutFileInCommit(given, fileName);
//                    String content = readContentsAsString(join(FILES, givenFsha));
//                    stagingArea.map.put(fileName, content);
//                } else if (!givenFsha.equals(splitFsha)
//                        && !activeFsha.equals(givenFsha)) {
//                    String fileContents =
//                            createConflictFile(activeFsha, givenFsha);
//                    writeContents(join(CWD, fileName), fileContents);
//                    stagingArea.map.put(fileName, fileContents);
//                    conflict = true;
//                }
//            }
//        }
//
//        writeObject(STAGING_AREA, stagingArea);
//        return conflict;
//    }


    // Helper method to find and return split point.
    private static String splitPointID(String commitID) {
        String head = readContentsAsString(HEAD);
        HashMap<String, Date> hAnces = ancestors(head, new HashMap<>());
        HashMap<String, Date> gAnces = ancestors(commitID, new HashMap<>());
        String cAnces = null;
        for (Map.Entry<String, Date> entry: hAnces.entrySet()) {
            if (gAnces.containsKey(entry.getKey())) {
                if (cAnces == null) {
                    cAnces = entry.getKey();
                } else {
                    Commit c = checkOutCommit(cAnces);
                    if (c.getDate().compareTo(entry.getValue()) < 0) {
                        cAnces = entry.getKey();
                    }
                }
            }
        }
        return cAnces;
    }

    private static HashMap<String, Date> ancestors(String id,
                                                   HashMap<String, Date> map) {
        if (id == null || map.containsKey(id)) {
            return map;
        }
        Commit c = checkOutCommit(id);
        map.put(id, c.getDate());
        String p1 = c.getParent();
        String p2 = c.getMergeParent();
        HashMap<String, Date> map1 = ancestors(p1, map);
        HashMap<String, Date> map2 = ancestors(p2, map);
        map1.putAll(map2);

        return map1;
    }

    // Helper method to traverse the commits tree and return split point.
//    private static String splitPointID(String commitID) {
//        HashSet<String> ancestors = new HashSet<>();
//        String head = readContentsAsString(HEAD);
//        return bfs(ancestors, head, commitID);
//    }
//    private static String bfs(HashSet<String> set,
//                              String branch1,
//                              String branch2) {
//        if (branch1 != null && branch2 != null) {
//            if (branch1.equals(branch2)) {
//                return branch1;
//            }
//        }
//        if (branch1 != null && set.contains(branch1)) {
//            return branch1;
//        }
//        if (branch2 != null && set.contains(branch2)) {
//            return branch2;
//        }
//        set.add(branch1);
//        set.add(branch2);
//
//        Commit c1 = checkOutCommit(branch1);
//        Commit c2 = checkOutCommit(branch2);
//
//        String parent1 = c1 != null ? c1.getParent() : null;
//        String parent2 = c2 != null ? c2.getParent() : null;
//        return bfs(set, parent1, parent2);
//    }

    /**
     * Helper method to check out a commit.
     * It just loads a commit into memory from a file it has been serialized to.
     */
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

    /**
     * This method has the purpose of creating the commit file on the FS.
     * We use a separate method so that we split the id into first four
     * characters (which represent a directory that will contain the files
     * with name the remaining  characters of the commit's sha1) and it returns
     * the final commit File (which is the last 36 chars of the sha1)
     * With this I'm trying to imitate somehow the original git!!!
     * @param sha1
     * @return File
     */
    private static File createCommitFile(String sha1) {
        File commitDir = join(COMMITS, sha1.substring(0, 4));
        if (!commitDir.exists()) {
            commitDir.mkdir();
        }
        return join(commitDir, sha1.substring(4));
    }

    /**
     * Helper method that replaces active CWD files with the version
     * of files in the provided commit.
     * @param commitID
     */
    private static void switchActiveCommit(String commitID) {
        HashMap<String, String> activeCommitFiles =
                checkOutCommit(readContentsAsString(HEAD)).getFiles();
        HashMap<String, String> replaceFiles =
                checkOutCommit(commitID).getFiles();
        writeContents(HEAD, commitID);
        for (Map.Entry<String, String> set : replaceFiles.entrySet()) {
            File file = join(CWD, set.getKey());
            File blob = join(FILES, set.getValue());
            writeContents(file, readContentsAsString(blob));
        }
        for (Map.Entry<String, String> set: activeCommitFiles.entrySet()) {
            if (!replaceFiles.containsKey(set.getKey())) {
                restrictedDelete(join(CWD, set.getKey()));
            }
        }
    }

    /**
     * Helper method that only prints out an error to the terminal and exits
     * should there be any untracked files.
     */
    private static void checkUntrackedFile(String commitID) {
        HashMap<String, String> files = checkOutCommit(commitID).getFiles();
        for (String file : checkUntracked()) {
            String shaCurrent = sha1(readContentsAsString(join(CWD, file)));
            String shaGiven = files.get(file);
            if (shaGiven != null && !shaCurrent.equals(shaGiven)) {
                System.out.println("There is an untracked file in the"
                        + " way; delete it, or add and commit it first.");
                System.exit(0);
            }
        }
    }

    /**
     * Helper method that returns a set of untracked files, based on current
     * active commit.
     * @return set
     */
    private static HashSet<String> checkUntracked() {
        HashMap<String, String> activeCommitFiles =
                checkOutCommit(readContentsAsString(HEAD)).getFiles();
        StagingArea stagingArea = readObject(STAGING_AREA, StagingArea.class);
        HashSet<String> set = new HashSet<>();
        List<String> currentFiles = plainFilenamesIn(CWD);
        if (currentFiles != null) {
            for (String file : currentFiles) {
                if (!activeCommitFiles.containsKey(file)
                        && !stagingArea.map.containsKey(file)) {
                    set.add(file);
                }
            }
        }
        return set;
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
