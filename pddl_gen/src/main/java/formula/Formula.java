package formula;

import java.util.List;
import java.util.Map;

/**
 * Syntax tree of a boolean formula over MP-DECLARE data conditions.
 *
 * Atoms are data conditions ({@code A.amount > 10}, {@code T.cat is c1}, {@code A.cat in (c1, c2)})
 * or plain propositional variables ({@code p}). Every atom can be negated without a {@link Not} node:
 * comparisons flip their operator, propositional variables carry a polarity.
 */
public sealed interface Formula permits Formula.Literal, Formula.Const, Formula.Not, Formula.And, Formula.Or,
    Formula.Nand, Formula.Nor, Formula.Xor, Formula.Implies, Formula.Iff {

  // Each operator mapped to its complement
  Map<String, String> NEGATED_OPERATOR = Map.of(
      ">", "<=", "<=", ">",
      ">=", "<", "<", ">=",
      "=", "!=", "!=", "=",
      "is", "is not", "is not", "is",
      "in", "not in", "not in", "in");

  // The "positive" operator of each complementary pair, used to identify an atom and its negation as the same variable
  List<String> POSITIVE_OPERATORS = List.of(">", ">=", "=", "is", "in");

  /** A formula that can be negated in place (no NOT node needed). */
  sealed interface Literal extends Formula permits Comparison, Var {
    Literal negate();

    /** Identifies the underlying boolean variable: a literal and its negation share the key. */
    String key();

    boolean positive();
  }

  /** {@code <scope>.<attribute> <operator> <value>}, e.g. {@code A.amount >= 10}. The value is a List for (not) in. */
  record Comparison(String scope, String attribute, String operator, Object value) implements Literal {
    public Comparison {
      if (!NEGATED_OPERATOR.containsKey(operator)) {
        throw new IllegalArgumentException("Unknown operator: " + operator);
      }
    }

    @Override
    public Comparison negate() {
      return new Comparison(scope, attribute, NEGATED_OPERATOR.get(operator), value);
    }

    @Override
    public boolean positive() {
      return POSITIVE_OPERATORS.contains(operator);
    }

    @Override
    public String key() {
      String positiveOperator = positive() ? operator : NEGATED_OPERATOR.get(operator);
      return "cmp|" + scope + "|" + attribute + "|" + positiveOperator + "|" + value;
    }

    @Override
    public String toString() {
      String renderedValue = value instanceof List<?> values
          ? "(" + String.join(", ", values.stream().map(String::valueOf).toList()) + ")"
          : String.valueOf(value);
      return scope + "." + attribute + " " + operator + " " + renderedValue;
    }
  }

  /** Propositional variable with a polarity ({@code p} or {@code not p}). */
  record Var(String name, boolean positive) implements Literal {
    public Var(String name) {
      this(name, true);
    }

    @Override
    public Var negate() {
      return new Var(name, !positive);
    }

    @Override
    public String key() {
      return "var|" + name;
    }

    @Override
    public String toString() {
      return positive ? name : "not " + name;
    }
  }

  record Const(boolean value) implements Formula {
    @Override
    public String toString() {
      return value ? "true" : "false";
    }
  }

  record Not(Formula operand) implements Formula {}

  record And(List<Formula> operands) implements Formula {
    public And {
      operands = List.copyOf(operands);
    }
  }

  record Or(List<Formula> operands) implements Formula {
    public Or {
      operands = List.copyOf(operands);
    }
  }

  record Nand(List<Formula> operands) implements Formula {
    public Nand {
      operands = List.copyOf(operands);
    }
  }

  record Nor(List<Formula> operands) implements Formula {
    public Nor {
      operands = List.copyOf(operands);
    }
  }

  record Xor(Formula left, Formula right) implements Formula {}

  record Implies(Formula left, Formula right) implements Formula {}

  record Iff(Formula left, Formula right) implements Formula {}
}
