package sorald.sonar;

import java.nio.file.Path;
import org.sonarsource.sonarlint.core.client.api.common.TextRange;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import sorald.rule.RuleViolation;

/** Facade around {@link org.sonarsource.sonarlint.core.client.api.common.analysis.Issue} */
class ScannedViolation extends RuleViolation {
    private final Issue issue;
    private final TextRange textRange;

    ScannedViolation(Issue issue) {
        textRange = issue.getTextRange();
        if (textRange == null) {
            throw new IllegalArgumentException(
                    "issue for '" + issue.getRuleKey() + "' lacks primary location");
        }
        this.issue = issue;
    }

    @Override
    public int getStartLine() {
        return textRange.getStartLine();
    }

    @Override
    public int getEndLine() {
        return textRange.getEndLine();
    }

    @Override
    public int getStartCol() {
        return textRange.getStartLineOffset();
    }

    @Override
    public int getEndCol() {
        return textRange.getEndLineOffset();
    }

    @Override
    public Path getAbsolutePath() {
        Path path = issue.getInputFile().getClientObject();
        return path.toAbsolutePath().normalize();
    }

    @Override
    public String getRuleKey() {
        return issue.getRuleKey().split(":")[1];
    }

    @Override
    public String getMessage() {
        return issue.getMessage();
    }
}
