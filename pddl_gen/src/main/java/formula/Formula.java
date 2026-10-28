package formula;

import java.util.List;
import java.util.Map;

/**
 * The tree we get after parsing a data condition.
 *
 * Atoms are either data conditions ({@code A.amount > 10}, {@code T.cat is c1}, {@code A.cat in (c1, c2)})
 * or plain variables ({@code p}), which we only really use for testing. The nice thing about the atoms is
 * that we can negate them without wrapping them in a {@link Not}: a comparison just flips its operator,
 * a variable flips its sign.
 */
public sealed interface Formula permits Formula.Literal, Formula.Const, Formula.Not, Formula.And, Formula.Or,
    Formula.Nand, Formula.Nor, Formula.Xor, Formula.Implies, Formula.Iff {

  // Every operator with its opposite
  Map<String, String> NEGATED_OPERATOR = Map.of(
      ">", "<=", "<=", ">",
      ">=", "<", "<", ">=",
      "=", "!=", "!=", "=",
      "is", "is not", "is not", "is",
      "in", "not in", "not in", "in");

  // One operator per pair, we use it so that an atom and its negation end up with the same key
  List<String> POSITIVE_OPERATORS = List.of(">", ">=", "=", "is", "in");

  /** Something we can negate in place, so either a comparison or a variable. */
  sealed interface Literal extends Formula permits Comparison, Var {
    Literal negate();

    /** Same key for an atom and its negation (A.x > 10 and A.x <= 10 are the same variable, just flipped). */
    String key();

    boolean positive();
  }

  /** A data condition like {@code A.amount >= 10}. For (not) in the value is a list. */
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

  /** A plain variable, {@code p} or {@code not p}. */
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
