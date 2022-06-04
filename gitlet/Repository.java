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
    /**
     * Staging area is a map of fileNames, sha1 (key,value) pairs.
     */
    public static void init() {
        if (GITLET_DIR.exists()) {
            throw new GitletException(
                    "A Gitlet version-control system already" +
                            "exists in the current directory");
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
        System.out.println(initCommit.toString());
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
            String sha1 = sha1(readContentsAsString(file));
            String commitedSHA = currentCommitFileMap == null
                    ? null
                    : currentCommitFileMap.get(fileName);
            if (!sha1.equals(commitedSHA)) {
                stagingArea.map.put(fileName, sha1);
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
        for (Map.Entry<String, String> set : stagingArea.map.entrySet()) {
            File stagedFile = join(CWD, set.getKey());
            File shaFile = join(GITLET_DIR, set.getValue());
            writeContents(shaFile, readContentsAsString(stagedFile));
        }
        Commit parentCommit = checkOutCommit(readContentsAsString(head));
        HashMap<String, String> newCommitFiles = parentCommit.isEmpty()
                ? new HashMap<>()
                : parentCommit.getFiles();
        newCommitFiles.putAll(stagingArea.map);
        Commit newCommit = new Commit(
            message,
            AUTHOR,
            parentCommit.id,
            newCommitFiles
        );
        File commit = join(COMMITS, newCommit.id);
        File branch = join(BRANCHES, readContentsAsString(activeBranch));
        writeObject(commit, newCommit);
        writeContents(branch, newCommit.id);
        writeContents(head, newCommit.id);
        stagingArea.map.clear();
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
