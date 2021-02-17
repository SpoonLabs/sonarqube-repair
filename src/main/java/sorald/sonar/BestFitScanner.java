package sorald.sonar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import sorald.FileUtils;
import sorald.processor.SoraldAbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
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
     * Calculate a best fits mapping between Spoon elements and rule violations.
     *
     * <p>First it tries to find a Spoon element that intersects the rule violation's position. If
     * that fails, it searches all Spoon elements that start on the same line that the rule
     * violation starts on. Only elements that return true for {@link
     * SoraldAbstractProcessor#canRepairInternal(CtElement)} are considered as potential best fits.
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
                        .map(RuleViolation::getFileName)
                        .map(File::new)
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
                && !filesWithViolations.contains(element.getPosition().getFile());
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
        Stream<E> candidates =
                Stream.concat(intersectingCandidates.stream(), sameLineCandidates.stream());
        return candidates
                .sorted(
                        (lhs, rhs) -> {
                            if (lhs == rhs) {
                                return 0;
                            }

                            double lhsIntersect = intersectFraction(lhs, violation);
                            double rhsIntersect = intersectFraction(rhs, violation);

                            if (Math.abs(lhsIntersect - rhsIntersect)
                                    < INTERSECTION_FRACTION_TOLERANCE) {
                                return Integer.compare(elementSize(rhs), elementSize(lhs));
                            } else {
                                return Double.compare(rhsIntersect, lhsIntersect);
                            }
                        })
                .filter(this::canRepair)
                .filter(e -> !bestFitsMap.containsKey(e))
                .findFirst();
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
                && FileUtils.pathAbsNormEqual(
                        violation.getFileName(), element.getPosition().getFile().getAbsolutePath());
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