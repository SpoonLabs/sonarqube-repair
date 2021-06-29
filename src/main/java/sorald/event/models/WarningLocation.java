package sorald.event.models;

import java.nio.file.Path;
import sorald.rule.RuleViolation;

public class WarningLocation {
    private final String filePath;
    private final Integer startLine;
    private final Integer endLine;
    private final Integer startColumn;
    private final Integer endColumn;
    private final String violationSpecifier;

    public WarningLocation(RuleViolation violation, Path projectPath) {
        this.filePath = projectPath.relativize(violation.getAbsolutePath()).toString();
        this.startLine = violation.getStartLine();
        this.endLine = violation.getEndLine();
        this.startColumn = violation.getStartCol();
        this.endColumn = violation.getEndCol();
        this.violationSpecifier = violation.relativeSpecifier(projectPath);
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getStartLine() {
        return startLine;
    }

    public Integer getEndLine() {
        return endLine;
    }

    public Integer getStartColumn() {
        return startColumn;
    }

    public Integer getEndColumn() {
        return endColumn;
    }

    public String getViolationSpecifier() {
        return violationSpecifier;
    }
}
