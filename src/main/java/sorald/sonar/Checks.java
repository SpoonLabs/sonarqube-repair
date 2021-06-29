package sorald.sonar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.sonar.check.Rule;
import org.sonar.java.checks.*;
import org.sonar.java.checks.naming.*;
import org.sonar.java.checks.regex.*;
import org.sonar.java.checks.security.*;
import org.sonar.java.checks.serialization.*;
import org.sonar.java.checks.spring.*;
import org.sonar.java.checks.synchronization.DoubleCheckedLockingCheck;
import org.sonar.java.checks.synchronization.SynchronizationOnGetClassCheck;
import org.sonar.java.checks.synchronization.TwoLocksWaitCheck;
import org.sonar.java.checks.synchronization.ValueBasedObjectUsedForLockCheck;
import org.sonar.java.checks.synchronization.WriteObjectTheOnlySynchronizedMethodCheck;
import org.sonar.java.checks.tests.*;
import org.sonar.java.checks.unused.*;
import org.sonar.java.se.checks.*;
import org.sonar.plugins.java.api.JavaCheck;
import org.sonar.plugins.java.api.JavaFileScanner;
import sorald.rule.RuleType;

/** Class for easily accessing Sonar check classes. */
@SuppressWarnings({"unchecked", "UnstableApiUsage"})
class Checks {
    private Checks() {}

    private static final Map<RuleType, Map<String, Class<? extends JavaFileScanner>>>
            TYPE_TO_CHECKS;

    private static final Map<Class<? extends JavaFileScanner>, RuleType> CHECK_TO_TYPE;

    /**
     * @param checkType A check type.
     * @return All checks of the given type.
     */
    static List<Class<? extends JavaFileScanner>> getChecksByType(RuleType checkType) {
        return new ArrayList<>(TYPE_TO_CHECKS.get(checkType).values());
    }

    /**
     * @param checkClass A Sonar check class
     * @return An instance of the passed Sonar check class
     */
    static JavaFileScanner instantiateCheck(Class<? extends JavaFileScanner> checkClass) {
        try {
            return checkClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate class " + checkClass.getName());
        }
    }

    /**
     * Get a specific check by key.
     *
     * @param key The key of the check.
     * @return The check class corresponding to the key.
     */
    static Class<? extends JavaFileScanner> getCheck(String key) {
        return TYPE_TO_CHECKS.values().stream()
                .map(checks -> checks.get(key))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no rule with key " + key));
    }

    /**
     * Get an instance of the check specified by key.
     *
     * @param key The key of the check.
     * @return An instance of the related check class.
     */
    static JavaFileScanner getCheckInstance(String key) {
        return instantiateCheck(getCheck(key));
    }

    /** @return All Sonar-Java checks that Sorald currently keeps track of. */
    static List<Class<? extends JavaFileScanner>> getAllChecks() {
        return TYPE_TO_CHECKS.values().stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    /**
     * @param checkClass A Sonar-Java check class.
     * @return the numeric identifier of the rule related to the given check class. Non-digits are
     *     stripped, so e.g. S1234 becomes 1234.
     */
    static String getRuleKey(Class<? extends JavaCheck> checkClass) {
        return Arrays.stream(checkClass.getAnnotationsByType(Rule.class))
                .map(Rule::key)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        checkClass.getName() + " does not have a key"));
    }

    /**
     * @param check A check.
     * @return The rule type associated with this check.
     */
    static RuleType getRuleType(Class<? extends JavaFileScanner> check) {
        return CHECK_TO_TYPE.get(check);
    }

    private static Map<String, Class<? extends JavaFileScanner>> createKeyToCheckMap(
            Class<? extends JavaFileScanner>... checksToAdd) {
        Map<String, Class<? extends JavaFileScanner>> keyToCheck = new HashMap<>();
        for (Class<? extends JavaFileScanner> check : checksToAdd) {
            keyToCheck.put(getRuleKey(check), check);
        }
        return Collections.unmodifiableMap(keyToCheck);
    }

    static {
        Map<RuleType, Map<String, Class<? extends JavaFileScanner>>> typeToChecks =
                new EnumMap<>(RuleType.class);

        typeToChecks.put(
                RuleType.BUG,
                createKeyToCheckMap(
                        AbsOnNegativeCheck.class,
                        AllBranchesAreIdenticalCheck.class,
                        AnchorPrecedenceCheck.class,
                        ArrayHashCodeAndToStringCheck.class,
                        AssertionCompareToSelfCheck.class,
                        AssertionInTryCatchCheck.class,
                        AssertionsInProductionCodeCheck.class,
                        AssertionTypesCheck.class,
                        AssertJApplyConfigurationCheck.class,
                        AssertJContextBeforeAssertionCheck.class,
                        AssertJTestForEmptinessCheck.class,
                        AssertOnBooleanVariableCheck.class,
                        BigDecimalDoubleConstructorCheck.class,
                        CastArithmeticOperandCheck.class,
                        ClassComparedByNameCheck.class,
                        ClassWithoutHashCodeInHashStructureCheck.class,
                        CollectionCallingItselfCheck.class,
                        CollectionInappropriateCallsCheck.class,
                        CollectionSizeAndArrayLengthCheck.class,
                        CompareStringsBoxedTypesWithEqualsCheck.class,
                        CompareToNotOverloadedCheck.class,
                        CompareToResultTestCheck.class,
                        CompareToReturnValueCheck.class,
                        ConditionalUnreachableCodeCheck.class,
                        ConfusingOverloadCheck.class,
                        ConstructorInjectionCheck.class,
                        ControllerWithSessionAttributesCheck.class,
                        CustomSerializationMethodCheck.class,
                        CustomUnclosedResourcesCheck.class,
                        DateFormatWeekYearCheck.class,
                        DivisionByZeroCheck.class,
                        DoubleBraceInitializationCheck.class,
                        DoubleCheckedLockingAssignmentCheck.class,
                        DoubleCheckedLockingCheck.class,
                        DoublePrefixOperatorCheck.class,
                        DuplicateConditionIfElseIfCheck.class,
                        EmptyStringRepetitionCheck.class,
                        EqualsArgumentTypeCheck.class,
                        EqualsOnAtomicClassCheck.class,
                        EqualsOverridenWithHashCodeCheck.class,
                        ExternalizableClassConstructorCheck.class,
                        FloatEqualityCheck.class,
                        ForLoopFalseConditionCheck.class,
                        ForLoopIncrementSignCheck.class,
                        GetClassLoaderCheck.class,
                        GettersSettersOnRightFieldCheck.class,
                        GraphemeClustersInClassesCheck.class,
                        HasNextCallingNextCheck.class,
                        IdenticalOperandOnBinaryExpressionCheck.class,
                        IfConditionAlwaysTrueOrFalseCheck.class,
                        IgnoredOperationStatusCheck.class,
                        IgnoredReturnValueCheck.class,
                        IgnoredStreamReturnValueCheck.class,
                        ImmediateReverseBoxingCheck.class,
                        ImpossibleRegexCheck.class,
                        InappropriateRegexpCheck.class,
                        InnerClassOfNonSerializableCheck.class,
                        InputStreamReadCheck.class,
                        InstanceOfAlwaysTrueCheck.class,
                        InterruptedExceptionCheck.class,
                        InvalidDateValuesCheck.class,
                        InvalidRegexCheck.class,
                        IterableIteratorCheck.class,
                        IteratorNextExceptionCheck.class,
                        JUnit5SilentlyIgnoreClassAndMethodCheck.class,
                        JUnitCompatibleAnnotationsCheck.class,
                        JunitNestedAnnotationCheck.class,
                        LocksNotUnlockedCheck.class,
                        LongBitsToDoubleOnIntCheck.class,
                        LoopExecutingAtMostOnceCheck.class,
                        MathOnFloatCheck.class,
                        MethodNamedEqualsCheck.class,
                        MethodNamedHashcodeOrEqualCheck.class,
                        MinMaxRangeCheck.class,
                        NonNullSetToNullCheck.class,
                        NonSerializableWriteCheck.class,
                        NotifyCheck.class,
                        NoWayOutLoopCheck.class,
                        NullDereferenceCheck.class,
                        NullDereferenceInConditionalCheck.class,
                        NullShouldNotBeUsedWithOptionalCheck.class,
                        ObjectFinalizeOverloadedCheck.class,
                        ObjectOutputStreamCheck.class,
                        OneExpectedCheckedExceptionCheck.class,
                        OptionalGetBeforeIsPresentCheck.class,
                        OverwrittenKeyCheck.class,
                        ParameterReassignedToCheck.class,
                        PreparedStatementAndResultSetCheck.class,
                        PrimitiveWrappersInTernaryOperatorCheck.class,
                        PrintfFailCheck.class,
                        RawByteBitwiseOperationsCheck.class,
                        ReflectionOnNonRuntimeAnnotationCheck.class,
                        ReturnInFinallyCheck.class,
                        ReuseRandomCheck.class,
                        RunFinalizersCheck.class,
                        ScheduledThreadPoolExecutorZeroCheck.class,
                        SelfAssignementCheck.class,
                        SerializableObjectInSessionCheck.class,
                        SerializableSuperConstructorCheck.class,
                        ServletInstanceFieldCheck.class,
                        ShiftOnIntOrLongCheck.class,
                        SillyEqualsCheck.class,
                        SillyStringOperationsCheck.class,
                        SpringComponentWithWrongScopeCheck.class,
                        SpringIncompatibleTransactionalCheck.class,
                        SpringScanDefaultPackageCheck.class,
                        StaticMultithreadedUnsafeFieldsCheck.class,
                        StreamConsumedCheck.class,
                        StreamNotConsumedCheck.class,
                        StringBufferAndBuilderWithCharCheck.class,
                        StringCallsBeyondBoundsCheck.class,
                        SymmetricEqualsCheck.class,
                        SyncGetterAndSetterCheck.class,
                        SynchronizationOnGetClassCheck.class,
                        SynchronizationOnStringOrBoxedCheck.class,
                        SynchronizedFieldAssignmentCheck.class,
                        SynchronizedOverrideCheck.class,
                        ThreadLocalCleanupCheck.class,
                        ThreadOverridesRunCheck.class,
                        ThreadRunCheck.class,
                        ThreadSleepCheck.class,
                        ThreadWaitCallCheck.class,
                        ToArrayCheck.class,
                        ToStringReturningNullCheck.class,
                        TransactionalMethodVisibilityCheck.class,
                        TwoLocksWaitCheck.class,
                        UnclosedResourcesCheck.class,
                        UnicodeCaseCheck.class,
                        UnusedReturnedDataCheck.class,
                        UnusedThrowableCheck.class,
                        UselessIncrementCheck.class,
                        ValueBasedObjectUsedForLockCheck.class,
                        VolatileNonPrimitiveFieldCheck.class,
                        VolatileVariablesOperationsCheck.class,
                        WaitInSynchronizeCheck.class,
                        WrongAssignmentOperatorCheck.class));

        typeToChecks.put(
                RuleType.VULNERABILITY,
                createKeyToCheckMap(
                        AESAlgorithmCheck.class,
                        AuthorizationsStrongDecisionsCheck.class,
                        AvoidDESCheck.class,
                        BasicAuthCheck.class,
                        CipherBlockChainingCheck.class,
                        CryptographicKeySizeCheck.class,
                        DeprecatedHashAlgorithmCheck.class,
                        DynamicClassLoadCheck.class,
                        EmptyDatabasePasswordCheck.class,
                        EncryptionAlgorithmCheck.class,
                        EnumMutableFieldCheck.class,
                        FileCreateTempFileCheck.class,
                        GetRequestedSessionIdCheck.class,
                        HostnameVerifierImplementationCheck.class,
                        HttpRefererCheck.class,
                        LDAPAuthenticatedConnectionCheck.class,
                        LDAPDeserializationCheck.class,
                        MainInServletCheck.class,
                        MutableMembersUsageCheck.class,
                        NullCipherCheck.class,
                        OpenSAML2AuthenticationBypassCheck.class,
                        PasswordEncoderCheck.class,
                        PersistentEntityUsedAsRequestParameterCheck.class,
                        PredictableSeedCheck.class,
                        PrintStackTraceCalledWithoutArgumentCheck.class,
                        RequestMappingMethodPublicCheck.class,
                        RSAUsesOAEPCheck.class,
                        SecureXmlTransformerCheck.class,
                        ServletMethodsExceptionsThrownCheck.class,
                        SMTPSSLServerIdentityCheck.class,
                        SpringAntMatcherOrderCheck.class,
                        SpringComponentWithNonAutowiredMembersCheck.class,
                        SpringSessionFixationCheck.class,
                        StrongCipherAlgorithmCheck.class,
                        VerifiedServerHostnamesCheck.class,
                        WeakSSLContextCheck.class,
                        XxeActiveMQCheck.class,
                        XxeProcessingCheck.class));

        typeToChecks.put(
                RuleType.SECURITY_HOTSPOT,
                createKeyToCheckMap(
                        AndroidBroadcastingCheck.class,
                        AndroidExternalStorageCheck.class,
                        AndroidSSLConnectionCheck.class,
                        CommandLineArgumentsCheck.class,
                        ControllingPermissionsCheck.class,
                        CookieDomainCheck.class,
                        CookieHttpOnlyCheck.class,
                        CookieShouldNotContainSensitiveDataCheck.class,
                        CORSCheck.class,
                        CustomCryptographicAlgorithmCheck.class,
                        DataEncryptionCheck.class,
                        DataHashingCheck.class,
                        DebugFeatureEnabledCheck.class,
                        EmailHotspotCheck.class,
                        EnvVariablesHotspotCheck.class,
                        FilePermissionsCheck.class,
                        HardCodedCredentialsCheck.class,
                        HardcodedIpCheck.class,
                        JacksonDeserializationCheck.class,
                        LogConfigurationCheck.class,
                        ObjectDeserializationCheck.class,
                        PopulateBeansCheck.class,
                        PseudoRandomCheck.class,
                        ReceivingIntentsCheck.class,
                        RedosCheck.class,
                        RegexHotspotCheck.class,
                        SecureCookieCheck.class,
                        SocketUsageCheck.class,
                        SpringRequestMappingMethodCheck.class,
                        SpringSecurityDisableCSRFCheck.class,
                        SQLInjectionCheck.class,
                        StandardInputReadCheck.class,
                        Struts1EndpointCheck.class,
                        Struts2EndpointCheck.class,
                        UserEnumerationCheck.class,
                        XmlDeserializationCheck.class,
                        ZipEntryCheck.class));

        typeToChecks.put(
                RuleType.CODE_SMELL,
                createKeyToCheckMap(
                        AbstractClassNoFieldShouldBeInterfaceCheck.class,
                        AbstractClassWithoutAbstractMethodCheck.class,
                        AccessibilityChangeCheck.class,
                        AnnotationDefaultArgumentCheck.class,
                        AnonymousClassesTooBigCheck.class,
                        AnonymousClassShouldBeLambdaCheck.class,
                        ArrayCopyLoopCheck.class,
                        ArrayDesignatorAfterTypeCheck.class,
                        ArrayDesignatorOnVariableCheck.class,
                        ArrayForVarArgCheck.class,
                        ArraysAsListOfPrimitiveToStreamCheck.class,
                        AssertionArgumentOrderCheck.class,
                        AssertionFailInCatchBlockCheck.class,
                        AssertionInThreadRunCheck.class,
                        AssertionsCompletenessCheck.class,
                        AssertionsInTestsCheck.class,
                        AssertionsWithoutMessageCheck.class,
                        AssertJChainSimplificationCheck.class,
                        AssertJConsecutiveAssertionCheck.class,
                        AssertsOnParametersOfPublicMethodCheck.class,
                        AssertThatThrownByAloneCheck.class,
                        AssertTrueInsteadOfDedicatedAssertCheck.class,
                        AtLeastOneConstructorCheck.class,
                        BadFieldNameStaticNonFinalCheck.class,
                        BadLocalConstantNameCheck.class,
                        BadTestClassNameCheck.class,
                        BadTestMethodNameCheck.class,
                        BlindSerialVersionUidCheck.class,
                        BooleanGratuitousExpressionsCheck.class,
                        BooleanInversionCheck.class,
                        BooleanLiteralCheck.class,
                        BooleanMethodNameCheck.class,
                        BooleanMethodReturnCheck.class,
                        BooleanOrNullLiteralInAssertionsCheck.class,
                        BoxedBooleanExpressionsCheck.class,
                        CallOuterPrivateMethodCheck.class,
                        CallSuperInTestCaseCheck.class,
                        CallSuperMethodFromInnerClassCheck.class,
                        CanonEqFlagInRegexCheck.class,
                        CaseInsensitiveComparisonCheck.class,
                        CatchExceptionCheck.class,
                        CatchIllegalMonitorStateExceptionCheck.class,
                        CatchNPECheck.class,
                        CatchOfThrowableOrErrorCheck.class,
                        CatchRethrowingCheck.class,
                        CatchUsesExceptionWithContextCheck.class,
                        ChangeMethodContractCheck.class,
                        ChildClassShadowFieldCheck.class,
                        ClassCouplingCheck.class,
                        ClassFieldCountCheck.class,
                        ClassNamedLikeExceptionCheck.class,
                        ClassWithOnlyStaticMethodsInstantiationCheck.class,
                        CloneableImplementingCloneCheck.class,
                        CloneMethodCallsSuperCloneCheck.class,
                        CloneOverrideCheck.class,
                        CognitiveComplexityMethodCheck.class,
                        CollapsibleIfCandidateCheck.class,
                        CollectInsteadOfForeachCheck.class,
                        CollectionImplementationReferencedCheck.class,
                        CollectionIsEmptyCheck.class,
                        CollectionMethodsWithLinearComplexityCheck.class,
                        CollectionsEmptyConstantsCheck.class,
                        CombineCatchCheck.class,
                        CommentRegularExpressionCheck.class,
                        CompareObjectWithEqualsCheck.class,
                        ConcatenationWithStringValueOfCheck.class,
                        ConditionalOnNewLineCheck.class,
                        ConfusingVarargCheck.class,
                        ConstantMathCheck.class,
                        ConstantMethodCheck.class,
                        ConstantsShouldBeStaticFinalCheck.class,
                        ConstructorCallingOverridableCheck.class,
                        ControlCharacterInLiteralCheck.class,
                        DanglingElseStatementsCheck.class,
                        DateAndTimesCheck.class,
                        DateUtilsTruncateCheck.class,
                        DeadStoreCheck.class,
                        DefaultEncodingUsageCheck.class,
                        DefaultInitializedFieldCheck.class,
                        DeprecatedTagPresenceCheck.class,
                        DiamondOperatorCheck.class,
                        DisallowedClassCheck.class,
                        DisallowedConstructorCheck.class,
                        DisallowedMethodCheck.class,
                        DisallowedThreadGroupCheck.class,
                        DuplicateArgumentCheck.class,
                        DuplicatesInCharacterClassCheck.class,
                        EmptyClassCheck.class,
                        EmptyLineRegexCheck.class,
                        EmptyMethodsCheck.class,
                        EnumEqualCheck.class,
                        EnumMapCheck.class,
                        EnumSetCheck.class,
                        EqualsNotOverriddenInSubclassCheck.class,
                        EqualsNotOverridenWithCompareToCheck.class,
                        EqualsParametersMarkedNonNullCheck.class,
                        ErrorClassExtendedCheck.class,
                        EscapedUnicodeCharactersCheck.class,
                        ExceptionsShouldBeImmutableCheck.class,
                        ExpectedExceptionCheck.class,
                        ExpressionComplexityCheck.class,
                        FieldModifierCheck.class,
                        FieldNameMatchingTypeNameCheck.class,
                        FilesExistsJDK8Check.class,
                        FinalClassCheck.class,
                        FinalizeFieldsSetCheck.class,
                        FixmeTagPresenceCheck.class,
                        ForLoopIncrementAndUpdateCheck.class,
                        ForLoopTerminationConditionCheck.class,
                        ForLoopUsedAsWhileLoopCheck.class,
                        ForLoopVariableTypeCheck.class,
                        GarbageCollectorCalledCheck.class,
                        HardcodedURICheck.class,
                        IdenticalCasesInSwitchCheck.class,
                        IfElseIfStatementEndsWithElseCheck.class,
                        IgnoredTestsCheck.class,
                        ImmediatelyReturnedVariableCheck.class,
                        ImplementsEnumerationCheck.class,
                        IncorrectOrderOfMembersCheck.class,
                        IncrementDecrementInSubExpressionCheck.class,
                        IndentationAfterConditionalCheck.class,
                        IndexOfStartPositionCheck.class,
                        IndexOfWithPositiveNumberCheck.class,
                        InnerClassOfSerializableCheck.class,
                        InnerClassTooManyLinesCheck.class,
                        InnerStaticClassesCheck.class,
                        InputStreamOverrideReadCheck.class,
                        InstanceofUsedOnExceptionCheck.class,
                        IntegerToHexStringCheck.class,
                        InterfaceAsConstantContainerCheck.class,
                        InterfaceOrSuperclassShadowingCheck.class,
                        InvariantReturnCheck.class,
                        JdbcDriverExplicitLoadingCheck.class,
                        JUnit45MethodAnnotationCheck.class,
                        JUnit4AnnotationsCheck.class,
                        JUnit5DefaultPackageClassAndMethodCheck.class,
                        JunitMethodDeclarationCheck.class,
                        KeySetInsteadOfEntrySetCheck.class,
                        KeywordAsIdentifierCheck.class,
                        LambdaOptionalParenthesisCheck.class,
                        LambdaSingleExpressionCheck.class,
                        LambdaTooBigCheck.class,
                        LambdaTypeParameterCheck.class,
                        LazyArgEvaluationCheck.class,
                        LeastSpecificTypeCheck.class,
                        LoggedRethrownExceptionsCheck.class,
                        LoggerClassCheck.class,
                        LoggersDeclarationCheck.class,
                        LoopsOnSameSetCheck.class,
                        MagicNumberCheck.class,
                        MainMethodThrowsExceptionCheck.class,
                        MapComputeIfAbsentOrPresentCheck.class,
                        MembersDifferOnlyByCapitalizationCheck.class,
                        MethodIdenticalImplementationsCheck.class,
                        MethodNameSameAsClassCheck.class,
                        MethodOnlyCallsSuperCheck.class,
                        MethodParametersOrderCheck.class,
                        MethodTooBigCheck.class,
                        MethodWithExcessiveReturnsCheck.class,
                        MismatchPackageDirectoryCheck.class,
                        MissingBeanValidationCheck.class,
                        MockingAllMethodsCheck.class,
                        ModulusEqualityCheck.class,
                        MultilineBlocksCurlyBracesCheck.class,
                        NestedBlocksCheck.class,
                        NestedEnumStaticCheck.class,
                        NestedIfStatementsCheck.class,
                        NestedSwitchStatementCheck.class,
                        NestedTernaryOperatorsCheck.class,
                        NestedTryCatchCheck.class,
                        NioFileDeleteCheck.class,
                        NoCheckstyleTagPresenceCheck.class,
                        NonShortCircuitLogicCheck.class,
                        NonStaticClassInitializerCheck.class,
                        NoPmdTagPresenceCheck.class,
                        NoTestInTestClassCheck.class,
                        NPEThrowCheck.class,
                        NullCheckWithInstanceofCheck.class,
                        ObjectCreatedOnlyToCallGetClassCheck.class,
                        ObjectFinalizeOverridenNotPublicCheck.class,
                        OctalValuesCheck.class,
                        OneClassInterfacePerFileCheck.class,
                        OneDeclarationPerLineCheck.class,
                        OneExpectedRuntimeExceptionCheck.class,
                        OperatorPrecedenceCheck.class,
                        OptionalAsParameterCheck.class,
                        OutputStreamOverrideWriteCheck.class,
                        OverrideAnnotationCheck.class,
                        PackageInfoCheck.class,
                        ParameterizedTestCheck.class,
                        ParameterNullnessCheck.class,
                        PreferStreamAnyMatchCheck.class,
                        PrimitivesMarkedNullableCheck.class,
                        PrimitiveTypeBoxingWithToStringCheck.class,
                        PrintfMisuseCheck.class,
                        PrivateFieldUsedLocallyCheck.class,
                        PrivateReadResolveCheck.class,
                        ProtectedMemberInFinalClassCheck.class,
                        PublicConstructorInAbstractClassCheck.class,
                        PublicStaticFieldShouldBeFinalCheck.class,
                        PublicStaticMutableMembersCheck.class,
                        RandomFloatToIntCheck.class,
                        RandomizedTestDataCheck.class,
                        RawTypeCheck.class,
                        ReadObjectSynchronizedCheck.class,
                        RedundantAbstractMethodCheck.class,
                        RedundantAssignmentsCheck.class,
                        RedundantCloseCheck.class,
                        RedundantJumpCheck.class,
                        RedundantModifierCheck.class,
                        RedundantStreamCollectCheck.class,
                        RedundantTypeCastCheck.class,
                        RegexComplexityCheck.class,
                        RegexPatternsNeedlesslyCheck.class,
                        ReluctantQuantifierCheck.class,
                        RepeatAnnotationCheck.class,
                        ReplaceGuavaWithJava8Check.class,
                        ReplaceLambdaByMethodRefCheck.class,
                        ResultSetIsLastCheck.class,
                        ReturnEmptyArrayNotNullCheck.class,
                        ReturnOfBooleanExpressionsCheck.class,
                        SAMAnnotatedCheck.class,
                        SelectorMethodArgumentCheck.class,
                        SerializableComparatorCheck.class,
                        SerializableFieldInSerializableClassCheck.class,
                        SerialVersionUidCheck.class,
                        SeveralBreakOrContinuePerLoopCheck.class,
                        SillyBitOperationCheck.class,
                        SimpleClassNameCheck.class,
                        SpecializedFunctionalInterfacesCheck.class,
                        SpringAssertionsSimplificationCheck.class,
                        SpringAutoConfigurationCheck.class,
                        SpringBeansShouldBeAccessibleCheck.class,
                        SpringComponentScanCheck.class,
                        SpringComposedRequestMappingCheck.class,
                        SpringConfigurationWithAutowiredFieldsCheck.class,
                        SpringConstructorInjectionCheck.class,
                        StandardCharsetsConstantsCheck.class,
                        StandardFunctionalInterfaceCheck.class,
                        StaticFieldInitializationCheck.class,
                        StaticFieldUpateCheck.class,
                        StaticFieldUpdateInConstructorCheck.class,
                        StaticImportCountCheck.class,
                        StaticMemberAccessCheck.class,
                        StaticMembersAccessCheck.class,
                        StaticMethodCheck.class,
                        StreamPeekCheck.class,
                        StringConcatenationInLoopCheck.class,
                        StringLiteralDuplicatedCheck.class,
                        StringLiteralInsideEqualsCheck.class,
                        StringMethodsOnSingleCharCheck.class,
                        StringMethodsWithLocaleCheck.class,
                        StringOffsetMethodsCheck.class,
                        StringPrimitiveConstructorCheck.class,
                        StringReplaceCheck.class,
                        StringToPrimitiveConversionCheck.class,
                        StringToStringCheck.class,
                        SubClassStaticReferenceCheck.class,
                        SunPackagesUsedCheck.class,
                        SuppressWarningsCheck.class,
                        SuspiciousListRemoveCheck.class,
                        SwitchAtLeastThreeCasesCheck.class,
                        SwitchCaseTooBigCheck.class,
                        SwitchCaseWithoutBreakCheck.class,
                        SwitchDefaultLastCaseCheck.class,
                        SwitchInsteadOfIfSequenceCheck.class,
                        SwitchWithLabelsCheck.class,
                        SwitchWithTooManyCasesCheck.class,
                        SynchronizedClassUsageCheck.class,
                        SynchronizedLockCheck.class,
                        SystemExitCalledCheck.class,
                        SystemOutOrErrUsageCheck.class,
                        TernaryOperatorCheck.class,
                        TestAnnotationWithExpectedExceptionCheck.class,
                        TestsInSeparateFolderCheck.class,
                        TestsStabilityCheck.class,
                        ThisExposedFromConstructorCheck.class,
                        ThreadAsRunnableArgumentCheck.class,
                        ThreadLocalWithInitialCheck.class,
                        ThreadSleepInTestsCheck.class,
                        ThreadStartedInConstructorCheck.class,
                        ThrowCheckedExceptionCheck.class,
                        ThrowsFromFinallyCheck.class,
                        ThrowsSeveralCheckedExceptionCheck.class,
                        TodoTagPresenceCheck.class,
                        TooManyAssertionsCheck.class,
                        TooManyMethodsCheck.class,
                        ToStringUsingBoxingCheck.class,
                        TransientFieldInNonSerializableCheck.class,
                        TryWithResourcesCheck.class,
                        UnderscoreMisplacedOnNumberCheck.class,
                        UnderscoreOnNumberCheck.class,
                        UnicodeAwareCharClassesCheck.class,
                        UnnecessarySemicolonCheck.class,
                        UnreachableCatchCheck.class,
                        UnusedGroupNamesCheck.class,
                        UnusedLabelCheck.class,
                        UnusedLocalVariableCheck.class,
                        UnusedMethodParameterCheck.class,
                        UnusedPrivateFieldCheck.class,
                        UnusedTestRuleCheck.class,
                        UnusedTypeParameterCheck.class,
                        UppercaseSuffixesCheck.class,
                        URLHashCodeAndEqualsCheck.class,
                        UselessExtendsCheck.class,
                        UselessPackageInfoCheck.class,
                        UseSwitchExpressionCheck.class,
                        UtilityClassWithPublicConstructorCheck.class,
                        ValueBasedObjectsShouldNotBeSerializedCheck.class,
                        VarArgCheck.class,
                        VariableDeclarationScopeCheck.class,
                        VisibleForTestingUsageCheck.class,
                        WaitInWhileLoopCheck.class,
                        WaitOnConditionCheck.class,
                        WildcardImportsShouldNotBeUsedCheck.class,
                        WildcardReturnParameterTypeCheck.class,
                        WriteObjectTheOnlySynchronizedMethodCheck.class));

        TYPE_TO_CHECKS = Collections.unmodifiableMap(typeToChecks);

        // sanity check: all CheckType values should be accounted for
        for (RuleType type : RuleType.values()) {
            assert TYPE_TO_CHECKS.containsKey(type);
        }

        Map<Class<? extends JavaFileScanner>, RuleType> checkToType = new IdentityHashMap<>();
        for (var ruleType : RuleType.values()) {
            for (var check : TYPE_TO_CHECKS.get(ruleType).values()) {
                checkToType.put(check, ruleType);
            }
        }
        CHECK_TO_TYPE = Collections.unmodifiableMap(checkToType);
    }
}
