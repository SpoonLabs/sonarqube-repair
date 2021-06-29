package sorald.sonar;

import java.util.Collection;
import java.util.stream.Collectors;
import sorald.rule.Rule;

/** Class that knows about all Sonar rules. Should ONLY be used by {@link sorald.rule.Rules}. */
public class SonarRules {
    private SonarRules() {}

    /**
     * Get all SonarJava rules.
     *
     * @return All SonarJava rules.
     */
    public static Collection<Rule> getAllRules() {
        return Checks.getAllChecks().stream()
                .map(Checks::getRuleKey)
                .map(Rule::of)
                .collect(Collectors.toList());
    }
}
