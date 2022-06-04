package gitlet;

import java.net.FileNameMap;
import java.util.HashSet;
import java.util.Set;

public class FileMap {
    private FileNode root;

    private class FileNode {
        private FileNode left, right;
        private String fileName;     // Node key.
        private String sha1;         // Node value.

        public FileNode(String fileName, String sha1) {
            this.fileName = fileName;
            this.sha1 = sha1;
        }
    }

    public void clear() {
        this.root = null;
    }

    public boolean containsFile(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("Calls containsKey() with a null argument.");
        }

        return containsFile(fileName, root);
    }

    private boolean containsFile(String fileName, FileNode node) {
        if (node == null) {
            return false;
        }
        int compare = fileName.compareTo(node.fileName);
        if (compare < 0) {
            return containsFile(fileName, node.left);
        } else if (compare > 0) {
            return containsFile(fileName, node.right);
        } else {
            return true;
        }
    }

    public void put(String fileName, String sha1) {
        if (fileName == null || sha1 == null) {
            throw new IllegalArgumentException("Calls put with null arg.");
        }

        root = put(fileName, sha1, root);
    }

    private FileNode put(String fileName, String sha1, FileNode node) {
        if (node == null) {
            return new FileNode(fileName, sha1);
        }
        int compare = fileName.compareTo(node.fileName);
        if (compare < 0)  {
            node.left = put(fileName, sha1, node.left);
        } else if (compare > 0) {
            node.right = put(fileName, sha1, node.right);
        } else {
            node.sha1 = sha1;
        }
        return node;
    }

    public Set<String> fileNameSet() {
        Set<String> set = new HashSet<>();
        fileNameSet(root, set);
        return set;
    }

    private void fileNameSet(FileNode node, Set<String> set) {
        if (node != null) {
            set.add(node.fileName);
            if (node.right != null) {
                fileNameSet(node.right, set);
            }
            if (node.left != null) {
                fileNameSet(node.left, set);
            }
        }
    }

    public void remove(String fileName) {
        root = remove(fileName, root);
    }

    private FileNode remove(String fileName, FileNode node) {
        if (node == null) {
            return null;
        }
        int compare = fileName.compareTo(node.fileName);
        if (compare < 0) {
            node.left = remove(fileName, node.left);
        } else if (compare > 0) {
            node.right = remove(fileName, node.right);
        } else {
            if (node.right == null) {
                return node.left;
            }
            if (node.left == null) {
                return node.right;
            }
            node.right = successor(node.right, node);
        }
        return node;
    }

    private FileNode successor(FileNode L, FileNode R) {
        if (L.left == null) {
            R.fileName = L.fileName;
            R.sha1 = L.sha1;
            return L.right;
        } else {
            L.left = successor(L.left, R);
            return L;
        }
    }
}
