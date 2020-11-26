package sorald.processor;

import sorald.annotations.IncompleteProcessor;
import sorald.annotations.ProcessorAnnotation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.ModifierKind;

@IncompleteProcessor(description = "does not fix variable naming")
@ProcessorAnnotation(key = 1444, description = "\"public static\" fields should be constant")
public class PublicStaticFieldShouldBeFinalProcessor extends SoraldAbstractProcessor<CtField<?>> {
    @Override
    public boolean canRepair(CtField<?> candidate) {
        return true;
    }

    @Override
    public void repair(CtField<?> element) {
        element.addModifier(ModifierKind.FINAL);
    }
}
