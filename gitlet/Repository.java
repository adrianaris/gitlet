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

    // Helper method to check-out a commit.
    private static Commit checkOutCommit(String sha1) {
        if (sha1 == null) {
            throw new IllegalArgumentException("sha1 id is null");
        }
        File commitFile = join(COMMITS, sha1);
        if (!commitFile.exists()) {
            throw new GitletException("Commit doesn't exist");
        }
        return readObject(commitFile, Commit.class);
    }
    // Helper class for staging.
    public static class StagingArea implements Serializable {
        public HashMap<String, String> map;
        public StagingArea() {
            map = new HashMap<>();
        }
    }
}
