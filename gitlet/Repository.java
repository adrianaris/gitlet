package gitlet;

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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
    /**
     * Staging area is a map of fileNames, sha1 (key,value) pairs.
     */
    public static HashMap<String, String> stagingArea = new HashMap<>();

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
                "Adrian Serbanescu",
                null,
                null);

        File newCommit = join(COMMITS, initCommit.id);
        File branch = join(BRANCHES, "master");
        writeObject(newCommit, initCommit);
        writeContents(branch, initCommit.id);
        writeContents(head, initCommit.id);
        System.out.println(initCommit.toString());
    }

    public static void add(String fileName) {
        List<String> currentFiles = plainFilenamesIn(CWD);
        HashMap<String, String> currentCommitFileMap =
                checkOutCommit(readContentsAsString(head));
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
                stagingArea.put(fileName, sha1);
            }
        } else {
            throw new GitletException("File does not exist.");
        }
    }

    // Helper method to check-out a commit.
    private static HashMap<String, String> checkOutCommit(String sha1) {
        if (sha1 == null) {
            return null;
        }
        File commitFile = join(COMMITS, sha1);
        if (commitFile == null) {
            throw new GitletException("Commit doesn't exist");
        }
        Commit currentCommit = readObject(commitFile, Commit.class);
        HashMap<String, String> currentCommitFileMap = currentCommit.getFiles();
        if (currentCommit != null) {
            return currentCommitFileMap;
        } else {
            return null;
        }
    }
}
