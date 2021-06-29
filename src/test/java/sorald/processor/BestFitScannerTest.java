package sorald.processor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import sorald.Constants;
import sorald.TestHelper;
import sorald.rule.RuleViolation;
import sorald.sonar.BestFitScanner;
import sorald.sonar.ProjectScanner;
import sorald.sonar.SonarRule;
import spoon.FluentLauncher;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtType;

public class BestFitScannerTest {

    @Test
    public void calculateBestFits_throws_whenProcessorConcernsDifferentRuleThanViolations() {
        File projectBaseDir = TestHelper.PATH_TO_RESOURCES_FOLDER.toFile();
        Set<RuleViolation> xxeProcessingViolations =
                ProjectScanner.scanProject(
                        projectBaseDir,
                        projectBaseDir,
                        new SonarRule(new XxeProcessingProcessor().getRuleKey()));
        SoraldAbstractProcessor<?> arrayHashCodeProc = new ArrayHashCodeAndToStringProcessor();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.getEnvironment().setComplianceLevel(Constants.DEFAULT_COMPLIANCE_LEVEL);
        launcher.addInputResource(projectBaseDir.toString());
        CtModel model = launcher.buildModel();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BestFitScanner.calculateBestFits(
                                model.getUnnamedModule(),
                                xxeProcessingViolations,
                                arrayHashCodeProc));
    }

    /**
     * Incomplete processors should only be fed with the best position match. Otherwise, they may
     * say no to repair an element because they are incomplete, and the say yes to an element that
     * does not actually need repair.
     */
    @Test
    public void calculateBestFits_onlyProvidesBestPositionMatch_toIncompleteProcessor() {
        // this input file contains two levels of class nesting, so any position in the innermost
        // class is also contained in two other classes
        Path inputFileWithNestedClasses =
                ProcessorTestHelper.TEST_FILES_ROOT
                        .resolve("S2057_SerialVersionUidCheck")
                        .resolve("NestedInNestedGuiClass.java");

        CtModel model =
                new FluentLauncher()
                        .inputResource(inputFileWithNestedClasses.toString())
                        .complianceLevel(Constants.DEFAULT_COMPLIANCE_LEVEL)
                        .buildModel();

        CtType<?> innerMostClass =
                (CtType<?>)
                        model.getUnnamedModule()
                                .getElements(
                                        e ->
                                                e instanceof CtType<?>
                                                        && ((CtType<?>) e)
                                                                .getSimpleName()
                                                                .equals("Serial"))
                                .get(0);
        SourcePosition innerMostClassPos = innerMostClass.getPosition();
        IncompleteClassProc incompleteClassProc = new IncompleteClassProc();

        RuleViolation ruleViolation =
                new RuleViolation() {
                    @Override
                    public int getStartLine() {
                        return innerMostClassPos.getLine();
                    }

                    @Override
                    public int getEndLine() {
                        return innerMostClassPos.getEndLine();
                    }

                    @Override
                    public int getStartCol() {
                        return innerMostClassPos.getColumn();
                    }

                    @Override
                    public int getEndCol() {
                        return innerMostClassPos.getEndColumn();
                    }

                    @Override
                    public Path getAbsolutePath() {
                        return innerMostClassPos.getFile().toPath().toAbsolutePath();
                    }

                    @Override
                    public String getRuleKey() {
                        return incompleteClassProc.getRuleKey();
                    }
                };

        BestFitScanner.calculateBestFits(
                model.getUnnamedModule(), Set.of(ruleViolation), incompleteClassProc);

        assertThat(incompleteClassProc.receivedToCanRepair.size(), equalTo(1));
        assertThat(incompleteClassProc.receivedToCanRepair.get(0), is(innerMostClass));
    }

    private static class IncompleteClassProc extends SoraldAbstractProcessor<CtClass<?>> {
        private List<CtClass<?>> receivedToCanRepair = new ArrayList<>();

        @Override
        protected boolean canRepairInternal(CtClass<?> candidate) {
            receivedToCanRepair.add(candidate);
            return false;
        }

        @Override
        protected void repairInternal(CtClass<?> element) {}

        @Override
        public boolean isIncomplete() {
            return true;
        }

        @Override
        public String getRuleKey() {
            return "9999";
        }
    }
}
