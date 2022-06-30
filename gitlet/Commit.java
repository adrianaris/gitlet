package gitlet;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

/**
 * Represents a gitlet commit object.
 * This Class takes care of saving to the file system the actual changes to our
 * repository. It achieves this by saving for each commit a file containing the
 * metadata of the commit (like Author, message etc.) and actual data that we want to track
 * and version control.
 *
 *  @author Adrian Serbanescu
 */
public class Commit implements Serializable {
    /** The ID of this commit */
    private String id;
    /** The message of this Commit. */
    private String message;
    /** Author of the Commit. */
    private String author;
    private String date;
    private final DateFormat DATE_FORMAT = new SimpleDateFormat(
            "EEE MMM d HH:mm:ss yyy Z");
    private String parent;
    /**
     * Should the commit be a merge this is the parentID of the merged-in commit parent.
     */
    private String mergeParent;
    /** Map of fileNames and corresponding sha1 */
    private HashMap<String, String> files;

    public Commit(String message,
                  String author,
                  String parent,
                  String mergeParent,
                  HashMap<String, String> files) {
        this.author = author;
        this.message = message;
        this.parent = parent;
        this.files = files;
        this.date = parent == null
                ? DATE_FORMAT.format(new Date(0))
                : DATE_FORMAT.format(new Date());
        id = files == null ? Utils.sha1() : Utils.sha1(files.toString());
        this.mergeParent = mergeParent;
    }

    public boolean isEmpty() {
        return files == null;
    }
    public HashMap<String, String> getFiles() {
        return files;
    }

    public void print() {
        System.out.println("===");
        System.out.println("commit " + id);
        if (mergeParent != null) {
            System.out.println("Merge: "
                    + parent.substring(0, 7) + " "
                    + mergeParent.substring(0, 7));
        }
        System.out.println("Date: " + date);
        System.out.println(message);
        System.out.println();
    }

    public String getParent() {
        return parent;
    }

    public String getMessage() {
        return message;
    }

    public String getId() {
        return id;
    }
}
