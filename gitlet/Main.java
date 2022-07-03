package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Adrian Serbanescu
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args == null) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        switch(firstArg) {
            case "init":
                validateNumArgs("init", args, 1);
                Repository.init();
                break;
            case "add":
                validateNumArgs("add", args, 2);
                checkIfGitletDirExists();
                Repository.add(args[1]);
                break;
            case "commit":
                if (args.length != 2) {
                    throw new GitletException("no message");
                }
                checkIfGitletDirExists();
                Repository.commit(args[1]);
                break;
            case "rm":
                validateNumArgs("rm", args, 2);
                checkIfGitletDirExists();
                Repository.rm(args[1]);
                break;
            case "log":
                validateNumArgs("log", args, 1);
                checkIfGitletDirExists();
                Repository.log();
                break;
            case "global-log":
                validateNumArgs("global-log", args, 1);
                checkIfGitletDirExists();
                Repository.globalLog();
                break;
            case "find":
                validateNumArgs("find", args, 2);
                checkIfGitletDirExists();
                Repository.find(args[1]);
                break;
            case "status":
                validateNumArgs("status", args, 1);
                checkIfGitletDirExists();
                Repository.status();
                break;
            case "checkout":
                checkIfGitletDirExists();
                if (args.length == 2) {
                    Repository.checkOutBranch(args[1]);
                } else if(args.length == 3) {
                    if (!args[1].equals("--")) {
                        System.out.println("Incorrect operands.");
                        System.exit(0);
                    }
                    Repository.checkOutFileInHead(args[2]);
                } else if(args.length == 4) {
                    if (!args[2].equals("--")) {
                        System.out.println("Incorrect operands.");
                        System.exit(0);
                    }
                    Repository.checkOutFileInCommit(args[1], args[3]);
                } else {
                    System.out.println("Invalid number of arguments" +
                            " for: checkout");
                    System.exit(0);
                }
                break;
            case "branch":
                validateNumArgs("branch", args, 2);
                checkIfGitletDirExists();
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                validateNumArgs("rm-branch", args, 2);
                checkIfGitletDirExists();
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                validateNumArgs("reset", args, 2);
                checkIfGitletDirExists();
                Repository.reset(args[1]);
                break;
            case "merge":
                validateNumArgs("merge", args, 2);
                checkIfGitletDirExists();
                Repository.merge(args[1]);
                break;
            default:
                System.out.println("No command with that name exists.");
        }
    }

    public static void checkIfGitletDirExists() {
        if (!Repository.GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    public static void validateNumArgs(String cmd, String[] args, int n) {
        if (args.length != n) {
            throw new RuntimeException(
                    String.format("Invalid number of arguments for: %s.", cmd));
        }
    }
}
