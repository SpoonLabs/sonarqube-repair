package sorald.processor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import sorald.annotations.IncompleteProcessor;
import sorald.annotations.ProcessorAnnotation;
import sorald.event.EventHelper;
import sorald.event.SoraldEventHandler;
import sorald.event.models.CrashEvent;
import sorald.event.models.RepairEvent;
import sorald.rule.RuleViolation;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;

/** superclass for all processors */
public abstract class SoraldAbstractProcessor<E extends CtElement> extends AbstractProcessor<E> {
    private int maxFixes = Integer.MAX_VALUE;
    private List<SoraldEventHandler> eventHandlers;
    private final List<RuleViolation> processedViolations;

    private Map<CtElement, RuleViolation> bestFits;

    @SuppressWarnings("unchecked")
    public SoraldAbstractProcessor() {
        super();
        // we must override the processed element types as they depend on the concrete type of
        // the process method, which with type erasure will always be CtElement for
        // SoraldAbstractProcessor::process
        clearProcessedElementType();
        Arrays.stream(getClass().getDeclaredMethods())
                .filter(
                        meth ->
                                meth.getName().equals("repairInternal")
                                        && meth.getParameterCount() == 1)
                .map(Method::getParameterTypes)
                .flatMap(Arrays::stream)
                .filter(CtElement.class::isAssignableFrom)
                .filter(cls -> !cls.equals(CtElement.class))
                .map(paramType -> (Class<CtElement>) paramType)
                .forEach(this::addProcessedElementType);

        // This might become false if we ever add a processor for CtElement. Which we probably
        // should not, it seems to always make sense to target a more specific type.
        assert !getProcessedElementTypes().isEmpty();

        processedViolations = new ArrayList<>();
    }

    /**
     * Pass a candidate element that appears in the vicinity of a violation for the processor to
     * inspect. In general, this method should only return false for incomplete processors, but it
     * is possible that certain complete processors also need to return false to avoid repairing an
     * incorrect element.
     *
     * <p><b>This method does not mutate the state of the processor</b>.
     *
     * <p>This method never crashes, instead returning false if there is a problem in the concrete
     * processor.
     *
     * @param candidate A candidate element to inspect.
     * @return true if the processor can repair the violation based on this element.
     */
    public final boolean canRepair(E candidate) {
        try {
            return canRepairInternal(candidate);
        } catch (Exception e) {
            fireCrashEvent("canRepairInternal", e);
            return false;
        }
    }

    /**
     * Repair a violating element after having been accepted by {@link
     * SoraldAbstractProcessor#canRepair(CtElement)}.
     *
     * <p>This method never crashes.
     *
     * @param element An element to repair.
     * @return true if the repair proceeded without crashing, false if errors were encountered.
     */
    public final boolean repair(E element) {
        try {
            repairInternal(element);
            return true;
        } catch (Exception e) {
            fireCrashEvent("repairInternal", e);
            return false;
        }
    }

    /**
     * Same as the general description of {@link SoraldAbstractProcessor#canRepair(CtElement)}.
     *
     * <p>Most complete processors (i.e. processors that fully handle a rule) should not override
     * this method. The position matching performed between rule violations and Spoon elements
     * before this method is invoked makes it such that, for most rules, the first element passed to
     * this method is always the violating element. If this is found not to be the case for some
     * rule, then the first step is to attempt to improve the position matching in {@link
     * sorald.sonar.BestFitScanner}. Only as a last resort should this method be overridden to steer
     * the search.
     *
     * <p>Incomplete processors must however override this method in order to decline repairing
     * certain elements. See {@link ToStringReturningNullProcessor} for a good example. Only the
     * best position match is passed to an incomplete processors implementation of this method, and
     * if it returns false, no further matching is performed for that rule violation.
     *
     * <p>It is very important that this method <b>does not mutate the state of the processor.</b>
     * Doing so may have unexpected side effects.
     *
     * @param candidate A candidate element.
     * @return true if the processor can repair the violation based on this element.
     */
    protected boolean canRepairInternal(E candidate) {
        return true;
    }

    /**
     * Repair a violating element. An element is only passed to this method after having been
     * accepted by {@link SoraldAbstractProcessor#canRepairInternal(CtElement)}.
     *
     * @param element An element to repair.
     */
    protected abstract void repairInternal(E element);

    /** @return Whether or not this processor is incomplete. */
    public boolean isIncomplete() {
        return getClass().getAnnotation(IncompleteProcessor.class) != null;
    }

    public SoraldAbstractProcessor<E> setBestFits(Map<CtElement, RuleViolation> bestFits) {
        this.bestFits = Collections.unmodifiableMap(bestFits);
        return this;
    }

    public Map<CtElement, RuleViolation> getBestFits() {
        return bestFits;
    }

    public SoraldAbstractProcessor setMaxFixes(int maxFixes) {
        this.maxFixes = maxFixes;
        return this;
    }

    public SoraldAbstractProcessor<E> setEventHandlers(List<SoraldEventHandler> eventHandlers) {
        this.eventHandlers = eventHandlers;
        return this;
    }

    public int getNbFixes() {
        return processedViolations.size();
    }

    @Override
    public final void process(E element) {
        CtElement elementClone = element.clone();
        elementClone.setParent(element.getParent());
        try {
            assert !processedViolations.contains(bestFits.get(element));

            repair(element);

            EventHelper.fireEvent(
                    new RepairEvent(bestFits.get(element), elementClone, false), eventHandlers);

            processedViolations.add(bestFits.get(element));
        } catch (Exception e) {
            fireCrashEvent("process", e);

            if (bestFits != null && bestFits.containsKey(element)) {
                EventHelper.fireEvent(
                        new RepairEvent(bestFits.get(element), elementClone, true), eventHandlers);
            }
        }
    }

    @Override
    public final boolean isToBeProcessed(E element) {
        return getNbFixes() < maxFixes && bestFits.containsKey(element);
    }

    /** @return The numerical identifier of the rule this processor is related to */
    public String getRuleKey() {
        return Arrays.stream(getClass().getAnnotationsByType(ProcessorAnnotation.class))
                .map(ProcessorAnnotation::key)
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        getClass().getName() + " does not have a key"))
                .toString();
    }

    /** @return The concrete type that this processor accepts. */
    @SuppressWarnings("unchecked")
    public Class<E> getTargetType() {
        assert getProcessedElementTypes().size() == 1;
        return (Class<E>) getProcessedElementTypes().iterator().next();
    }

    private void fireCrashEvent(String methodName, Exception e) {
        EventHelper.fireEvent(
                new CrashEvent("Crash in " + getClass().getCanonicalName() + "::" + methodName, e),
                eventHandlers);
    }
}
