package formula;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

/** Small helpers to print, evaluate and walk through formulas. */
public final class Formulas {

  private Formulas() {}

  /** Prints the formula with parentheses everywhere, so the parser can always read it back. */
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

  /**
   * Evaluates f with every atom treated as its own boolean (env maps {@link Literal#key()} to the value
   * of the positive version of the atom). This is what the tests use for the truth tables.
   */
  public static boolean evaluate(Formula f, Map<String, Boolean> env) {
    return evaluate(f, literal -> env.get(literal.key()) == literal.positive());
  }

  /** Evaluates f, asking atomHolds whether each atom is true (e.g. by looking at the real event values). */
  public static boolean evaluate(Formula f, Predicate<Literal> atomHolds) {
    if (f instanceof Literal literal) {
      return atomHolds.test(literal);
    }
    if (f instanceof Const constant) {
      return constant.value();
    }
    if (f instanceof Not not) {
      return !evaluate(not.operand(), atomHolds);
    }
    if (f instanceof And and) {
      return and.operands().stream().allMatch(o -> evaluate(o, atomHolds));
    }
    if (f instanceof Or or) {
      return or.operands().stream().anyMatch(o -> evaluate(o, atomHolds));
    }
    if (f instanceof Nand nand) {
      return !nand.operands().stream().allMatch(o -> evaluate(o, atomHolds));
    }
    if (f instanceof Nor nor) {
      return !nor.operands().stream().anyMatch(o -> evaluate(o, atomHolds));
    }
    if (f instanceof Xor xor) {
      return evaluate(xor.left(), atomHolds) != evaluate(xor.right(), atomHolds);
    }
    if (f instanceof Implies implies) {
      return !evaluate(implies.left(), atomHolds) || evaluate(implies.right(), atomHolds);
    }
    Iff iff = (Iff) f;
    return evaluate(iff.left(), atomHolds) == evaluate(iff.right(), atomHolds);
  }

  /** Keys of all the atoms in f. */
  public static Set<String> literalKeys(Formula f) {
    Set<String> keys = new HashSet<>();
    for (Literal literal : literals(f)) {
      keys.add(literal.key());
    }
    return keys;
  }

  /** All the comparisons in f, in the order they show up. */
  public static List<Comparison> comparisons(Formula f) {
    List<Comparison> comparisons = new ArrayList<>();
    for (Literal literal : literals(f)) {
      if (literal instanceof Comparison comparison) {
        comparisons.add(comparison);
      }
    }
    return comparisons;
  }

  /** All the atoms in f, in the order they show up. */
  public static List<Literal> literals(Formula f) {
    List<Literal> literals = new ArrayList<>();
    collectLiterals(f, literals);
    return literals;
  }

  private static void collectLiterals(Formula f, List<Literal> literals) {
    if (f instanceof Literal literal) {
      literals.add(literal);
    } else if (f instanceof Not not) {
      collectLiterals(not.operand(), literals);
    } else if (f instanceof And and) {
      and.operands().forEach(o -> collectLiterals(o, literals));
    } else if (f instanceof Or or) {
      or.operands().forEach(o -> collectLiterals(o, literals));
    } else if (f instanceof Nand nand) {
      nand.operands().forEach(o -> collectLiterals(o, literals));
    } else if (f instanceof Nor nor) {
      nor.operands().forEach(o -> collectLiterals(o, literals));
    } else if (f instanceof Xor xor) {
      collectLiterals(xor.left(), literals);
      collectLiterals(xor.right(), literals);
    } else if (f instanceof Implies implies) {
      collectLiterals(implies.left(), literals);
      collectLiterals(implies.right(), literals);
    } else if (f instanceof Iff iff) {
      collectLiterals(iff.left(), literals);
      collectLiterals(iff.right(), literals);
    }
  }
}
