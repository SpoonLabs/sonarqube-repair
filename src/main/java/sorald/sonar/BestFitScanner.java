package sorald.sonar;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import sorald.FileUtils;
import sorald.processor.SoraldAbstractProcessor;
import sorald.rule.RuleViolation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.CtScanner;

/** Scanner for matching rule violations against Spoon elements. */
public class BestFitScanner<E extends CtElement> extends CtScanner {
    private final List<RuleViolation> violations;
    private final SoraldAbstractProcessor<E> processor;

    private final Map<RuleViolation, List<E>> onSameLine;
    private final Map<RuleViolation, List<E>> intersecting;

    private final Set<File> filesWithViolations;

    public static final double INTERSECTION_FRACTION_TOLERANCE = 0.005;

    /**
     * Calculate a best fits mapping between Spoon elements and rule violations. Intuitively, a best
     * fit for a violation v is the Spoon element e that is most intersected by the violation.
     *
     * <p>We judge "most intersected" by considering the fraction of e that is intersected by v. If
     * half of e's source position is intersected by the source position of v, then that fraction is
     * 0.5. If there are two elements e1 and e2 that both have the same intersection fraction (this
     * is common for nestable elements such as expressions), then the largest of the two is
     * considered the better fit, as the absolute intersection is larger.
     *
     * <p>The matching is 1:1, but there is no guarantee that all violations appear in the value
     * set.
     *
     * @param element The root element to scan. This is typically the unnamed module.
     * @param violations The rule violations to find matching elements for. Must be violations of a
     *     single rule.
     * @param processor The processor for which to calculate best matches. Must be the processor for
     *     the single rule that the violations violate.
     * @param <E> The type of Spoon element considered by the given processor.
     * @return A mapping from Spoon element to an associated rule violation.
     */
    public static <E extends CtElement> Map<CtElement, RuleViolation> calculateBestFits(
            CtElement element,
            Set<RuleViolation> violations,
            SoraldAbstractProcessor<E> processor) {
        checkRuleViolationsConcernProcessorRule(violations, processor);

        var scanner = new BestFitScanner<>(violations, processor);
        scanner.scan(element);

        Map<CtElement, RuleViolation> bestFitsMap = new IdentityHashMap<>();
        for (var violation : violations) {
            scanner.getBestFit(violation, bestFitsMap)
                    .ifPresent(e -> bestFitsMap.put(e, violation));
        }
        return bestFitsMap;
    }

    private BestFitScanner(Set<RuleViolation> violations, SoraldAbstractProcessor<E> processor) {
        var tmpViolations = new ArrayList<>(violations);
        Collections.sort(tmpViolations);
        this.violations = Collections.unmodifiableList(tmpViolations);
        this.processor = processor;
        onSameLine = new HashMap<>();
        intersecting = new HashMap<>();
        filesWithViolations =
                violations.stream()
                        .map(RuleViolation::getAbsolutePath)
                        .map(Path::toFile)
                        .collect(Collectors.toSet());
    }

    @Override
    public void scan(CtElement element) {
        if (element != null && !isTypeInFileWithoutViolations(element)) {
            element.accept(this);
        }
    }

    private boolean isTypeInFileWithoutViolations(CtElement element) {
        return element instanceof CtType
                && element.getPosition().isValidPosition()
                && filesWithViolations.stream()
                        .noneMatch(
                                fileWithViolation ->
                                        FileUtils.realPathEquals(
                                                fileWithViolation.toPath(),
                                                element.getPosition().getFile().toPath()));
    }

    @Override
    protected void enter(CtElement e) {
        if (processor.getTargetType().isAssignableFrom(e.getClass())) {
            E candidate = processor.getTargetType().cast(e);
            for (RuleViolation violation : violations) {
                if (!inSameFile(e, violation)) {
                    continue;
                }

                if (startOnSameLine(candidate, violation)) {
                    var elementsOnSameLine = onSameLine.getOrDefault(violation, new ArrayList<>());
                    elementsOnSameLine.add(candidate);
                    onSameLine.putIfAbsent(violation, elementsOnSameLine);
                }

                if (elementIntersectsViolation(candidate, violation)) {
                    var intersectingElements =
                            intersecting.getOrDefault(violation, new ArrayList<>());
                    intersectingElements.add(candidate);
                    intersecting.putIfAbsent(violation, intersectingElements);
                }
            }
        }
    }

    /**
     * Get the best fit Spoon element for the given violation s.t. the element does not already
     * appear in the best fits map. Intersections are prioritized over same-line elements.
     */
    private Optional<E> getBestFit(
            RuleViolation violation, Map<CtElement, RuleViolation> bestFitsMap) {
        List<E> intersectingCandidates =
                intersecting.getOrDefault(violation, Collections.emptyList());
        List<E> sameLineCandidates = onSameLine.getOrDefault(violation, Collections.emptyList());

        Comparator<E> reversedComparePositionFit =
                (lhs, rhs) -> -comparePositionFit(lhs, rhs, violation);
        Stream<E> reverseSortedUnusedCandidates =
                Stream.concat(intersectingCandidates.stream(), sameLineCandidates.stream())
                        .sorted(reversedComparePositionFit)
                        .filter(e -> !bestFitsMap.containsKey(e))
                        .filter(e -> candidatePostFilter(e, violation));

        if (processor.isIncomplete()) {
            // if the processor is incomplete, we only consider the best position match, and a false
            // from canRepair is considered final
            return reverseSortedUnusedCandidates.findFirst().filter(this::canRepair);
        } else {
            // if the processor is not incomplete, canRepair is allowed to steer the search for a
            // suitable candidate
            return reverseSortedUnusedCandidates.filter(this::canRepair).findFirst();
        }
    }

    private boolean canRepair(E element) {
        // The processor doesn't have a factory set at this point, so we TEMPORARILY set the
        // element's factory
        final Factory originalFactory = processor.getFactory();
        try {
            processor.setFactory(element.getFactory());
            return processor.canRepair(element);
        } finally {
            processor.setFactory(originalFactory);
        }
    }

    /**
     * Post filter for position-matched candidates for elements that require special handling, such
     * as jointly declared variables (i.e. multiple declarations in one statement).
     */
    private boolean candidatePostFilter(E element, RuleViolation violation) {
        if (element instanceof CtVariable && ((CtVariable<?>) element).isPartOfJointDeclaration()) {
            String ident =
                    getPrecedingIdentifier(violation, element.getPosition().getCompilationUnit());
            return ident.equals(((CtVariable<?>) element).getSimpleName());
        } else {
            return true;
        }
    }

    /**
     * Get the first identifier-like symbol preceding the source position of the given rule
     * violation.
     */
    private static String getPrecedingIdentifier(RuleViolation violation, CtCompilationUnit cu) {
        String cuSource = cu.getOriginalSourceCode();
        int searchStartPos =
                calculateSourcePos(
                        violation.getStartLine(),
                        violation.getStartCol(),
                        cu.getLineSeparatorPositions());
        int identEndPos = reverseFind(cuSource, searchStartPos, Character::isJavaIdentifierPart);
        int identStartPos =
                reverseFind(cuSource, identEndPos, c -> !Character.isJavaIdentifierPart(c)) + 1;
        return cuSource.substring(identStartPos, identEndPos + 1);
    }

    private static int reverseFind(String s, int startIdx, Predicate<Character> predicate) {
        int searchPos = startIdx;
        while (searchPos > 0 && !predicate.test(s.charAt(searchPos))) {
            --searchPos;
        }
        return searchPos;
    }

    private static boolean elementIntersectsViolation(CtElement element, RuleViolation violation) {
        int[] lineSeps = element.getPosition().getCompilationUnit().getLineSeparatorPositions();
        int violationSourceStart =
                calculateSourcePos(violation.getStartLine(), violation.getStartCol(), lineSeps);
        int violationSourceEnd =
                calculateSourcePos(violation.getEndLine(), violation.getEndCol(), lineSeps);

        int elemSourceStart = element.getPosition().getSourceStart();
        int elemSourceEnd = element.getPosition().getSourceEnd();

        return pointsIntersect(
                violationSourceStart, violationSourceEnd, elemSourceStart, elemSourceEnd);
    }

    private static int calculateSourcePos(int line, int column, int[] lineSeps) {
        return (line == 1 ? 0 : lineSeps[line - 2]) + column;
    }

    private static boolean pointsIntersect(int startLhs, int endLhs, int startRhs, int endRhs) {
        return startRhs <= endLhs && endRhs >= startLhs;
    }

    /**
     * Compare the intersection fraction (as defined by {@link
     * BestFitScanner#intersectFraction(CtElement, RuleViolation)}) of the elements with the
     * violation.
     *
     * <p>If the intersection fractions are equal down to {@link
     * BestFitScanner#INTERSECTION_FRACTION_TOLERANCE}, we compare the absolute intersection
     * instead.
     *
     * @param lhs The left-hand element in the comparison.
     * @param rhs The right-hand element in the comparison.
     * @param violation The violation to compute intersection fractions with.
     * @return A negative value if lhs is a worse position fit than rhs, 0 if they are equally good,
     *     and a positive value if rhs is a better position fit than lhs.
     */
    private int comparePositionFit(E lhs, E rhs, RuleViolation violation) {
        if (lhs == rhs) {
            return 0;
        }

        double lhsIntersect = intersectFraction(lhs, violation);
        double rhsIntersect = intersectFraction(rhs, violation);

        if (Math.abs(lhsIntersect - rhsIntersect) < INTERSECTION_FRACTION_TOLERANCE) {
            return Integer.compare(elementSize(lhs), elementSize(rhs));
        } else {
            return Double.compare(lhsIntersect, rhsIntersect);
        }
    }

    /**
     * @param element An element.
     * @param violation A rule violation.
     * @return The fraction of the element's source position that is intersected by the violation's
     *     source position.
     */
    private static double intersectFraction(CtElement element, RuleViolation violation) {
        int[] lineSeps = element.getPosition().getCompilationUnit().getLineSeparatorPositions();
        int violationSourceStart =
                calculateSourcePos(violation.getStartLine(), violation.getStartCol(), lineSeps);
        int violationSourceEnd =
                calculateSourcePos(violation.getEndLine(), violation.getEndCol(), lineSeps);

        int elemSourceStart = element.getPosition().getSourceStart();
        int elemSourceEnd = element.getPosition().getSourceEnd();

        if (!pointsIntersect(
                elemSourceStart, elemSourceEnd, violationSourceStart, violationSourceEnd)) {
            return 0;
        } else {
            int elemSize = elemSourceEnd - elemSourceStart;
            int adjustedViolationStart = Math.max(0, violationSourceStart - elemSourceStart);
            int adjustedViolationEnd =
                    Math.max(0, Math.min(violationSourceEnd - elemSourceStart, elemSize));

            int violationSizeInsideElement = adjustedViolationEnd - adjustedViolationStart;
            return (double) violationSizeInsideElement / elemSize;
        }
    }

    private static int elementSize(CtElement element) {
        return element.getPosition().getSourceEnd() - element.getPosition().getSourceStart();
    }

    private static boolean startOnSameLine(CtElement element, RuleViolation violation) {
        return element.getPosition().getLine() == violation.getStartLine();
    }

    private static boolean inSameFile(CtElement element, RuleViolation violation) {
        return element.getPosition().isValidPosition()
                && FileUtils.realPathEquals(
                        violation.getAbsolutePath(), element.getPosition().getFile().toPath());
    }

    /** All rule violations must concern the same rule as the processor. */
    private static void checkRuleViolationsConcernProcessorRule(
            Set<RuleViolation> ruleViolations, SoraldAbstractProcessor<?> processor) {
        String procKey = processor.getRuleKey();
        ruleViolations.stream()
                .map(RuleViolation::getRuleKey)
                .filter(vKey -> !procKey.equals(vKey))
                .findFirst()
                .ifPresent(
                        vKey -> {
                            throw new IllegalArgumentException(
                                    String.format(
                                            "rule key mismatch, processor for rule %s but violation for %s",
                                            procKey, vKey));
                        });
    }
}
