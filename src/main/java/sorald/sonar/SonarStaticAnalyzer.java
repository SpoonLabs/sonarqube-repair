package sorald.sonar;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.Issue;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.container.standalone.StandaloneGlobalContainer;
import sorald.rule.Rule;
import sorald.rule.RuleViolation;
import sorald.rule.StaticAnalyzer;

public class SonarStaticAnalyzer implements StaticAnalyzer {
    private final File projectRoot;

    private static final Map<Path, StandaloneGlobalContainer> containerCache = new HashMap<>();

    public SonarStaticAnalyzer(File projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Override
    public Collection<RuleViolation> findViolations(
            List<File> files, List<Rule> rules, List<String> classpath) {
        return analyze(files, rules, classpath);
    }

    private Collection<RuleViolation> analyze(
            List<File> files, List<Rule> rules, List<String> classpath) {

        Path baseDir = projectRoot.toPath();
        StandaloneGlobalContainer container = getOrCreateGlobalContainer(baseDir);

        List<RuleKey> includedRules =
                rules.stream()
                        .map(rule -> new RuleKey("java", rule.getKey()))
                        .collect(Collectors.toList());
        List<RuleKey> excludedRules = getExcludedRuleKeys(Set.copyOf(includedRules), container);

        var inputFiles =
                files.stream()
                        .map(File::toPath)
                        .map(JavaInputFile::new)
                        .collect(Collectors.toList());
        var config =
                StandaloneAnalysisConfiguration.builder()
                        .setBaseDir(baseDir)
                        .addInputFiles()
                        .addIncludedRules(includedRules)
                        .addExcludedRules(excludedRules)
                        .addInputFiles(inputFiles)
                        .build();

        var engine = new SonarLintEngine(container);
        var issueHandler = new IssueHandler();
        engine.analyze(config, issueHandler, null, null);

        return issueHandler.issues.stream().map(ScannedViolation::new).collect(Collectors.toList());
    }

    private static List<RuleKey> getExcludedRuleKeys(
            Set<RuleKey> included, StandaloneGlobalContainer container) {
        return container.getAllRuleDetails().stream()
                .map(
                        ruleDetails -> {
                            String[] keyParts = ruleDetails.getKey().split(":");
                            String ruleRepository = keyParts[0];
                            String ruleKey = keyParts[1];
                            return new RuleKey(ruleRepository, ruleKey);
                        })
                .filter(ruleKey -> !included.contains(ruleKey))
                .collect(Collectors.toList());
    }

    private static class IssueHandler implements IssueListener {
        private final List<Issue> issues = new ArrayList<>();

        @Override
        public void handle(@Nonnull Issue issue) {
            issues.add(issue);
        }
    }

    private static StandaloneGlobalContainer getOrCreateGlobalContainer(Path baseDir) {
        if (containerCache.containsKey(baseDir)) {
            return containerCache.get(baseDir);
        }

        URL sonarJavaJar;
        try {
            sonarJavaJar =
                    new URL(
                            "file:///home/slarse/.m2/repository/org/sonarsource/java/sonar-java-plugin/6.9.0.23563/sonar-java-plugin-6.9.0.23563.jar");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        StandaloneGlobalConfiguration globalConfiguration =
                StandaloneGlobalConfiguration.builder()
                        .setWorkDir(baseDir)
                        .addPlugins(sonarJavaJar)
                        .addEnabledLanguages(Language.JAVA)
                        .build();
        StandaloneGlobalContainer container = StandaloneGlobalContainer.create(globalConfiguration);
        container.startComponents();

        containerCache.put(baseDir, container);

        return container;
    }
}
