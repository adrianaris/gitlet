package gitlet;


import java.io.Serializable;
import java.util.HashMap;
import static gitlet.Utils.*;

public class StagingArea implements Serializable {
    private static StagingArea stagingArea = null;
    private HashMap<String, String> fileMap;
    private StagingArea() {
        if (stagingArea == null) {
            fileMap = new HashMap<String, String>();
        } else {
            stagingArea = readObject(Repository.STAGING_AREA, StagingArea.class);
            fileMap = stagingArea.getFileMap();
        }
    }
    public static StagingArea getInstance() {
        return new StagingArea();
    }
    public void update(HashMap<String, String> map) {
        fileMap = map;
    }
    public void clear() {
        fileMap.clear();
    }
    public HashMap<String, String> getFileMap() {
        return fileMap;
    }
}
