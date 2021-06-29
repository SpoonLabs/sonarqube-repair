package sorald;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static sorald.Assertions.assertHasRuleViolation;
import static sorald.Assertions.assertNoRuleViolations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sorald.event.StatsMetadataKeys;
import sorald.processor.CastArithmeticOperandProcessor;
import sorald.processor.DeadStoreProcessor;
import sorald.rule.Rule;

public class MavenLauncherTest {

    @Test
    public void sorald_repairsProductionAndTestCode_inMavenProject(@TempDir File workdir)
            throws IOException {
        // arrange
        org.apache.commons.io.FileUtils.copyDirectory(
                TestHelper.PATH_TO_RESOURCES_FOLDER
                        .resolve("scenario_test_files/simple-java8-maven-project")
                        .toFile(),
                workdir);

        File productionFile =
                workdir.toPath().resolve("src/main/java/sorald/test/App.java").toFile();
        File testFile = workdir.toPath().resolve("src/test/java/sorald/test/AppTest.java").toFile();

        Rule deadStoreRule = Rule.of(new DeadStoreProcessor().getRuleKey());

        assertHasRuleViolation(productionFile, deadStoreRule);
        assertHasRuleViolation(testFile, deadStoreRule);

        String[] args =
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    workdir.getAbsolutePath(),
                    Constants.ARG_RULE_KEY,
                    new DeadStoreProcessor().getRuleKey(),
                    Constants.ARG_REPAIR_STRATEGY,
                    RepairStrategy.MAVEN.name()
                };

        // act
        Main.main(args);

        // assert
        assertNoRuleViolations(productionFile, deadStoreRule);
        assertNoRuleViolations(testFile, deadStoreRule);
    }

    /**
     * Test that Sorald can repair a rule violation for which Sonar requires the full classpath to
     * detect.
     */
    @Test
    void sorald_repairsRuleViolation_thatRequiresClasspathToDetect(@TempDir File workdir)
            throws IOException {
        // arrange
        org.apache.commons.io.FileUtils.copyDirectory(
                TestHelper.PATH_TO_RESOURCES_FOLDER
                        .resolve("scenario_test_files")
                        .resolve("classpath-dependent-project")
                        .toFile(),
                workdir);

        Path statsFile = workdir.toPath().resolve("stats.json");

        String castArithmOperandKey = new CastArithmeticOperandProcessor().getRuleKey();
        String[] args = {
            Constants.REPAIR_COMMAND_NAME,
            Constants.ARG_SOURCE,
            workdir.getAbsolutePath(),
            Constants.ARG_RULE_KEY,
            castArithmOperandKey,
            Constants.ARG_REPAIR_STRATEGY,
            RepairStrategy.MAVEN.name(),
            Constants.ARG_STATS_OUTPUT_FILE,
            statsFile.toString()
        };

        // act
        Main.main(args);

        // assert
        JSONObject stats = FileUtils.readJSON(statsFile);
        JSONArray repairs = stats.getJSONArray(StatsMetadataKeys.REPAIRS);
        assertThat(repairs.length(), equalTo(1));
        JSONObject repair = repairs.getJSONObject(0);
        assertThat(
                repair.getString(StatsMetadataKeys.REPAIR_RULE_KEY), equalTo(castArithmOperandKey));
    }
}
