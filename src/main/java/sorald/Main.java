package sorald;

import sorald.cli.Cli;

public class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            new IllegalArgumentException("Too few args!");
        }

        try {
            throw new InterruptedException();
        } catch (InterruptedException | IllegalArgumentException e) {
            System.out.println("oops!");
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }

        int exitStatus = Cli.createCli().execute(args);
        if (exitStatus != 0) {
            int otherExitStatus = exitStatus;
            System.exit(exitStatus);
        }
    }
}
