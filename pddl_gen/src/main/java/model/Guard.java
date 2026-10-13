package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import formula.Formula;
import formula.Formula.And;
import formula.Formula.Comparison;
import formula.Formula.Const;
import formula.Formula.Iff;
import formula.Formula.Implies;
import formula.Formula.Literal;
import formula.Formula.Nand;
import formula.Formula.Nor;
import formula.Formula.Not;
import formula.Formula.Or;
import formula.Formula.Xor;
import formula.FormulaParser;

/**
 * Glue between the condition strings in the .decl files and the formula package.
 *
 * A guard is the data condition that has to hold for an automaton arc to fire. After going through here
 * it only contains comparisons with = != < <= > >=, so the PDDL side doesn't have to care about
 * "is", "in" and so on.
 */
public final class Guard {

  private Guard() {}

  /** Parses a condition from the .decl file. No condition (or just stray delimiters) gives back null. */
  public static Formula parse(String conditionString) {
    if (conditionString == null || conditionString.isBlank()) {
      return null;
    }
    String trimmed = conditionString.strip();
    // Some exports leave a dangling "|" or similar when there's no condition, that's not a real condition
    if (!trimmed.matches(".*[A-Za-z0-9].*")) {
      return null;
    }
    return expandSetOperators(FormulaParser.parse(trimmed));
  }

  /** The guard for "the condition does NOT hold". No condition means always true, so here it's always false. */
  public static Formula negate(Formula guard) {
    return guard == null ? new Const(false) : new Not(guard);
  }

  /**
   * "is"/"is not" become = / != and the sets become plain comparisons:
   * A.c in (c1, c2) is A.c = c1 or A.c = c2, and A.c not in (c1, c2) is A.c != c1 and A.c != c2.
   */
  static Formula expandSetOperators(Formula f) {
    if (f instanceof Comparison comparison) {
      return expandComparison(comparison);
    }
    if (f instanceof Literal || f instanceof Const) {
      return f;
    }
    if (f instanceof Not not) {
      return new Not(expandSetOperators(not.operand()));
    }
    if (f instanceof And and) {
      return new And(and.operands().stream().map(Guard::expandSetOperators).toList());
    }
    if (f instanceof Or or) {
      return new Or(or.operands().stream().map(Guard::expandSetOperators).toList());
    }
    if (f instanceof Nand nand) {
      return new Nand(nand.operands().stream().map(Guard::expandSetOperators).toList());
    }
    if (f instanceof Nor nor) {
      return new Nor(nor.operands().stream().map(Guard::expandSetOperators).toList());
    }
    if (f instanceof Xor xor) {
      return new Xor(expandSetOperators(xor.left()), expandSetOperators(xor.right()));
    }
    if (f instanceof Implies implies) {
      return new Implies(expandSetOperators(implies.left()), expandSetOperators(implies.right()));
    }
    Iff iff = (Iff) f;
    return new Iff(expandSetOperators(iff.left()), expandSetOperators(iff.right()));
  }

  private static Formula expandComparison(Comparison c) {
    switch (c.operator()) {
      case "is":
        return new Comparison(c.scope(), c.attribute(), "=", c.value());
      case "is not":
        return new Comparison(c.scope(), c.attribute(), "!=", c.value());
      case "in":
      case "not in": {
        boolean in = c.operator().equals("in");
        List<Formula> parts = new ArrayList<>();
        for (Object value : (List<?>) c.value()) {
          parts.add(new Comparison(c.scope(), c.attribute(), in ? "=" : "!=", value));
        }
        if (parts.size() == 1) {
          return parts.get(0);
        }
        return in ? new Or(parts) : new And(parts);
      }
      default:
        return c;
    }
  }

  /**
   * Swaps the categorical values (c1, c2, ...) with their number, using the same encoding as
   * variable_values.txt. Numbers stay as they are.
   */
  public static Formula encodeValues(Formula f, Map<String, Attribute> attributes, String constraintName) {
    if (f == null) {
      return null;
    }
    if (f instanceof Comparison c) {
      Attribute attribute = attributes.get(c.attribute());
      if (attribute == null || !"enum".equals(attribute.getType()) || c.value() instanceof Number) {
        return c;
      }
      Double encoded = attribute.variableValueMap.get(String.valueOf(c.value()));
      if (encoded == null) {
        throw new Error("Value " + c.value() + " in constraint " + constraintName + " is not declared for attribute " + c.attribute());
      }
      return new Comparison(c.scope(), c.attribute(), c.operator(), encoded.intValue());
    }
    if (f instanceof Literal || f instanceof Const) {
      return f;
    }
    if (f instanceof Not not) {
      return new Not(encodeValues(not.operand(), attributes, constraintName));
    }
    if (f instanceof And and) {
      return new And(and.operands().stream().map(o -> encodeValues(o, attributes, constraintName)).toList());
    }
    if (f instanceof Or or) {
      return new Or(or.operands().stream().map(o -> encodeValues(o, attributes, constraintName)).toList());
    }
    if (f instanceof Nand nand) {
      return new Nand(nand.operands().stream().map(o -> encodeValues(o, attributes, constraintName)).toList());
    }
    if (f instanceof Nor nor) {
      return new Nor(nor.operands().stream().map(o -> encodeValues(o, attributes, constraintName)).toList());
    }
    if (f instanceof Xor xor) {
      return new Xor(encodeValues(xor.left(), attributes, constraintName), encodeValues(xor.right(), attributes, constraintName));
    }
    if (f instanceof Implies implies) {
      return new Implies(encodeValues(implies.left(), attributes, constraintName), encodeValues(implies.right(), attributes, constraintName));
    }
    Iff iff = (Iff) f;
    return new Iff(encodeValues(iff.left(), attributes, constraintName), encodeValues(iff.right(), attributes, constraintName));
  }
}
