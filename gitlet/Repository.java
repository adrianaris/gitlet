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

    /**
     * Helper class for staging. This class is in this state because I hurried
     * to write code and left as it currently is because it worked. I wanted to
     * make it more deep but due to me trying to fit into course's time
     * constraints for finishing this project I decided to leave it as it is.
     *
     * Perhaps I should experiment with singleton Class!!!
     */

    public static class StagingArea implements Serializable {
        private HashMap<String, String> map;
        public StagingArea() {
            map = new HashMap<>();
        }
    }

    /**
     * This method creates all the folder structure of the program and
     * also creates an initial empty commit which plays the part of the
     * sentinel for the underlying graph which the list of commits with
     * all its branching and merging will become.
     */
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

    /**
     * Because I've decided to implement the design decision that a file that
     * is staged for removal is saved in the underlying Map DS of the Staging
     * Area with null value we first check if that file is staged with null
     * so that if we add it after staging for removal the file it's just
     * restored.
     */
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

    /**
     * I've split this command into two methods so that is easier to accommodate
     * for special case of mergeCommit. I don't know if it would have been
     * possible to do it another way but at this moment I have to do it like
     * this due to the way I designed the Commit class.
     *
     * Bellow method in particular takes the log message as argument
     * and subsequently calls the special merge bellow it that it's also
     * capable of taking the id of the merged-in commit as argument.
     */
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

    /**
     * Stages a file for removal (i.e. adds it to the stagingArea with null
     * value) and removes it from the fs if tracked.
     * Mirroring the add function if the file is already staged for addition
     * removal will unstage it.
     */
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

   /**
    * Iterates over the parents of a commit and prints each one.
    * See print() method in Commit class.
    */
    public static void log() {
        Commit currentCommit = checkOutCommit(readContentsAsString(HEAD));
        log(currentCommit);
    }

    /**
     * Takes a Commit object as argument, traverses all the parents and at each
     * step prints it. (Does not traverse the branch of the tree which belongs
     * to the merged in branch, should any of the commits be a merge commit).
     */
    private static void log(Commit commit) {
        commit.print();
        String parentId = commit.getParent();
        if (parentId != null) {
            Commit parent = checkOutCommit(parentId);
            log(parent);
        }
    }

   /**
    * Similar to log() but for the entire list of commits without relation
    * between them considered.
    */
    public static void globalLog() {
        HashSet<String> commits = getAllCommits();
        for (String commit: commits) {
            Commit com = checkOutCommit(commit);
            com.print();
        }
    }

    /**
     * Finds a commit that has the log message given as argument.
     */
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

    /**
     * This method prints the state of the program (i.e. what files are tracked
     * or not, what state the stagingArea is in etc.).
     *
     * It achieves this by iterating over the list of files in the active
     * commit, the CWD and the staged files, and for each particular case it
     * builds a string to be fit into it's section of the overall structure.
     */
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

    /**
     * Next three methods represent each the behaviour of the 3 checkout()
     * commands, respectively. The caveat is the first calls the second with
     * commit id of the head as argument, and the third is independent of the
     * first two.
     */
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

    /**
     * This method creates a branch file and writes the head id in it.
     */
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

    /**
     * Resets the state of the repo to the given commit id.
     */
    public static void reset(String commitId) {
        StagingArea sa = readObject(STAGING_AREA, StagingArea.class);
        sa.map.clear();
        writeObject(STAGING_AREA, sa);
        checkUntrackedFile(commitId);
        switchActiveCommit(commitId);
        File activeBranch = join(BRANCHES, readContentsAsString(ACTIVE_BRANCH));
        writeContents(activeBranch, commitId);
    }

    /**
     * This public method takes a branchName as an argument and mainly checks
     * for fail or special cases. Should everything pass then another private
     * merge method is called which in turn calls several other private methods
     * for the actual merge.
     */
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

    /**
     * Helper method for merge. This is the meat of this command. Takes all
     * three commits in discussion, split, current and given, iterates over
     * the entire list of files from the combined three commits and checks for
     * all the particular cases.
     */
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
             */
            } else if (aSha == null && sSha == null) {
                checkOutFileInCommit(given, fileName);
                stagingArea.map.put(fileName, gC);
            /**
             * Any files present at the split point, unmodified in the current
             * branch, and absent in the given branch should be removed.
             */
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

    /**
     * This method together with the next one have the purpose to traverse the
     * graph, that the entire list of commits has become, starting from the
     * current head and the head of the given branch, at the same time, in order
     * to find the latest common ancestor, ancestor judged to be the latest
     * based on the commit date.
     */
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

    /**
     * Traverses a branch and returns a map of commit id, commit date of all the
     * ancestors of the commit id given as argument.
     */
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

//----------------------------------------------------------------------------//
   /**
    * All methods under this section are private utility methods that do not fit
    * particularly under one of the main methods and most of them are used in
    * multiple locations. They are written under this module because they make
    * use of a bunch of private elements present in this class.
    */
//----------------------------------------------------------------------------//

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

    /**
     * Helper method to get a set of all the commits.
     */
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
}
