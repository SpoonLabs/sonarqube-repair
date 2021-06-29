package sorald.cli;

import java.nio.file.Path;
import sorald.rule.RuleViolation;

/** Rule violation specified from the CLI. */
class SpecifiedViolation extends RuleViolation {
    private final String ruleKey;
    private final Path absPath;
    private final int startLine;
    private final int startCol;
    private final int endLine;
    private final int endCol;

    SpecifiedViolation(
            String ruleKey, Path absPath, int startLine, int startCol, int endLine, int endCol) {
        this.ruleKey = ruleKey;
        this.absPath = absPath;
        this.startLine = startLine;
        this.endLine = endLine;
        this.startCol = startCol;
        this.endCol = endCol;
    }

    @Override
    public int getStartLine() {
        return startLine;
    }

    @Override
    public int getEndLine() {
        return endLine;
    }

    @Override
    public int getStartCol() {
        return startCol;
    }

    @Override
    public int getEndCol() {
        return endCol;
    }

    @Override
    public Path getAbsolutePath() {
        return absPath;
    }

    @Override
    public String getRuleKey() {
        return ruleKey;
    }
}
