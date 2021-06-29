package sorald.event.models.repair;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import sorald.event.collectors.RepairStatisticsCollector;
import sorald.event.models.RepairEvent;
import sorald.event.models.WarningLocation;
import sorald.event.models.miner.MinedViolationEvent;
import sorald.rule.Rule;

/** Repair statistics for a single rule. */
public class RuleRepairStatistics {
    private final String ruleKey;
    private final String ruleName;
    private final List<MinedViolationEvent> violationsBefore;
    private final List<MinedViolationEvent> violationsAfter;
    private final List<WarningLocation> performedRepairsLocations;
    private final List<WarningLocation> crashedRepairsLocations;

    public RuleRepairStatistics(
            String ruleKey,
            String ruleName,
            List<MinedViolationEvent> violationsBefore,
            List<MinedViolationEvent> violationsAfter,
            List<RepairEvent> repairedViolations,
            List<RepairEvent> failedRepairs,
            Path projectPath) {
        this.ruleKey = ruleKey;
        this.ruleName = ruleName;
        this.violationsBefore = new ArrayList<>(violationsBefore);
        this.violationsAfter = new ArrayList<>(violationsAfter);

        this.performedRepairsLocations = toWarningLocations(repairedViolations, projectPath);
        this.crashedRepairsLocations = toWarningLocations(failedRepairs, projectPath);
    }

    private static List<WarningLocation> toWarningLocations(
            List<RepairEvent> repairEvents, Path projectPath) {
        return repairEvents.stream()
                .map(
                        repairEvent ->
                                new WarningLocation(repairEvent.getRuleViolation(), projectPath))
                .collect(Collectors.toList());
    }

    public String getRuleKey() {
        return ruleKey;
    }

    public String getRuleName() {
        return ruleName;
    }

    public List<WarningLocation> getPerformedRepairsLocations() {
        return Collections.unmodifiableList(performedRepairsLocations);
    }

    public List<WarningLocation> getCrashedRepairsLocations() {
        return Collections.unmodifiableList(crashedRepairsLocations);
    }

    public int getNbViolationsBefore() {
        return violationsBefore.size();
    }

    public int getNbViolationsAfter() {
        return violationsAfter.size();
    }

    public int getNbPerformedRepairs() {
        return performedRepairsLocations.size();
    }

    public int getNbCrashedRepairs() {
        return crashedRepairsLocations.size();
    }

    /**
     * Convert a repair statistics collector into a list of repair statistics containers designed
     * for pretty JSON output.
     *
     * @param statsCollector A statistics collector for the repair mode.
     * @param projectPath Path to the project.
     * @return A list of repair statistics container, one for each rule.
     */
    public static List<RuleRepairStatistics> createRepairStatsList(
            RepairStatisticsCollector statsCollector, Path projectPath) {
        Map<String, List<RepairEvent>> keyToRepair = statsCollector.performedRepairs();
        Map<String, List<RepairEvent>> keyToFailure = statsCollector.crashedRepairs();
        Map<String, List<MinedViolationEvent>> keyToViolationsBefore =
                statsCollector.minedViolationsBefore();
        Map<String, List<MinedViolationEvent>> keyToViolationsAfter =
                statsCollector.minedViolationsAfter();

        Set<String> distinctKeys = new HashSet<>(keyToRepair.keySet());
        distinctKeys.addAll(keyToFailure.keySet());

        return distinctKeys.stream()
                .map(
                        key -> {
                            List<RepairEvent> repairs = keyToRepair.getOrDefault(key, List.of());
                            List<RepairEvent> failures = keyToFailure.getOrDefault(key, List.of());
                            List<MinedViolationEvent> violationsBefore =
                                    keyToViolationsBefore.getOrDefault(key, List.of());
                            List<MinedViolationEvent> violationsAfter =
                                    keyToViolationsAfter.getOrDefault(key, List.of());
                            String ruleName = Rule.of(key).getName();
                            return new RuleRepairStatistics(
                                    key,
                                    ruleName,
                                    violationsBefore,
                                    violationsAfter,
                                    repairs,
                                    failures,
                                    projectPath);
                        })
                .collect(Collectors.toList());
    }
}
