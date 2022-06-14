package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Adrian Serbanescu
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        String firstArg = args[0];
        if (firstArg == null) {
            throw new GitletException("No command provided.");
        }
        switch(firstArg) {
            case "init":
                validateNumArgs("init", args, 1);
                Repository.init();
                break;
            case "add":
                validateNumArgs("add", args, 2);
                Repository.add(args[1]);
                break;
            case "commit":
                if (args.length != 2) {
                    throw new GitletException("no message");
                }
                Repository.commit(args[1]);
                break;
            case "rm":
                validateNumArgs("rm", args, 2);
                Repository.rm(args[1]);
                break;
            case "log":
                validateNumArgs("log", args, 1);
                Repository.log();
                break;
            case "global-log":
                validateNumArgs("global-log", args, 1);
                Repository.globalLog();
                break;
            case "find":
                validateNumArgs("find", args, 2);
                Repository.find(args[1]);
                break;
            case "status":
                validateNumArgs("status", args, 1);
                Repository.status();
                break;
            case "checkout":
                if (args.length == 2) {
                    Repository.checkOutBranch(args[1]);
                } else if(args.length == 3) {
                    if (!args[1].equals("--")) {
                        throw new GitletException("Unsupported modifier.");
                    }
                    Repository.checkOutFileInHead(args[2]);
                } else if(args.length == 4) {
                    if (!args[2].equals("--")) {
                        throw new GitletException("Unsupported modifier.")
                    }
                    Repository.checkOutFileInCommit(args[1], args[3]);
                } else {
                    throw new GitletException("Invalid number of arguments" +
                            " for: checkout");
                }
                break;
            default:
                throw  new GitletException("Unknown command: " + args[0]);
        }
    }

    public static void validateNumArgs(String cmd, String[] args, int n) {
        if (args.length != n) {
            throw new RuntimeException(
                    String.format("Invalid number of arguments for: %s.", cmd));
        }
    }
}
