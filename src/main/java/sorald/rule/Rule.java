package sorald.rule;

import sorald.sonar.SonarRule;

/** A static analysis rule */
public interface Rule {

    /** @return A key that uniquely identifies this rule within Sorald. */
    String getKey();

    /** @return The name of this rule. */
    String getName();

    /** @return The type of this rule. */
    RuleType getType();

    /**
     * Create a rule based on the key.
     *
     * @param key A key for which to create a rule.
     * @return A rule based on the key.
     */
    static Rule of(String key) {
        return new SonarRule(key);
    }
}
