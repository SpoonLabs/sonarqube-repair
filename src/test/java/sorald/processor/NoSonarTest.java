package sorald.processor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static sorald.Assertions.assertHasRuleViolation;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import sorald.Constants;
import sorald.Main;
import sorald.TestHelper;
import sorald.rule.Rule;

public class NoSonarTest {
    @Test
    public void noSonarTesting() throws Exception {
        String fileName = "NOSONARCommentTest.java";
        Path pathToBuggyFile = TestHelper.createTemporaryTestResourceWorkspace().resolve(fileName);
        Rule rule = Rule.of(new ArrayHashCodeAndToStringProcessor().getRuleKey());

        assertHasRuleViolation(pathToBuggyFile.toFile(), rule);
        Main.main(
                new String[] {
                    Constants.REPAIR_COMMAND_NAME,
                    Constants.ARG_SOURCE,
                    pathToBuggyFile.toString(),
                    Constants.ARG_RULE_KEY,
                    rule.getKey(),
                    Constants.ARG_MAX_FIXES_PER_RULE,
                    "3"
                });
        TestHelper.removeComplianceComments(pathToBuggyFile.toString());

        String lineWithNosonarComment = Files.readAllLines(pathToBuggyFile).get(7);
        assertThat(
                lineWithNosonarComment,
                containsString("String argStr = args.toString(); // Noncompliant, NOSONAR"));
    }
}
