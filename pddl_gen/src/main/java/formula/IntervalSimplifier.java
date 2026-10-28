package formula;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import formula.Formula.And;
import formula.Formula.Comparison;
import formula.Formula.Const;
import formula.Formula.Literal;
import formula.Formula.Or;

/**
 * Interval reasoning on the comparisons of a guard (option --intervals of the generator).
 *
 * A comparison with a number (x > 5, x <= 90, x = 3, x != 4) says in which interval x has to be, so the
 * comparisons on the same attribute that are in AND can be replaced by their intersection, the minimal
 * interval: "x <= 90 and x < 20" is just "x < 20", and "x > 50 and x < 20" can never hold. In a guard in
 * CNF this happens in three places:
 * - inside a clause (l1 or ... or lk), since its nand form nand(not l1, ..., not lk) is an AND of comparisons
 * - between the clauses with a single comparison, which are in AND with each other
 * - between those and the other clauses (unit propagation): with x > 60 required, "x > 120 or x < 40"
 *   is just x > 120
 * We only ever drop comparisons, and the guard allows exactly the same values as before. No domain is
 * assumed: the values can be any number, integer or not.
 */
public final class IntervalSimplifier {

  private IntervalSimplifier() {}

  /**
   * The literals an AND of these literals needs, in their original order, or empty if they can't all
   * hold. Only comparisons with a number are looked at, everything else is kept as it is.
   */
  public static Optional<List<Literal>> conjunction(List<Literal> literals) {
    Map<String, Bounds> bounds = new LinkedHashMap<>();
    Set<Literal> needed = new HashSet<>();
    for (Literal literal : literals) {
      if (literal instanceof Comparison c && c.value() instanceof Number && Bounds.OPERATORS.contains(c.operator())) {
        bounds.computeIfAbsent(c.scope() + "." + c.attribute(), k -> new Bounds()).add(c);
      } else {
        needed.add(literal);
      }
    }
    for (Bounds b : bounds.values()) {
      if (b.isEmpty()) {
        return Optional.empty();
      }
      needed.addAll(b.needed());
    }
    return Optional.of(literals.stream().filter(needed::contains).distinct().toList());
  }

  /** The clauses of a guard in CNF, simplified, or empty if the guard can never hold. */
  public static Optional<List<List<Literal>>> clauses(List<List<Literal>> clauses) {
    List<List<Literal>> simplified = new ArrayList<>();
    for (List<Literal> clause : clauses) {
      Optional<List<Literal>> nand = conjunction(clause.stream().map(Literal::negate).toList());
      if (nand.isEmpty()) {
        continue; // the negated literals can't all hold, so the clause always does
      }
      simplified.add(nand.get().stream().map(Literal::negate).toList());
    }

    // The clauses with one literal are in AND with each other, and they have to hold for the guard to
    // hold, so in the other clauses a literal they rule out can go, and a clause with a literal they
    // already give is always true. That can leave new one-literal clauses, so we go on until nothing changes.
    while (true) {
      List<Literal> units = simplified.stream().filter(c -> c.size() == 1).map(c -> c.get(0)).toList();
      Optional<List<Literal>> neededUnits = conjunction(units);
      if (neededUnits.isEmpty()) {
        return Optional.empty();
      }
      List<Literal> known = neededUnits.get();
      Set<Literal> keep = new HashSet<>(known);
      boolean changed = false;
      Set<List<Literal>> next = new LinkedHashSet<>();
      for (List<Literal> clause : simplified) {
        if (clause.size() == 1) {
          if (keep.contains(clause.get(0))) {
            next.add(clause);
          }
          continue;
        }
        if (clause.stream().anyMatch(l -> contradicts(known, l.negate()))) {
          changed = true; // the units already make one of its literals true
          continue;
        }
        List<Literal> rest = clause.stream().filter(l -> !contradicts(known, l)).toList();
        if (rest.isEmpty()) {
          return Optional.empty();
        }
        changed |= rest.size() < clause.size();
        next.add(rest);
      }
      simplified = new ArrayList<>(next);
      if (!changed) {
        return Optional.of(simplified);
      }
    }
  }

  // The known literals and l can't all hold
  private static boolean contradicts(List<Literal> known, Literal l) {
    List<Literal> all = new ArrayList<>(known);
    all.add(l);
    return conjunction(all).isEmpty();
  }

  /** The clauses as a formula again: an AND of ORs (true when there are no clauses). */
  public static Formula toFormula(List<List<Literal>> clauses) {
    List<Formula> conjuncts = new ArrayList<>();
    for (List<Literal> clause : clauses) {
      conjuncts.add(clause.size() == 1 ? clause.get(0) : new Or(new ArrayList<Formula>(clause)));
    }
    if (conjuncts.isEmpty()) {
      return new Const(true);
    }
    return conjuncts.size() == 1 ? conjuncts.get(0) : new And(conjuncts);
  }

  /** Whether the comparison holds for the value x (only for comparisons with a number). */
  public static boolean holds(Comparison c, double x) {
    double v = ((Number) c.value()).doubleValue();
    return switch (c.operator()) {
      case ">" -> x > v;
      case ">=" -> x >= v;
      case "<" -> x < v;
      case "<=" -> x <= v;
      case "=" -> x == v;
      case "!=" -> x != v;
      default -> throw new IllegalArgumentException("Not a numeric comparison: " + c);
    };
  }

  /** The interval of one attribute: the tightest lower and upper bound seen so far, and the != values. */
  private static final class Bounds {
    static final Set<String> OPERATORS = Set.of(">", ">=", "<", "<=", "=", "!=");

    double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
    boolean loStrict, hiStrict;
    Comparison loAtom, hiAtom, equalAtom;
    final List<Comparison> notEqual = new ArrayList<>();

    void add(Comparison c) {
      double v = value(c);
      switch (c.operator()) {
        case ">" -> lower(v, true, c);
        case ">=" -> lower(v, false, c);
        case "<" -> upper(v, true, c);
        case "<=" -> upper(v, false, c);
        case "=" -> {
          lower(v, false, c);
          upper(v, false, c);
          equalAtom = c;
        }
        default -> notEqual.add(c);
      }
    }

    // A bound replaces the old one if it's tighter: higher (lower for the upper bound), or the same but strict
    private void lower(double v, boolean strict, Comparison c) {
      if (v > lo || (v == lo && strict && !loStrict)) {
        lo = v;
        loStrict = strict;
        loAtom = c;
      }
    }

    private void upper(double v, boolean strict, Comparison c) {
      if (v < hi || (v == hi && strict && !hiStrict)) {
        hi = v;
        hiStrict = strict;
        hiAtom = c;
      }
    }

    private boolean inside(double v) {
      return (v > lo || (v == lo && !loStrict)) && (v < hi || (v == hi && !hiStrict));
    }

    boolean isEmpty() {
      if (lo > hi || (lo == hi && (loStrict || hiStrict))) {
        return true;
      }
      // a single point that a != takes away
      return lo == hi && notEqual.stream().anyMatch(c -> value(c) == lo);
    }

    List<Comparison> needed() {
      List<Comparison> needed = new ArrayList<>();
      if (lo == hi && equalAtom != null) {
        needed.add(equalAtom); // x = v says it all
        return needed;
      }
      if (loAtom != null) {
        needed.add(loAtom);
      }
      if (hiAtom != null && hiAtom != loAtom) {
        needed.add(hiAtom);
      }
      // x != v only matters when v is inside the interval
      for (Comparison c : notEqual) {
        if (inside(value(c))) {
          needed.add(c);
        }
      }
      return needed;
    }

    private static double value(Comparison c) {
      return ((Number) c.value()).doubleValue();
    }
  }
}
