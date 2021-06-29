package sorald.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine;
import sorald.Constants;
import sorald.FileUtils;
import sorald.PrettyPrintingStrategy;
import sorald.Processors;
import sorald.Repair;
import sorald.RepairStrategy;
import sorald.SoraldConfig;
import sorald.event.EventHelper;
import sorald.event.EventType;
import sorald.event.SoraldEventHandler;
import sorald.event.StatsMetadataKeys;
import sorald.event.collectors.RepairStatisticsCollector;
import sorald.event.models.ExecutionInfo;
import sorald.event.models.miner.MinedViolationEvent;
import sorald.event.models.repair.RuleRepairStatistics;
import sorald.processor.SoraldAbstractProcessor;
import sorald.rule.Rule;
import sorald.rule.RuleViolation;
import sorald.sonar.ProjectScanner;
import sorald.util.MavenUtils;

/** The CLI command for the primary repair application. */
@CommandLine.Command(
        name = Constants.REPAIR_COMMAND_NAME,
        mixinStandardHelpOptions = true,
        description = "Repair Sonar rule violations in a targeted project.")
class RepairCommand extends BaseCommand {
    String ruleKey;
    List<RuleViolation> specifiedRuleViolations = List.of();

    @CommandLine.Option(
            names = {Constants.ARG_SOURCE},
            description = "The path to the file or folder to be analyzed and possibly repaired.",
            required = true,
            converter = RealFileConverter.class)
    File source;

    @CommandLine.ArgGroup(multiplicity = "1")
    Rules rules;

    static class Rules {
        @CommandLine.Option(
                names = {Constants.ARG_RULE_KEY},
                description =
                        "Choose one of the following rule keys:\n" + Processors.RULE_DESCRIPTIONS,
                required = true)
        String ruleKey = null;

        @CommandLine.Option(
                names = Constants.ARG_RULE_VIOLATION_SPECIFIERS,
                description =
                        "One or more rule violation specifiers. Specifiers can be gathered "
                                + "with the '"
                                + Constants.MINE_COMMAND_NAME
                                + "' command using the "
                                + Constants.ARG_STATS_OUTPUT_FILE
                                + " option.",
                required = true,
                split = ",")
        List<String> ruleViolationSpecifiers = List.of();
    }

    @CommandLine.Option(
            names = {Constants.ARG_PRETTY_PRINTING_STRATEGY},
            description =
                    "Mode for pretty printing the source code: 'NORMAL', which means that all source code will be printed and its formatting might change (such as indentation), and 'SNIPER', which means that only statements changed towards the repair of Sonar rule violations will be printed.")
    PrettyPrintingStrategy prettyPrintingStrategy = PrettyPrintingStrategy.SNIPER;

    @CommandLine.Option(
            names = Constants.ARG_MAX_FIXES_PER_RULE,
            description = "Max number of fixes per rule.")
    int maxFixesPerRule = Integer.MAX_VALUE;

    @CommandLine.Option(
            names = Constants.ARG_REPAIR_STRATEGY,
            description =
                    "Type of repair strategy. DEFAULT - load everything without splitting up the folder in segments, "
                            + "MAVEN - use Maven to locate production source code and the classpath (test source code is ignored), "
                            + "SEGMENT - splitting the folder into smaller segments and repair one segment at a time (need to specify --maxFilesPerSegment if not default)")
    RepairStrategy repairStrategy = RepairStrategy.DEFAULT;

    @CommandLine.Option(
            names = Constants.ARG_MAX_FILES_PER_SEGMENT,
            description =
                    "Max number of files per loaded segment for segmented repair. It should be >= 3000 files per segment.")
    int maxFilesPerSegment = 6500;

    @Override
    public Integer call() throws IOException {
        postprocessArgs();
        validateArgs();
        SoraldConfig config = createConfig();

        var statsCollector = new RepairStatisticsCollector();
        List<SoraldEventHandler> eventHandlers =
                statsOutputFile == null ? List.of() : List.of(statsCollector);
        EventHelper.fireEvent(EventType.EXEC_START, eventHandlers);

        List<String> classpath = resolveClasspath();

        Set<RuleViolation> ruleViolations = resolveRuleViolations(eventHandlers, classpath);
        if (ruleViolations.isEmpty()) {
            System.out.println("No rule violations found, nothing to do ...");
        } else {
            SoraldAbstractProcessor<?> proc =
                    new Repair(config, classpath, eventHandlers).repair(ruleViolations);
            printEndProcess(proc);
        }

        EventHelper.fireEvent(EventType.EXEC_END, List.of(statsCollector));

        if (statsOutputFile != null) {
            // mine violations to trigger stats collection
            mineViolations(source, ruleKey, eventHandlers, classpath);
            writeStatisticsOutput(
                    statsCollector,
                    FileUtils.getClosestDirectory(source).toPath().toAbsolutePath().normalize());
        }

        return 0;
    }

    private List<String> resolveClasspath() {
        if (resolveClasspathFrom != null) {
            return MavenUtils.resolveClasspath(resolveClasspathFrom.toPath());
        } else if (repairStrategy == RepairStrategy.MAVEN) {
            return MavenUtils.resolveClasspath(source.toPath());
        } else {
            return List.of();
        }
    }

    private Set<RuleViolation> resolveRuleViolations(
            List<SoraldEventHandler> eventHandlers, List<String> classpath) {
        Set<RuleViolation> minedViolations =
                mineViolations(source, ruleKey, eventHandlers, classpath);

        if (!specifiedRuleViolations.isEmpty()) {
            specifiedRuleViolations.forEach(
                    specifiedViolation ->
                            checkSpecifiedViolationExists(specifiedViolation, minedViolations));

            return minedViolations.stream()
                    .filter(specifiedRuleViolations::contains)
                    .collect(Collectors.toSet());
        } else {
            return minedViolations;
        }
    }

    private void checkSpecifiedViolationExists(
            RuleViolation specifiedViolation, Collection<RuleViolation> minedViolations) {
        if (!minedViolations.contains(specifiedViolation)) {
            String violationSpecifier = specifiedViolation.relativeSpecifier(source.toPath());
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    String.format(
                            "No actual violation matching violation spec: '%s'",
                            violationSpecifier));
        }
    }

    /**
     * Mine violations from the target directory and the given rule key.
     *
     * @param target A target directory.
     * @param ruleKey Key of the rule to mine violations of.
     * @param eventHandlers Event handlers to use for events.
     * @param classpath
     * @return All found warnings.
     */
    private static Set<RuleViolation> mineViolations(
            File target,
            String ruleKey,
            List<SoraldEventHandler> eventHandlers,
            List<String> classpath) {
        Rule rule = Rule.of(ruleKey);
        Path projectPath = target.toPath().toAbsolutePath().normalize();
        Set<RuleViolation> violations =
                ProjectScanner.scanProject(
                        target, FileUtils.getClosestDirectory(target), List.of(rule), classpath);
        violations.forEach(
                warn ->
                        EventHelper.fireEvent(
                                new MinedViolationEvent(warn, projectPath), eventHandlers));
        return violations;
    }

    private void writeStatisticsOutput(RepairStatisticsCollector statsCollector, Path projectPath)
            throws IOException {
        var executionInfo =
                new ExecutionInfo(
                        spec.commandLine().getParseResult().originalArgs(),
                        SoraldVersionProvider.getVersionFromPropertiesResource(
                                SoraldVersionProvider.DEFAULT_RESOURCE_NAME),
                        System.getProperty(Constants.JAVA_VERSION_SYSTEM_PROPERTY),
                        target);

        List<RuleRepairStatistics> repairStats =
                RuleRepairStatistics.createRepairStatsList(statsCollector, projectPath);

        FileUtils.writeJSON(
                statsOutputFile,
                statsCollector,
                Map.of(
                        StatsMetadataKeys.EXECUTION_INFO,
                        executionInfo,
                        StatsMetadataKeys.REPAIRS,
                        repairStats));
    }

    private void validateArgs() {
        if (maxFilesPerSegment <= 0) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    Constants.ARG_MAX_FILES_PER_SEGMENT + " must be greater than 0");
        }

        validateRuleKey();
    }

    /** Perform further processing of raw command line args. */
    private void postprocessArgs() throws IOException {
        specifiedRuleViolations = parseRuleViolations(rules);
        ruleKey = parseRuleKey(rules, specifiedRuleViolations);
    }

    private List<RuleViolation> parseRuleViolations(Rules rules) throws IOException {
        List<RuleViolation> violations = new ArrayList<>();
        for (var spec : rules.ruleViolationSpecifiers) {
            violations.add(parseRuleViolation(spec));
        }
        return violations;
    }

    private String parseRuleKey(Rules rules, List<RuleViolation> ruleViolations) {
        return withSonarPrefix(
                ruleViolations.isEmpty()
                        ? rules.ruleKey
                        : ruleViolations.stream()
                                .map(RuleViolation::getRuleKey)
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        "no valid rule key in input, should not happen!")));
    }

    private void validateRuleKey() {
        if (Processors.getProcessor(ruleKey) == null) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Sorry, repair not available for rule "
                            + ruleKey
                            + ". See the available rules below.");
        }
    }

    private RuleViolation parseRuleViolation(String violationSpecifier) throws IOException {
        String[] parts = violationSpecifier.split(Constants.VIOLATION_SPECIFIER_SEP);
        String key = parts[0];
        String rawFilename = parts[1];
        Path absPath = source.toPath().resolve(rawFilename).toRealPath();
        int startLine = Integer.parseInt(parts[2]);
        int startCol = Integer.parseInt(parts[3]);
        int endLine = Integer.parseInt(parts[4]);
        int endCol = Integer.parseInt(parts[5]);
        return new SpecifiedViolation(
                withSonarPrefix(key), absPath, startLine, startCol, endLine, endCol);
    }

    private String withSonarPrefix(String key) {
        // TODO Remove this method when "sufficient" time has passed since introducing the S prefix
        //      to the CLI
        return key.startsWith("S") ? key : "S" + key;
    }

    private static void printEndProcess(SoraldAbstractProcessor<?> processor) {
        System.out.println("-----Number of fixes------");
        System.out.println(processor.getClass().getSimpleName() + ": " + processor.getNbFixes());
        System.out.println("-----End of report------");
    }

    private SoraldConfig createConfig() {
        SoraldConfig config = new SoraldConfig();
        config.setSource(source.getAbsolutePath());
        config.setPrettyPrintingStrategy(prettyPrintingStrategy);
        config.setMaxFixesPerRule(maxFixesPerRule);
        config.setMaxFilesPerSegment(maxFilesPerSegment);
        config.setRepairStrategy(repairStrategy);
        config.setStatsOutputFile(statsOutputFile);
        return config;
    }
}
