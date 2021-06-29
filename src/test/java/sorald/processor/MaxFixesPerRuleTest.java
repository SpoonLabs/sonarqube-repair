package sorald.processor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import sorald.Assertions;
import sorald.Constants;
import sorald.Main;
import sorald.TestHelper;
import sorald.rule.Rule;

public class MaxFixesPerRuleTest {
    @Test
    public void arrayToStringProcessorTest() throws Exception {
        String fileName = "ArrayHashCodeAndToString.java";
        Path pathToBuggyFile = TestHelper.createTemporaryTestResourceWorkspace().resolve(fileName);
        Rule rule = Rule.of(new ArrayHashCodeAndToStringProcessor().getRuleKey());

        Assertions.assertHasRuleViolation(pathToBuggyFile.toFile(), rule);
        Main.main(
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    pathToBuggyFile.toString(),
                    Constants.ARG_RULE_KEY,
                    new ArrayHashCodeAndToStringProcessor().getRuleKey(),
                    Constants.ARG_MAX_FIXES_PER_RULE,
                    "3"
                });
        TestHelper.removeComplianceComments(pathToBuggyFile.toString());

        try {
            Assertions.assertHasRuleViolation(pathToBuggyFile.toFile(), rule);
        } catch (AssertionError e) {
            assertThat(e.getMessage(), containsString("Unexpected at [27]"));
        }
    }
}
