package sorald.sonar;

import java.util.Objects;
import sorald.rule.Rule;
import sorald.rule.RuleType;

public class SonarRule implements Rule {
    private final String key;
    private final String name;
    private final RuleType type;

    public SonarRule(String key) {
        this.key = key;
        var check = Checks.getCheck(key);
        this.name = check.getSimpleName().replaceFirst("Check$", "");
        this.type = Checks.getRuleType(check);
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public RuleType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SonarRule sonarRule = (SonarRule) o;
        return key.equals(sonarRule.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
