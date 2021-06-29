package sorald.event.models.miner;

import java.nio.file.Path;
import sorald.event.EventType;
import sorald.event.SoraldEvent;
import sorald.event.models.WarningLocation;
import sorald.rule.Rule;
import sorald.rule.RuleViolation;

/** Event representing a mined rule information */
public class MinedViolationEvent implements SoraldEvent {
    private final String ruleKey;
    private final String ruleName;
    private final WarningLocation warningLocation;

    /**
     * Wrapping just one violation inside a mined-rule event.
     *
     * @param violation A rule violation.
     * @param projectPath Root path to the project being mined.
     */
    public MinedViolationEvent(RuleViolation violation, Path projectPath) {
        this.ruleKey = violation.getRuleKey();
        this.ruleName = Rule.of(violation.getRuleKey()).getName();
        this.warningLocation = new WarningLocation(violation, projectPath);
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public String getRuleName() {
        return ruleName;
    }

    @Override
    public EventType type() {
        return EventType.MINED;
    }

    public WarningLocation getWarningLocation() {
        return warningLocation;
    }
}
