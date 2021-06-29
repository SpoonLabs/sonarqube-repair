package sorald.sonar;

import java.util.Collection;
import java.util.Optional;
import org.sonarsource.sonarlint.core.AbstractSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneSonarLintEngine;
import org.sonarsource.sonarlint.core.container.module.ModuleRegistry;
import org.sonarsource.sonarlint.core.container.standalone.StandaloneGlobalContainer;
import org.sonarsource.sonarlint.core.util.ProgressWrapper;

public class SonarLintEngine extends AbstractSonarLintEngine implements StandaloneSonarLintEngine {
    private final StandaloneGlobalContainer globalContainer;

    SonarLintEngine(StandaloneGlobalContainer globalContainer) {
        this.globalContainer = globalContainer;
    }

    @Override
    public void stop() {}

    @Override
    public Optional<StandaloneRuleDetails> getRuleDetails(String s) {
        return Optional.ofNullable(globalContainer.getRuleDetails(s));
    }

    @Override
    public Collection<StandaloneRuleDetails> getAllRuleDetails() {
        return globalContainer.getAllRuleDetails();
    }

    @Override
    public AnalysisResults analyze(
            StandaloneAnalysisConfiguration standaloneAnalysisConfiguration,
            IssueListener issueListener,
            LogOutput logOutput,
            ProgressMonitor progressMonitor) {

        return withModule(
                standaloneAnalysisConfiguration,
                moduleContainer ->
                        globalContainer.analyze(
                                moduleContainer,
                                standaloneAnalysisConfiguration,
                                issueListener,
                                new ProgressWrapper(null)));
    }

    @Override
    public Collection<PluginDetails> getPluginDetails() {
        return globalContainer.getPluginDetails();
    }

    @Override
    protected ModuleRegistry getModuleRegistry() {
        return globalContainer.getModuleRegistry();
    }
}
