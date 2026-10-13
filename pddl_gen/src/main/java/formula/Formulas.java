package formula;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import formula.Formula.And;
import formula.Formula.Const;
import formula.Formula.Iff;
import formula.Formula.Implies;
import formula.Formula.Literal;
import formula.Formula.Nand;
import formula.Formula.Nor;
import formula.Formula.Not;
import formula.Formula.Or;
import formula.Formula.Xor;

/** Rendering and evaluation of {@link Formula} trees. */
public final class Formulas {

  private Formulas() {}

  /** Fully parenthesised rendering that {@link FormulaParser} reads back. */
  public static String render(Formula f) {
    if (f instanceof Literal || f instanceof Const) {
      return f.toString();
    }
    if (f instanceof Not not) {
      return "not (" + render(not.operand()) + ")";
    }
    if (f instanceof And and) {
      return "(" + join(and.operands(), " and ") + ")";
    }
    if (f instanceof Or or) {
      return "(" + join(or.operands(), " or ") + ")";
    }
    if (f instanceof Nand nand) {
      return "nand(" + join(nand.operands(), ", ") + ")";
    }
    if (f instanceof Nor nor) {
      return "nor(" + join(nor.operands(), ", ") + ")";
    }
    if (f instanceof Xor xor) {
      return "(" + render(xor.left()) + " xor " + render(xor.right()) + ")";
    }
    if (f instanceof Implies implies) {
      return "(" + render(implies.left()) + " -> " + render(implies.right()) + ")";
    }
    Iff iff = (Iff) f;
    return "(" + render(iff.left()) + " <-> " + render(iff.right()) + ")";
  }

  private static String join(List<Formula> operands, String separator) {
    return operands.stream().map(Formulas::render).collect(Collectors.joining(separator));
  }

  /** Evaluates {@code f} treating each atom as an independent boolean variable ({@code env} maps {@link Literal#key()} to its value). */
  public static boolean evaluate(Formula f, Map<String, Boolean> env) {
    if (f instanceof Literal literal) {
      return env.get(literal.key()) == literal.positive();
    }
    if (f instanceof Const constant) {
      return constant.value();
    }
    if (f instanceof Not not) {
      return !evaluate(not.operand(), env);
    }
    if (f instanceof And and) {
      return and.operands().stream().allMatch(o -> evaluate(o, env));
    }
    if (f instanceof Or or) {
      return or.operands().stream().anyMatch(o -> evaluate(o, env));
    }
    if (f instanceof Nand nand) {
      return !nand.operands().stream().allMatch(o -> evaluate(o, env));
    }
    if (f instanceof Nor nor) {
      return !nor.operands().stream().anyMatch(o -> evaluate(o, env));
    }
    if (f instanceof Xor xor) {
      return evaluate(xor.left(), env) != evaluate(xor.right(), env);
    }
    if (f instanceof Implies implies) {
      return !evaluate(implies.left(), env) || evaluate(implies.right(), env);
    }
    Iff iff = (Iff) f;
    return evaluate(iff.left(), env) == evaluate(iff.right(), env);
  }

  /** Keys of all the boolean variables (atoms) occurring in {@code f}. */
  public static Set<String> literalKeys(Formula f) {
    Set<String> keys = new HashSet<>();
    collectKeys(f, keys);
    return keys;
  }

  private static void collectKeys(Formula f, Set<String> keys) {
    if (f instanceof Literal literal) {
      keys.add(literal.key());
    } else if (f instanceof Not not) {
      collectKeys(not.operand(), keys);
    } else if (f instanceof And and) {
      and.operands().forEach(o -> collectKeys(o, keys));
    } else if (f instanceof Or or) {
      or.operands().forEach(o -> collectKeys(o, keys));
    } else if (f instanceof Nand nand) {
      nand.operands().forEach(o -> collectKeys(o, keys));
    } else if (f instanceof Nor nor) {
      nor.operands().forEach(o -> collectKeys(o, keys));
    } else if (f instanceof Xor xor) {
      collectKeys(xor.left(), keys);
      collectKeys(xor.right(), keys);
    } else if (f instanceof Implies implies) {
      collectKeys(implies.left(), keys);
      collectKeys(implies.right(), keys);
    } else if (f instanceof Iff iff) {
      collectKeys(iff.left(), keys);
      collectKeys(iff.right(), keys);
    }
  }
}
