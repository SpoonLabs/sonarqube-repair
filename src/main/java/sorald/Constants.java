package sorald;

import org.sonar.java.checks.*;
import org.sonar.java.checks.naming.MethodNamedEqualsCheck;
import org.sonar.java.checks.naming.MethodNamedHashcodeOrEqualCheck;
import org.sonar.java.checks.serialization.CustomSerializationMethodCheck;
import org.sonar.java.checks.serialization.ExternalizableClassConstructorCheck;
import org.sonar.java.checks.serialization.SerializableObjectInSessionCheck;
import org.sonar.java.checks.serialization.SerializableSuperConstructorCheck;
import org.sonar.java.checks.spring.ControllerWithSessionAttributesCheck;
import org.sonar.java.checks.spring.SpringComponentWithWrongScopeCheck;
import org.sonar.java.checks.spring.SpringIncompatibleTransactionalCheck;
import org.sonar.java.checks.spring.SpringScanDefaultPackageCheck;
import org.sonar.java.checks.synchronization.DoubleCheckedLockingCheck;
import org.sonar.java.checks.synchronization.SynchronizationOnGetClassCheck;
import org.sonar.java.checks.synchronization.TwoLocksWaitCheck;
import org.sonar.java.checks.synchronization.ValueBasedObjectUsedForLockCheck;
import org.sonar.java.checks.unused.UnusedReturnedDataCheck;
import org.sonar.java.checks.unused.UnusedThrowableCheck;
import org.sonar.java.se.checks.*;
import org.sonar.plugins.java.api.JavaFileScanner;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Constants {

	public static final String ARG_SYMBOL = "--";
	public static final String ARG_RULE_KEYS = "ruleKeys";
	public static final String ARG_ORIGINAL_FILES_PATH = "originalFilesPath";
	public static final String ARG_STATS_ON_GIT_REPOS = "statsOnGitRepos";
	public static final String ARG_STATS_OUTPUT_FILE = "statsOutputFile";
	public static final String ARG_GIT_REPOS_LIST = "gitReposList";
	public static final String ARG_TEMP_DIR = "tempDir";
	public static final String ARG_WORKSPACE = "workspace";
	public static final String ARG_GIT_REPO_PATH = "gitRepoPath";
	public static final String ARG_PRETTY_PRINTING_STRATEGY = "prettyPrintingStrategy";
	public static final String ARG_FILE_OUTPUT_STRATEGY = "fileOutputStrategy";
	public static final String ARG_MAX_FIXES_PER_RULE = "maxFixesPerRule";
	public static final String ARG_REPAIR_STRATEGY = "repairStrategy";
	public static final String ARG_MAX_FILES_PER_SEGMENT = "maxFilesPerSegment";
	
	public static final String PROCESSOR_PACKAGE = "sorald.processor";

	public static final String SORALD_WORKSPACE = "sorald-workspace";
	public static final String PATCHES = "SoraldGitPatches";
	public static final String PATCH_FILE_PREFIX = "soraldpatch_";
	public static final String PATH_TO_RESOURCES_FOLDER = "./src/test/resources/";

	public static final String JAVA_EXT = ".java";
	public static final String PATCH_EXT = ".patch";

	public static final String SPOONED = "spooned";
	public static final String INTERMEDIATE = "intermediate";
	public static final String SPOONED_INTERMEDIATE = SPOONED + File.separator + INTERMEDIATE;

	public static final String INT = "int";
	public static final String LONG = "long";
	public static final String FLOAT = "float";
	public static final String DOUBLE = "double";

	public static final String STRING_QUALIFIED_NAME = "java.lang.String";

	public static final String TOSTRING_METHOD_NAME = "toString";
	public static final String HASHCODE_METHOD_NAME = "hashCode";

	public static final String PATH_TO_STATS_OUTPUT = "experimentation/stats/warnings";

	public static final List<Class<? extends JavaFileScanner>> SONAR_CHECK_CLASSES;

	static {
		List<Class<? extends JavaFileScanner>> sonarChecks = new ArrayList<>();
		sonarChecks.add(ControllerWithSessionAttributesCheck.class);
		sonarChecks.add(SpringScanDefaultPackageCheck.class);
		sonarChecks.add(TwoLocksWaitCheck.class);
		sonarChecks.add(PreparedStatementAndResultSetCheck.class);
		sonarChecks.add(ThreadSleepCheck.class);
		sonarChecks.add(PrintfFailCheck.class);
		sonarChecks.add(ThreadWaitCallCheck.class);
		sonarChecks.add(SpringIncompatibleTransactionalCheck.class);
		sonarChecks.add(DoubleCheckedLockingCheck.class);
		sonarChecks.add(GettersSettersOnRightFieldCheck.class);
		sonarChecks.add(RunFinalizersCheck.class);
		sonarChecks.add(ScheduledThreadPoolExecutorZeroCheck.class);
		sonarChecks.add(ReuseRandomCheck.class);
		sonarChecks.add(ObjectFinalizeOverloadedCheck.class);
		sonarChecks.add(ReturnInFinallyCheck.class);
		sonarChecks.add(ThreadLocalCleanupCheck.class);
		sonarChecks.add(CompareStringsBoxedTypesWithEqualsCheck.class);
		sonarChecks.add(InputStreamReadCheck.class);
		sonarChecks.add(CompareToNotOverloadedCheck.class);
		sonarChecks.add(IterableIteratorCheck.class);
		sonarChecks.add(OverwrittenKeyCheck.class);
		sonarChecks.add(DateFormatWeekYearCheck.class);
		sonarChecks.add(UnusedThrowableCheck.class);
		sonarChecks.add(CollectionSizeAndArrayLengthCheck.class);
		sonarChecks.add(AllBranchesAreIdenticalCheck.class);
		sonarChecks.add(SynchronizedOverrideCheck.class);
		sonarChecks.add(ValueBasedObjectUsedForLockCheck.class);
		sonarChecks.add(AssertOnBooleanVariableCheck.class);
		sonarChecks.add(VolatileVariablesOperationsCheck.class);
		sonarChecks.add(SynchronizationOnGetClassCheck.class);
		sonarChecks.add(DoubleCheckedLockingAssignmentCheck.class);
		sonarChecks.add(StringCallsBeyondBoundsCheck.class);
		sonarChecks.add(RawByteBitwiseOperationsCheck.class);
		sonarChecks.add(SyncGetterAndSetterCheck.class);
		sonarChecks.add(StaticMultithreadedUnsafeFieldsCheck.class);
		sonarChecks.add(NullShouldNotBeUsedWithOptionalCheck.class);
		sonarChecks.add(DoublePrefixOperatorCheck.class);
		sonarChecks.add(WrongAssignmentOperatorCheck.class);
		sonarChecks.add(UnusedReturnedDataCheck.class);
		sonarChecks.add(InappropriateRegexpCheck.class);
		sonarChecks.add(NotifyCheck.class);
		sonarChecks.add(SynchronizedFieldAssignmentCheck.class);
		sonarChecks.add(SerializableObjectInSessionCheck.class);
		sonarChecks.add(WaitInSynchronizeCheck.class);
		sonarChecks.add(ForLoopFalseConditionCheck.class);
		sonarChecks.add(ForLoopIncrementSignCheck.class);
		sonarChecks.add(TransactionalMethodVisibilityCheck.class);
		sonarChecks.add(ServletInstanceFieldCheck.class);
		sonarChecks.add(ToStringReturningNullCheck.class);
		sonarChecks.add(EqualsOnAtomicClassCheck.class);
		sonarChecks.add(IgnoredReturnValueCheck.class);
		sonarChecks.add(ConfusingOverloadCheck.class);
		sonarChecks.add(CollectionInappropriateCallsCheck.class);
		sonarChecks.add(SillyEqualsCheck.class);
		sonarChecks.add(PrimitiveWrappersInTernaryOperatorCheck.class);
		sonarChecks.add(InterruptedExceptionCheck.class);
		sonarChecks.add(ThreadOverridesRunCheck.class);
		sonarChecks.add(LongBitsToDoubleOnIntCheck.class);
		sonarChecks.add(UselessIncrementCheck.class);
		sonarChecks.add(SillyStringOperationsCheck.class);
		sonarChecks.add(NonSerializableWriteCheck.class);
		sonarChecks.add(ArrayHashCodeAndToStringCheck.class);
		sonarChecks.add(CollectionCallingItselfCheck.class);
		sonarChecks.add(BigDecimalDoubleConstructorCheck.class);
		sonarChecks.add(InvalidDateValuesCheck.class);
		sonarChecks.add(ReflectionOnNonRuntimeAnnotationCheck.class);
		sonarChecks.add(CustomSerializationMethodCheck.class);
		sonarChecks.add(ExternalizableClassConstructorCheck.class);
		sonarChecks.add(ClassComparedByNameCheck.class);
		sonarChecks.add(DuplicateConditionIfElseIfCheck.class);
		sonarChecks.add(SynchronizationOnStringOrBoxedCheck.class);
		sonarChecks.add(HasNextCallingNextCheck.class);
		sonarChecks.add(IdenticalOperandOnBinaryExpressionCheck.class);
		sonarChecks.add(LoopExecutingAtMostOnceCheck.class);
		sonarChecks.add(SelfAssignementCheck.class);
		sonarChecks.add(StringBufferAndBuilderWithCharCheck.class);
		sonarChecks.add(MethodNamedHashcodeOrEqualCheck.class);
		sonarChecks.add(ThreadRunCheck.class);
		sonarChecks.add(MethodNamedEqualsCheck.class);
		sonarChecks.add(DoubleBraceInitializationCheck.class);
		sonarChecks.add(VolatileNonPrimitiveFieldCheck.class);
		sonarChecks.add(ToArrayCheck.class);
		sonarChecks.add(AbsOnNegativeCheck.class);
		sonarChecks.add(IgnoredStreamReturnValueCheck.class);
		sonarChecks.add(IteratorNextExceptionCheck.class);
		sonarChecks.add(CompareToResultTestCheck.class);
		sonarChecks.add(CastArithmeticOperandCheck.class);
		sonarChecks.add(ShiftOnIntOrLongCheck.class);
		sonarChecks.add(CompareToReturnValueCheck.class);
		sonarChecks.add(ImmediateReverseBoxingCheck.class);
		sonarChecks.add(EqualsArgumentTypeCheck.class);
		sonarChecks.add(InnerClassOfNonSerializableCheck.class);
		sonarChecks.add(SerializableSuperConstructorCheck.class);
		sonarChecks.add(ParameterReassignedToCheck.class);
		sonarChecks.add(EqualsOverridenWithHashCodeCheck.class);
		sonarChecks.add(ObjectFinalizeOverridenCallsSuperFinalizeCheck.class);
		sonarChecks.add(SpringComponentWithWrongScopeCheck.class);
		sonarChecks.add(ConstructorInjectionCheck.class);
		sonarChecks.add(ClassWithoutHashCodeInHashStructureCheck.class);
		sonarChecks.add(InstanceOfAlwaysTrueCheck.class);
		sonarChecks.add(NullDereferenceInConditionalCheck.class);
		sonarChecks.add(FloatEqualityCheck.class);
		sonarChecks.add(IfConditionAlwaysTrueOrFalseCheck.class);
		sonarChecks.add(ObjectFinalizeCheck.class);
		sonarChecks.add(GetClassLoaderCheck.class);
		sonarChecks.add(MathOnFloatCheck.class);
		sonarChecks.add(SymmetricEqualsCheck.class);

		sonarChecks.add(ObjectOutputStreamCheck.class);
		sonarChecks.add(NoWayOutLoopCheck.class);
		sonarChecks.add(UnclosedResourcesCheck.class);
		sonarChecks.add(DivisionByZeroCheck.class);
		sonarChecks.add(LocksNotUnlockedCheck.class);
		sonarChecks.add(StreamConsumedCheck.class);
		sonarChecks.add(StreamNotConsumedCheck.class);
		sonarChecks.add(OptionalGetBeforeIsPresentCheck.class);
		sonarChecks.add(MinMaxRangeCheck.class);
		sonarChecks.add(ConditionalUnreachableCodeCheck.class);
		sonarChecks.add(NullDereferenceCheck.class);
		sonarChecks.add(NonNullSetToNullCheck.class);
		sonarChecks.add(CustomUnclosedResourcesCheck.class);

//        TEMP_SONAR_CHECKS.add(new DefaultMessageListenerContainerCheck());
//        TEMP_SONAR_CHECKS.add(new SingleConnectionFactoryCheck());
//        TEMP_SONAR_CHECKS.add(new DependencyWithSystemScopeCheck());
		SONAR_CHECK_CLASSES = Collections.unmodifiableList(sonarChecks);
	}

}
