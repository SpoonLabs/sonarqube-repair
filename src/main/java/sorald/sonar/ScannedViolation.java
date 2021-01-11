package sorald.sonar;

import org.sonar.java.AnalyzerMessage;
import org.sonar.plugins.java.api.JavaCheck;

/** Facade around {@link org.sonar.java.AnalyzerMessage} */
class ScannedViolation extends RuleViolation {
    private final AnalyzerMessage message;

    ScannedViolation(AnalyzerMessage message) {
        if (message.primaryLocation() == null) {
            throw new IllegalArgumentException(
                    "message for '"
                            + getCheckName(message.getCheck())
                            + "' lacks primary location");
        }
        this.message = message;
    }

    @Override
    public int getStartLine() {
        return message.primaryLocation().startLine;
    }

    @Override
    public int getEndLine() {
        return message.primaryLocation().endLine;
    }

    @Override
    public int getStartCol() {
        return message.primaryLocation().startCharacter;
    }

    @Override
    public int getEndCol() {
        return message.primaryLocation().endCharacter;
    }

    @Override
    public String getFileName() {
        return message.getInputComponent().key().replace(":", "");
    }

    @Override
    public String getCheckName() {
        return getCheckName(message.getCheck());
    }

    @Override
    public String getRuleKey() {
        return Checks.getRuleKey(message.getCheck().getClass());
    }

    private static String getCheckName(JavaCheck check) {
        return check.getClass().getSimpleName();
    }
}
