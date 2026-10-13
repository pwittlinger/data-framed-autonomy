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
 * Conversion of an arbitrary formula into NAND normal form.
 *
 * Target shape: a conjunction (outermost) of clauses, where each clause is either a single atom or
 * {@code nand(a1, ..., ak)} over atoms. There are no NOT nodes (negations are folded into the atoms by
 * flipping the comparison) and no OR nodes.
 *
 * Pipeline:
 * <ol>
 *   <li>eliminate - rewrite {@code ->, <->, xor, nand, nor} using and/or/not</li>
 *   <li>toNnf - push negations down to the atoms (De Morgan), then fold them into the atoms</li>
 *   <li>toCnf - distribute or over and, as a set of clauses (each a set of literals), simplified</li>
 *   <li>toNand - write each clause {@code (l1 or ... or lk)} as {@code nand(not l1, ..., not lk)}</li>
 * </ol>
 * Step 3 is exponential in the worst case (e.g. {@code (a1 and b1) or ... or (an and bn)} has 2^n clauses).
 * Tseitin-style encodings avoid this but need fresh auxiliary variables, which have no meaning as
 * data conditions, so they are not used here.
 */
public final class NandNormalForm {

  private NandNormalForm() {}

  /** Parses {@code text} and returns its NAND normal form as a string. */
  public static String convert(String text) {
    return Formulas.render(normalize(FormulaParser.parse(text)));
  }

  public static Formula normalize(Formula f) {
    return toNand(toCnf(toNnf(eliminate(f), false)));
  }

  /** Rewrites everything except and/or/not/literals/constants. */
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

  /** Negation normal form of an eliminated formula, with every negation folded into its atom. */
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
      return negated ? new Or(operands) : new And(operands); // De Morgan
    }
    if (f instanceof Or or) {
      List<Formula> operands = or.operands().stream().map(o -> toNnf(o, negated)).toList();
      return negated ? new And(operands) : new Or(operands); // De Morgan
    }
    throw new IllegalArgumentException("Formula must be eliminated before NNF conversion: " + f);
  }

  /** Clauses (sets of literals) of an NNF formula. Empty set = true; containing the empty clause = false. */
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
      Set<Set<Literal>> clauses = Set.of(Set.of()); // neutral element of the distribution (false)
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
    throw new IllegalArgumentException("Formula must be in NNF before CNF conversion: " + f);
  }

  /**
   * Drops tautological clauses (containing a literal and its negation) and subsumed clauses;
   * complementary unit clauses (l and not l) make the whole set false.
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

  /** Writes a clause set as an and of (atom | nand(atoms)). */
  static Formula toNand(Set<Set<Literal>> clauses) {
    if (clauses.isEmpty()) {
      return new Const(true);
    }
    if (clauses.contains(Set.of())) {
      return new Const(false);
    }
    List<List<Literal>> sortedClauses = clauses.stream()
        .map(c -> c.stream().sorted(LITERAL_ORDER).toList())
        .sorted(Comparator.<List<Literal>>comparingInt(List::size).thenComparing(Object::toString))
        .toList();
    List<Formula> conjuncts = new ArrayList<>();
    for (List<Literal> literals : sortedClauses) {
      if (literals.size() == 1) {
        conjuncts.add(literals.get(0));
      } else {
        conjuncts.add(new Nand(literals.stream().map(literal -> (Formula) literal.negate()).toList()));
      }
    }
    return conjuncts.size() == 1 ? conjuncts.get(0) : new And(conjuncts);
  }
}
