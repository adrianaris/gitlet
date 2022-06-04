package gitlet;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date; // TODO: You'll likely use this in this class

/** Represents a gitlet commit object.
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
    /**
     * Array of parents IDs. Array because in case of branching and merging
     * we can end-up in the situation that the merge-commit has 2 parents.
     */
    private String[] parents;
    /** Array of files IDs that the Commit keeps track of. */
    private String[] files;

    public Commit(String message,
                        String author,
                        String date,
                        String[] parents,
                        String[] files) {
        this.author = author;
        this.message = message;
        this.parents = parents;
        this.files = files;
        this.date = date == null ? DATE_FORMAT.format(new Date()) : date;
        id = Utils.sha1();
    }

    public String[] getFiles() {
        return files;
    }
    //public String[] getParent() {
    //    return parents;
    //}
}
