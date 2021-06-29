package sorald.processor;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import sorald.Constants;
import sorald.annotations.ProcessorAnnotation;
import sorald.rule.RuleViolation;
import spoon.reflect.code.*;
import spoon.reflect.factory.TypeFactory;
import spoon.reflect.reference.CtTypeReference;

@ProcessorAnnotation(key = "S2184", description = "Math operands should be cast before assignment")
public class CastArithmeticOperandProcessor extends SoraldAbstractProcessor<CtBinaryOperator> {

    private static final Map<String, BinaryOperatorKind> toOpKind =
            Map.of(
                    "division", BinaryOperatorKind.DIV,
                    "addition", BinaryOperatorKind.PLUS,
                    "subtraction", BinaryOperatorKind.MINUS,
                    "multiplication", BinaryOperatorKind.MUL);

    @Override
    protected void repairInternal(CtBinaryOperator element) {
        RuleViolation violation = getBestFits().get(element);
        CtTypeReference<?> typeToBeUsedToCast = getOpKindAndType(violation).getRight();
        CtExpression<?> lhs = element.getLeftHandOperand();
        CtExpression<?> rhs = element.getRightHandOperand();

        if (isIntLiteral(lhs)) {
            repairWithLiteralSuffix((CtLiteral<?>) lhs, typeToBeUsedToCast);
        } else if (isIntLiteral(rhs)) {
            repairWithLiteralSuffix((CtLiteral<?>) rhs, typeToBeUsedToCast);
        } else {
            repairWithCast(element, typeToBeUsedToCast);
        }
    }

    private static void repairWithCast(
            CtBinaryOperator element, CtTypeReference<?> typeToBeUsedToCast) {
        CtCodeSnippetExpression newBinaryOperator =
                element.getFactory()
                        .createCodeSnippetExpression(
                                "("
                                        + typeToBeUsedToCast.getSimpleName()
                                        + ") "
                                        + element.getLeftHandOperand());
        element.setLeftHandOperand(newBinaryOperator);
        // A nicer code for the casting would be the next line. However, more parentheses are added
        // in
        // the expressions when using such a solution.
        // element.getLeftHandOperand().addTypeCast(typeToBeUsedToCast.clone());
    }

    private static void repairWithLiteralSuffix(
            CtLiteral<?> literalInt, CtTypeReference<?> typeForSuffix) {
        Integer value = (Integer) literalInt.getValue();
        CtCodeSnippetExpression<?> literalWithSuffix =
                literalInt
                        .getFactory()
                        .createCodeSnippetExpression(
                                value + getUpperCaseLiteralSuffix(typeForSuffix));
        literalInt.replace(literalWithSuffix);
    }

    /**
     * Parse the message of a SonarJava rule violation to get the operation kind and type.
     *
     * <p>As of SonarJava 6.9.0.23563, the message can be on two forms.<br>
     * <code>"Cast one of the operands of this integer division to a \"double\"."</code><br>
     * <code>
     * "Cast one of the operands of this " + OPERATION_BY_KIND.get(expr.kind()) + " operation to a \"" + varType.name() + "\"."
     * </code><br>
     *
     * @param violation A rule violation
     * @return The operator kind and type of the expression
     * @see org.sonar.java.checks.CastArithmeticOperandCheck
     */
    private Pair<BinaryOperatorKind, CtTypeReference<?>> getOpKindAndType(RuleViolation violation) {
        String message = violation.getMessage();
        Pattern p = Pattern.compile(".*?(\\w+)( operation)? to a \"(\\w+)\".*");
        Matcher m = p.matcher(message);

        if (!m.matches()) {
            throw new IllegalStateException(
                    "Message '" + message + "' did not contain expected match");
        }

        String rawOpKind = m.group(1);
        String rawExpectedType = m.group(3);

        TypeFactory tf = getFactory().Type();
        Map<String, CtTypeReference<?>> nameToPrimitiveType =
                Stream.of(tf.floatPrimitiveType(), tf.longPrimitiveType(), tf.doublePrimitiveType())
                        .collect(Collectors.toMap(CtTypeReference::getSimpleName, ref -> ref));

        return Pair.of(toOpKind.get(rawOpKind), nameToPrimitiveType.get(rawExpectedType));
    }

    private static boolean isIntLiteral(CtExpression<?> expr) {
        return expr instanceof CtLiteral
                && expr.getFactory().Type().INTEGER_PRIMITIVE.equals(expr.getType());
    }

    private static String getUpperCaseLiteralSuffix(CtTypeReference<?> floatDoubleOrLong) {
        String simpleName = floatDoubleOrLong.getSimpleName().toLowerCase();
        assert List.of(Constants.FLOAT, Constants.DOUBLE, Constants.LONG).contains(simpleName);
        return Map.of(Constants.FLOAT, "f", Constants.DOUBLE, "d", Constants.LONG, "l")
                .get(simpleName)
                .toUpperCase();
    }
}
