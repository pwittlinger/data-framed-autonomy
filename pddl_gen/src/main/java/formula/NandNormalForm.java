package formula;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

/**
 * Turns any formula into the shape we want: an AND of clauses, where each clause is either a single
 * atom or a nand(a1, ..., ak) of atoms. No not and no or are left at the end, the negations end up
 * inside the atoms (A.x > 10 negated is just A.x <= 10).
 *
 * The steps are:
 * <ol>
 *   <li>eliminate: rewrite {@code ->, <->, xor, nand, nor} with only and/or/not</li>
 *   <li>toNnf: push the negations down until they hit the atoms, then flip the atoms</li>
 *   <li>toCnf: distribute or over and, keeping the result as a set of clauses (sets of atoms)</li>
 *   <li>toNand: a clause (l1 or ... or lk) is the same as nand(not l1, ..., not lk)</li>
 * </ol>
 * Careful with step 3: in the worst case it blows up, e.g. (a1 and b1) or ... or (an and bn) gives
 * 2^n clauses. The usual fix (Tseitin) adds new helper variables, and those wouldn't mean anything
 * as data conditions, so we don't use it.
 */
public final class NandNormalForm {

  private NandNormalForm() {}

  /** Parses the text and gives back its NAND normal form as a string. */
  public static String convert(String text) {
    return Formulas.render(normalize(FormulaParser.parse(text)));
  }

  public static Formula normalize(Formula f) {
    return toNand(toCnf(toNnf(eliminate(f), false)));
  }

  /**
   * The clauses of f (each one is an OR of atoms), sorted so the order is always the same.
   * An empty list means f is always true, a list with an empty clause means it's always false.
   */
  public static List<List<Literal>> clauses(Formula f) {
    return sortClauses(toCnf(toNnf(eliminate(f), false)));
  }

  /** Rewrites everything that isn't and/or/not. */
  static Formula eliminate(Formula f) {
    if (f instanceof Literal || f instanceof Const) {
      return f;
    }
    if (f instanceof Not not) {
      return new Not(eliminate(not.operand()));
    }
    if (f instanceof And and) {
      return new And(eliminateAll(and.operands()));
    }
    if (f instanceof Or or) {
      return new Or(eliminateAll(or.operands()));
    }
    if (f instanceof Nand nand) {
      return new Not(new And(eliminateAll(nand.operands())));
    }
    if (f instanceof Nor nor) {
      return new Not(new Or(eliminateAll(nor.operands())));
    }
    if (f instanceof Implies implies) {
      return new Or(List.of(new Not(eliminate(implies.left())), eliminate(implies.right())));
    }
    if (f instanceof Iff iff) {
      Formula left = eliminate(iff.left());
      Formula right = eliminate(iff.right());
      return new And(List.of(new Or(List.of(new Not(left), right)), new Or(List.of(left, new Not(right)))));
    }
    Xor xor = (Xor) f;
    Formula left = eliminate(xor.left());
    Formula right = eliminate(xor.right());
    return new And(List.of(new Or(List.of(left, right)), new Or(List.of(new Not(left), new Not(right)))));
  }

  private static List<Formula> eliminateAll(List<Formula> operands) {
    return operands.stream().map(NandNormalForm::eliminate).toList();
  }

  /** Pushes the negations down (De Morgan) and flips the atoms they end up on. negated = odd number of nots above us. */
  static Formula toNnf(Formula f, boolean negated) {
    if (f instanceof Literal literal) {
      return negated ? literal.negate() : literal;
    }
    if (f instanceof Const constant) {
      return new Const(constant.value() != negated);
    }
    if (f instanceof Not not) {
      return toNnf(not.operand(), !negated);
    }
    if (f instanceof And and) {
      List<Formula> operands = and.operands().stream().map(o -> toNnf(o, negated)).toList();
      return negated ? new Or(operands) : new And(operands);
    }
    if (f instanceof Or or) {
      List<Formula> operands = or.operands().stream().map(o -> toNnf(o, negated)).toList();
      return negated ? new And(operands) : new Or(operands);
    }
    throw new IllegalArgumentException("Call eliminate before toNnf: " + f);
  }

  /** The formula as a set of clauses. Empty set = true, a set with the empty clause in it = false. */
  static Set<Set<Literal>> toCnf(Formula f) {
    if (f instanceof Literal literal) {
      return Set.of(Set.of(literal));
    }
    if (f instanceof Const constant) {
      return constant.value() ? Set.of() : Set.of(Set.of());
    }
    if (f instanceof And and) {
      Set<Set<Literal>> clauses = new HashSet<>();
      for (Formula operand : and.operands()) {
        clauses.addAll(toCnf(operand));
      }
      return simplify(clauses);
    }
    if (f instanceof Or or) {
      // We start from "false" ({{}}) and OR the operands in one by one: every clause on the left
      // gets merged with every clause on the right
      Set<Set<Literal>> clauses = Set.of(Set.of());
      for (Formula operand : or.operands()) {
        Set<Set<Literal>> operandClauses = toCnf(operand);
        Set<Set<Literal>> distributed = new HashSet<>();
        for (Set<Literal> c1 : clauses) {
          for (Set<Literal> c2 : operandClauses) {
            Set<Literal> union = new HashSet<>(c1);
            union.addAll(c2);
            distributed.add(Set.copyOf(union));
          }
        }
        clauses = simplify(distributed);
      }
      return clauses;
    }
    throw new IllegalArgumentException("Call toNnf before toCnf: " + f);
  }

  /**
   * Cleans up the clauses without changing the meaning:
   * - a clause with both l and not l is always true, so we drop it
   * - if both l and not l are clauses on their own, the whole thing is false
   * - if {a} is there, {a, b} doesn't add anything, so we drop the bigger one
   */
  static Set<Set<Literal>> simplify(Set<Set<Literal>> clauses) {
    List<Set<Literal>> remaining = clauses.stream()
        .filter(c -> c.stream().noneMatch(literal -> c.contains(literal.negate())))
        .collect(Collectors.toCollection(ArrayList::new));
    Set<Literal> units = remaining.stream()
        .filter(c -> c.size() == 1)
        .flatMap(Set::stream)
        .collect(Collectors.toSet());
    if (remaining.stream().anyMatch(Set::isEmpty) || units.stream().anyMatch(literal -> units.contains(literal.negate()))) {
      return Set.of(Set.of());
    }
    remaining.sort(Comparator.comparingInt(Set::size));
    List<Set<Literal>> kept = new ArrayList<>();
    for (Set<Literal> clause : remaining) {
      if (kept.stream().noneMatch(clause::containsAll)) {
        kept.add(clause);
      }
    }
    return Set.copyOf(kept);
  }

  private static final Comparator<Literal> LITERAL_ORDER =
      Comparator.comparing(Literal::key).thenComparing(literal -> !literal.positive());

  // Sets have no order, so we sort everything to always print the same thing
  private static List<List<Literal>> sortClauses(Set<Set<Literal>> clauses) {
    return clauses.stream()
        .map(c -> c.stream().sorted(LITERAL_ORDER).toList())
        .sorted(Comparator.<List<Literal>>comparingInt(List::size).thenComparing(Object::toString))
        .toList();
  }

  /** Writes the clauses as an AND of (atom | nand(atoms)). */
  static Formula toNand(Set<Set<Literal>> clauses) {
    if (clauses.isEmpty()) {
      return new Const(true);
    }
    if (clauses.contains(Set.of())) {
      return new Const(false);
    }
    List<Formula> conjuncts = new ArrayList<>();
    for (List<Literal> literals : sortClauses(clauses)) {
      if (literals.size() == 1) {
        conjuncts.add(literals.get(0));
      } else {
        conjuncts.add(new Nand(literals.stream().map(literal -> (Formula) literal.negate()).toList()));
      }
    }
    return conjuncts.size() == 1 ? conjuncts.get(0) : new And(conjuncts);
  }
}
