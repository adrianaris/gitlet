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
    public String id;
    /** The message of this Commit. */
    private String message;
    /** Author of the Commit. */
    private String author;
    private String date;
    private final DateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyy HH:mm a");
    private String parents;
    /** Map of fileNames and corresponding sha1 */
    private HashMap<String, String> files;

    public Commit(String message,
                  String author,
                  String parents,
                  HashMap<String, String> files) {
        this.author = author;
        this.message = message;
        this.parents = parents;
        this.files = files;
        this.date = parents == null
                ? DATE_FORMAT.format(new Date(0))
                : DATE_FORMAT.format(new Date());
        id = files == null ? Utils.sha1() : Utils.sha1(files.toString());
    }

    public boolean isEmpty() {
        return files == null;
    }
    public HashMap<String, String> getFiles() {
        return files;
    }
    //public String[] getParent() {
    //    return parents;
    //}
}
