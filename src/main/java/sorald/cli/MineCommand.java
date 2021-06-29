package sorald.cli;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import picocli.CommandLine;
import sorald.Constants;
import sorald.FileUtils;
import sorald.Processors;
import sorald.event.StatsMetadataKeys;
import sorald.event.collectors.MinerStatisticsCollector;
import sorald.event.models.ExecutionInfo;
import sorald.miner.MineSonarWarnings;
import sorald.rule.Rule;
import sorald.rule.RuleType;
import sorald.rule.Rules;
import sorald.util.MavenUtils;

/** CLI Command for Sorald's mining functionality. */
@CommandLine.Command(
        name = Constants.MINE_COMMAND_NAME,
        mixinStandardHelpOptions = true,
        description = "Mine a project for Sonar warnings.")
class MineCommand extends BaseCommand {

    @CommandLine.Option(
            names = {Constants.ARG_SOURCE},
            description = "The path to the file or folder to be analyzed and possibly repaired.")
    File source;

    @CommandLine.Option(
            names = Constants.ARG_STATS_ON_GIT_REPOS,
            description = "If the stats should be computed on git repos.")
    boolean statsOnGitRepos;

    @CommandLine.Option(
            names = Constants.ARG_MINER_OUTPUT_FILE,
            description = "The path to the output file.")
    File minerOutputFile;

    @CommandLine.Option(
            names = Constants.ARG_GIT_REPOS_LIST,
            description = "The path to the repos list.")
    File reposList;

    @CommandLine.Option(
            names = Constants.ARG_TEMP_DIR,
            description = "The path to the temp directory.")
    File tempDir;

    @CommandLine.Option(
            names = {Constants.ARG_RULE_TYPES},
            description =
                    "One or more types of rules to check for (use ',' to separate multiple types). Choices: ${COMPLETION-CANDIDATES}",
            split = ",")
    private List<RuleType> ruleTypes = new ArrayList<>();

    @CommandLine.Option(
            names = {Constants.ARG_HANDLED_RULES},
            description =
                    "When this argument is used, Sorald only mines violations of the rules that can be fixed by Sorald.")
    private boolean handledRules;

    @Override
    public Integer call() throws Exception {
        validateArgs();

        List<Rule> checks = inferRules(ruleTypes, handledRules);

        var statsCollector = new MinerStatisticsCollector();
        List<String> classpath =
                resolveClasspathFrom != null
                        ? MavenUtils.resolveClasspath(resolveClasspathFrom.toPath())
                        : List.of();

        var miner =
                new MineSonarWarnings(
                        statsOutputFile == null ? List.of() : List.of(statsCollector), classpath);

        if (statsOnGitRepos) {
            List<String> reposList = Files.readAllLines(this.reposList.toPath());
            miner.mineGitRepos(checks, minerOutputFile.getAbsolutePath(), reposList, tempDir);
        } else {
            miner.mineLocalProject(checks, source.toPath().normalize().toAbsolutePath().toString());
        }

        if (statsOutputFile != null) {
            Map<String, Object> additionalStatData =
                    Map.of(
                            StatsMetadataKeys.EXECUTION_INFO,
                            new ExecutionInfo(
                                    spec.commandLine().getParseResult().originalArgs(),
                                    SoraldVersionProvider.getVersionFromPropertiesResource(
                                            SoraldVersionProvider.DEFAULT_RESOURCE_NAME),
                                    System.getProperty(Constants.JAVA_VERSION_SYSTEM_PROPERTY),
                                    target));

            FileUtils.writeJSON(statsOutputFile, statsCollector, additionalStatData);
        }

        return 0;
    }

    /** Perform validation on the parsed arguments. */
    private void validateArgs() {
        if (resolveClasspathFrom != null
                && !MavenUtils.isMavenProjectRoot(resolveClasspathFrom.toPath())) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    String.format(
                            "%s is only supported for Maven projects, but %s has no pom.xml",
                            Constants.ARG_RESOLVE_CLASSPATH_FROM, source));
        }
    }

    /**
     * Infer which rules to use based on rule types specified (or left unspecified) on the command
     * line.
     */
    private static List<Rule> inferRules(List<RuleType> ruleTypes, boolean handledRules) {
        List<Rule> rules =
                List.copyOf(
                        ruleTypes.isEmpty()
                                ? Rules.getAllRules()
                                : Rules.getRulesByType(ruleTypes));

        return !handledRules
                ? rules
                : rules.stream()
                        .filter(rule -> Processors.getProcessor(rule.getKey()) != null)
                        .collect(Collectors.toList());
    }
}
