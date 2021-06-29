package sorald;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import sorald.event.EventHelper;
import sorald.event.EventType;
import sorald.event.SoraldEventHandler;
import sorald.event.collectors.CompilationUnitCollector;
import sorald.event.models.CrashEvent;
import sorald.processor.SoraldAbstractProcessor;
import sorald.rule.RuleViolation;
import sorald.segment.FirstFitSegmentationAlgorithm;
import sorald.segment.Node;
import sorald.segment.SoraldTreeBuilderAlgorithm;
import sorald.sonar.BestFitScanner;
import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.compiler.Environment;
import spoon.processing.ProcessingManager;
import spoon.processing.Processor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultImportComparator;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.ImportCleaner;
import spoon.reflect.visitor.ImportConflictDetector;
import spoon.reflect.visitor.PrettyPrinter;
import spoon.support.QueueProcessingManager;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/** Class for repairing projects. */
public class Repair {
    private final SoraldConfig config;

    final List<SoraldEventHandler> eventHandlers;
    private final CompilationUnitCollector cuCollector;
    private final List<String> classpath;

    public Repair(
            SoraldConfig config,
            List<String> classpath,
            List<? extends SoraldEventHandler> eventHandlers) {
        this.config = config;
        cuCollector = new CompilationUnitCollector();
        List<SoraldEventHandler> eventHandlersCopy = new ArrayList<>(eventHandlers);
        eventHandlersCopy.add(cuCollector);
        this.eventHandlers = Collections.unmodifiableList(eventHandlersCopy);
        this.classpath = new ArrayList<>(classpath);
    }

    /**
     * Execute a repair according to the config.
     *
     * @param ruleViolations Rule violations to repair. May not be empty, and must relate to a
     *     single rule.
     * @return The processor used in the repairs.
     * @throws IllegalArgumentException if the supplied rule violations are empty, or relate to
     *     multiple rules.
     */
    public SoraldAbstractProcessor<?> repair(Set<RuleViolation> ruleViolations) {
        List<String> distinctRuleKeys =
                ruleViolations.stream()
                        .map(RuleViolation::getRuleKey)
                        .distinct()
                        .collect(Collectors.toList());
        if (distinctRuleKeys.size() != 1) {
            throw new IllegalArgumentException(
                    "expected rule violations for precisely 1 rule key, got: " + distinctRuleKeys);
        }

        String ruleKey = distinctRuleKeys.get(0);
        Path inputDir = Path.of(config.getSource());

        SoraldAbstractProcessor<?> processor = createProcessor(ruleKey);
        Stream<CtModel> models = repair(inputDir, processor, ruleViolations);

        models.forEach(
                model -> {
                    cuCollector
                            .getCollectedCompilationUnits()
                            .forEach(this::overwriteCompilationUnit);
                    cuCollector.clear();
                });

        return processor;
    }

    Stream<CtModel> repair(
            Path inputDir, SoraldAbstractProcessor<?> processor, Set<RuleViolation> violations) {
        switch (config.getRepairStrategy()) {
            case DEFAULT:
                return Stream.of(defaultRepair(inputDir, processor, violations));
            case MAVEN:
                return Stream.of(mavenRepair(inputDir, processor, violations));
            case SEGMENT:
                return segmentRepair(
                        inputDir,
                        processor,
                        violations,
                        segment -> createSegmentLauncher(segment).getModel());
            default:
                throw new IllegalStateException(
                        "unknown repair strategy: " + config.getRepairStrategy());
        }
    }

    CtModel defaultRepair(
            Path inputDir, SoraldAbstractProcessor<?> processor, Set<RuleViolation> violations) {
        EventHelper.fireEvent(EventType.PARSE_START, eventHandlers);
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.toString());
        CtModel model = initLauncher(launcher).getModel();
        EventHelper.fireEvent(EventType.PARSE_END, eventHandlers);

        repairModelWithInitializedProcessor(model, processor, violations);
        return model;
    }

    CtModel mavenRepair(
            Path inputDir, SoraldAbstractProcessor<?> processor, Set<RuleViolation> violations) {
        EventHelper.fireEvent(EventType.PARSE_START, eventHandlers);
        MavenLauncher launcher =
                new MavenLauncher(inputDir.toString(), MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
        CtModel model = initLauncher(launcher).getModel();
        EventHelper.fireEvent(EventType.PARSE_END, eventHandlers);

        repairModelWithInitializedProcessor(model, processor, violations);
        return model;
    }

    Stream<CtModel> segmentRepair(
            Path inputDir,
            SoraldAbstractProcessor<?> processor,
            Set<RuleViolation> violations,
            Function<LinkedList<Node>, CtModel> parseSegment) {
        Node rootNode = SoraldTreeBuilderAlgorithm.buildTree(inputDir.toString());
        LinkedList<LinkedList<Node>> segments =
                FirstFitSegmentationAlgorithm.segment(rootNode, config.getMaxFilesPerSegment());

        return segments.stream()
                .map(
                        segment -> {
                            try {
                                EventHelper.fireEvent(EventType.PARSE_START, eventHandlers);
                                CtModel model = parseSegment.apply(segment);
                                EventHelper.fireEvent(EventType.PARSE_END, eventHandlers);

                                repairModelWithInitializedProcessor(model, processor, violations);
                                return model;
                            } catch (Exception e) {
                                reportSegmentCrash(segment, e);
                                e.printStackTrace();
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .takeWhile(model -> processor.getNbFixes() < config.getMaxFixesPerRule());
    }

    private void reportSegmentCrash(LinkedList<Node> segment, Exception e) {
        List<String> paths =
                segment.stream()
                        .map(
                                node ->
                                        node.isDirNode()
                                                ? node.getRootPath()
                                                : node.getJavaFiles().toString())
                        .collect(Collectors.toList());
        EventHelper.fireEvent(new CrashEvent("Crash in segment: " + paths, e), eventHandlers);
    }

    private void repairModelWithInitializedProcessor(
            CtModel model, SoraldAbstractProcessor<?> processor, Set<RuleViolation> violations) {
        EventHelper.fireEvent(EventType.REPAIR_START, eventHandlers);
        var bestFits = new IdentityHashMap<CtElement, RuleViolation>();
        model.getAllModules().stream()
                .map(module -> BestFitScanner.calculateBestFits(module, violations, processor))
                .flatMap(m -> m.entrySet().stream())
                .forEach(entry -> bestFits.put(entry.getKey(), entry.getValue()));
        processor.setBestFits(bestFits);

        Factory factory = model.getUnnamedModule().getFactory();
        ProcessingManager processingManager = new QueueProcessingManager(factory);
        processingManager.addProcessor(processor);
        processingManager.process(factory.Class().getAll());
        EventHelper.fireEvent(EventType.REPAIR_END, eventHandlers);
    }

    Launcher createSegmentLauncher(List<Node> segment) {
        Launcher launcher = new Launcher();

        for (Node node : segment) {
            if (node.isDirNode()) {
                launcher.addInputResource(node.getRootPath());
            } else {
                for (String file : node.getJavaFiles()) {
                    launcher.addInputResource(file);
                }
            }
        }
        return initLauncher(launcher);
    }

    private void overwriteCompilationUnit(CtCompilationUnit cu) {
        List<CtType<?>> typesToPrint =
                cu.getDeclaredTypes().stream()
                        .filter(CtType::isTopLevel)
                        .collect(Collectors.toList());
        Path sourcePath = cu.getPosition().getFile().toPath();

        String output =
                cu.getFactory()
                        .getEnvironment()
                        .createPrettyPrinter()
                        .printTypes(typesToPrint.toArray(CtType[]::new));

        // we overwrite the source
        writeToFile(sourcePath, output);
    }

    private static void writeToFile(Path filepath, String output) {
        File dir = filepath.getParent().toFile();
        if (!(dir.isDirectory() || dir.mkdirs())) {
            throw new IllegalStateException("could not create directory '" + dir + "'");
        }

        try {
            Files.writeString(filepath, output);
        } catch (IOException e) {
            // must convert to a runtime exception as this is used in writeModel, which in turn
            // is used in a stream (that can't have checked exceptions)
            throw new RuntimeException(e);
        }
    }

    private Launcher initLauncher(Launcher launcher) {
        Environment env = launcher.getEnvironment();
        env.setIgnoreDuplicateDeclarations(true);
        env.setComplianceLevel(Constants.DEFAULT_COMPLIANCE_LEVEL);

        if (!classpath.isEmpty()) {
            env.setSourceClasspath(classpath.toArray(String[]::new));
        }

        // this is a workaround for https://github.com/INRIA/spoon/issues/3693
        if (config.getPrettyPrintingStrategy() == PrettyPrintingStrategy.SNIPER) {
            env.setPrettyPrinterCreator(() -> new SniperJavaPrettyPrinter(env));
        }

        // need to build the model before setting the pretty-printer as the preprocessors need
        // data from the model
        CtModel model = launcher.buildModel();

        setPrettyPrinter(env, model);
        return launcher;
    }

    private void setPrettyPrinter(Environment env, CtModel model) {
        Supplier<? extends DefaultJavaPrettyPrinter> basePrinterCreator =
                config.getPrettyPrintingStrategy() == PrettyPrintingStrategy.SNIPER
                        ? createSniperPrinter(env)
                        : createDefaultPrinter(env);
        Supplier<PrettyPrinter> configuredPrinterCreator =
                applyCommonPrinterOptions(basePrinterCreator, model);
        env.setPrettyPrinterCreator(configuredPrinterCreator);
    }

    private static Supplier<PrettyPrinter> applyCommonPrinterOptions(
            Supplier<? extends DefaultJavaPrettyPrinter> prettyPrinterCreator, CtModel model) {
        Collection<CtTypeReference<?>> existingReferences = model.getElements(e -> true);
        List<Processor<CtElement>> preprocessors =
                List.of(
                        new SelectiveForceImport(existingReferences),
                        new ImportConflictDetector(),
                        new ImportCleaner().setImportComparator(new DefaultImportComparator()));
        return () -> {
            DefaultJavaPrettyPrinter printer = prettyPrinterCreator.get();
            printer.setIgnoreImplicit(false);
            printer.setPreprocessors(preprocessors);
            return printer;
        };
    }

    private static Supplier<? extends DefaultJavaPrettyPrinter> createSniperPrinter(
            Environment env) {
        env.setCommentEnabled(true);
        env.useTabulations(true);
        env.setTabulationSize(4);
        return () -> new SniperJavaPrettyPrinter(env);
    }

    private static Supplier<? extends DefaultJavaPrettyPrinter> createDefaultPrinter(
            Environment env) {
        return () -> new DefaultJavaPrettyPrinter(env);
    }

    private SoraldAbstractProcessor<?> createBaseProcessor(String ruleKey) {
        try {
            Class<?> processor = Processors.getProcessor(ruleKey);
            if (processor != null) {
                Constructor<?> cons = processor.getConstructor();
                return (SoraldAbstractProcessor<?>) cons.newInstance();
            }
        } catch (InstantiationException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SoraldAbstractProcessor<?> createProcessor(String ruleKey) {
        SoraldAbstractProcessor<?> processor = createBaseProcessor(ruleKey);
        if (processor != null) {
            return processor
                    .setMaxFixes(config.getMaxFixesPerRule())
                    .setEventHandlers(eventHandlers);
        }
        return null;
    }
}
