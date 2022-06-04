package gitlet;

import java.io.File;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;

import static gitlet.Utils.*;

// TODO: any imports you need here

/** Represents a gitlet repository.
 *  TODO: It's a good idea to give a description here of what else this Class
 *  does at a high level.
 *
 *  @author Adrian Serbanescu
 */
public class Repository {
    /**
     * TODO: add instance variables here.
     *
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The branches directory saves the heads of each branch we create */
    public static final File BRANCHES = join(GITLET_DIR, "branches");
    public static final File COMMITS = join(GITLET_DIR, "commits");
    public static final File FILES = join(COMMITS, "files");
    public static File head = join(GITLET_DIR, "HEAD");

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
                (new Date(0)).toString(),
                null,
                null);

        File newCommit = join(COMMITS, initCommit.id);
        File branch = join(BRANCHES, "master");
        writeObject(newCommit, initCommit);
        writeContents(branch, initCommit.id);
        writeContents(head, initCommit.id);
        System.out.println(initCommit.toString());
    }

    public static void add() {
        String parentID = readContentsAsString(head);
        List<String> currentFiles = plainFilenamesIn(CWD);

        for (String fileName : currentFiles) {
            File file = join(CWD, fileName);
            String hashedFileName = sha1(file);
            File toBeSaved = join(FILES, hashedFileName);
            if(!toBeSaved.exists()) {
                String contentsToBeSaved = fileName + "\n" + readContentsAsString(file);

            }
        }
    }
}
