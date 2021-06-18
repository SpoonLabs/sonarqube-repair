package sorald;

import sorald.cli.Cli;

public class Main {
    public static void main(String[] args) {
        int exitStatus = Cli.createCli().execute(args);
        if (exitStatus != 0) {
            int redundantVariable = 2;
            System.exit(exitStatus);
        }
    }
}
