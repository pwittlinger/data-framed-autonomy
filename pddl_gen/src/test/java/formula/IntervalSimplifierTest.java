package formula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import formula.Formula.Comparison;
import formula.Formula.Literal;
import values.ValueIntervalPreprocessor;

class IntervalSimplifierTest {

  private static Comparison x(String operator, int value) {
    return new Comparison("A", "x", operator, value);
  }

  private static Comparison y(String operator, int value) {
    return new Comparison("A", "y", operator, value);
  }

  private static Optional<List<Literal>> and(Literal... literals) {
    return IntervalSimplifier.conjunction(List.of(literals));
  }

  @Test
  void keepsTheTightestBound() {
    assertEquals(Optional.of(List.of(x("<", 20))), and(x("<=", 90), x("<", 20)));
    assertEquals(Optional.of(List.of(x(">", 5))), and(x(">=", 5), x(">", 5)));
    assertEquals(Optional.of(List.of(x(">", 10), x("<=", 50))), and(x(">", 10), x(">", 3), x("<=", 50)));
  }

  @Test
  void findsEmptyIntervals() {
    assertEquals(Optional.empty(), and(x(">", 50), x("<", 20)));
    assertEquals(Optional.empty(), and(x(">", 5), x("<=", 5)));
    assertEquals(Optional.empty(), and(x(">=", 5), x("<=", 5), x("!=", 5)));
    assertEquals(Optional.empty(), and(x("=", 5), x("=", 7)));
  }

  @Test
  void equalitiesAndInequalities() {
    assertEquals(Optional.of(List.of(x("=", 5))), and(x("=", 5), x(">", 3), x("<", 9)));
    // x != 3 can't happen once x > 5
    assertEquals(Optional.of(List.of(x(">", 5))), and(x("!=", 3), x(">", 5)));
    assertEquals(Optional.of(List.of(x("!=", 7), x(">", 5))), and(x("!=", 7), x(">", 5)));
  }

  @Test
  void attributesDontMix() {
    assertEquals(Optional.of(List.of(x(">", 5), y("<", 3))), and(x(">", 5), y("<", 3)));
  }

  @Test
  void nestedExampleFromTheLoanModel() {
    // (T.amount >= 20 and T.amount <= 50) or T.amount > 90
    Formula guard = FormulaParser.parse("(T.amount >= 20 and T.amount <= 50) or T.amount > 90");
    List<List<Literal>> clauses = NandNormalForm.clauses(guard);
    List<List<Literal>> simplified = IntervalSimplifier.clauses(clauses).orElseThrow();
    // (>= 20 or > 90) is just >= 20, the other clause stays
    Comparison atLeast20 = new Comparison("T", "amount", ">=", 20);
    assertTrue(simplified.contains(List.of(atLeast20)), simplified.toString());
    assertEquals(3, simplified.stream().mapToInt(List::size).sum());
  }

  @Test
  void clausesThatAlwaysHoldGoAway() {
    // x > 50 or x < 20 or y = 1: fine, nothing to do. x < 50 or x > 20 is always true
    List<List<Literal>> clauses = List.of(List.of(x("<", 50), x(">", 20)), List.of(y("=", 1)));
    assertEquals(Optional.of(List.of(List.of(y("=", 1)))), IntervalSimplifier.clauses(clauses));
  }

  @Test
  void unitClausesTogether() {
    List<List<Literal>> clauses = List.of(List.of(x(">", 5)), List.of(x(">", 10)), List.of(y("<", 1), y(">", 4)));
    assertEquals(Optional.of(List.of(List.of(x(">", 10)), List.of(y("<", 1), y(">", 4)))), IntervalSimplifier.clauses(clauses));
    assertEquals(Optional.empty(), IntervalSimplifier.clauses(List.of(List.of(x(">", 5)), List.of(x("<", 2)))));
  }

  @Test
  void unitsCleanUpTheOtherClauses() {
    // x > 120 or (x > 60 and x < 40) is just x > 120
    Formula guard = FormulaParser.parse("A.x > 120 or (A.x > 60 and A.x < 40)");
    assertEquals(Optional.of(List.of(List.of(x(">", 120)))), IntervalSimplifier.clauses(NandNormalForm.clauses(guard)));
    // with x > 5 required, (x > 3 or y = 1) always holds
    List<List<Literal>> clauses = List.of(List.of(x(">", 5)), List.of(x(">", 3), y("=", 1)));
    assertEquals(Optional.of(List.of(List.of(x(">", 5)))), IntervalSimplifier.clauses(clauses));
  }

  private static final String[] OPERATORS = {">", ">=", "<", "<=", "=", "!="};

  private static boolean holds(List<List<Literal>> clauses, double xv, double yv) {
    return clauses.stream().allMatch(clause -> clause.stream().anyMatch(l -> {
      Comparison c = (Comparison) l;
      return IntervalSimplifier.holds(c, c.attribute().equals("x") ? xv : yv);
    }));
  }

  /** Random CNFs over x and y with integer constants, checked on a grid of integers and halves. */
  @Test
  void randomGuardsStayEquivalent() {
    Random random = new Random(7);
    for (int round = 0; round < 3000; round++) {
      List<List<Literal>> clauses = new ArrayList<>();
      for (int i = 0, n = 1 + random.nextInt(4); i < n; i++) {
        List<Literal> clause = new ArrayList<>();
        for (int j = 0, m = 1 + random.nextInt(4); j < m; j++) {
          String operator = OPERATORS[random.nextInt(OPERATORS.length)];
          int value = random.nextInt(8);
          clause.add(random.nextBoolean() ? x(operator, value) : y(operator, value));
        }
        clauses.add(clause);
      }
      Optional<List<List<Literal>>> simplified = IntervalSimplifier.clauses(clauses);
      int before = clauses.stream().mapToInt(List::size).sum();
      for (double xv = -1; xv <= 9; xv += 0.5) {
        for (double yv = -1; yv <= 9; yv += 0.5) {
          boolean expected = holds(clauses, xv, yv);
          boolean actual = simplified.isPresent() && holds(simplified.get(), xv, yv);
          assertEquals(expected, actual, clauses + " -> " + simplified + " at x=" + xv + ", y=" + yv);
        }
      }
      simplified.ifPresent(s -> assertTrue(s.stream().mapToInt(List::size).sum() <= before));
    }
  }

  @Test
  void oneValuePerRegion() {
    Map<String, Double> values = new LinkedHashMap<>();
    for (double v : new double[] {0, 5, 10, 15, 20, 35, 50, 70, 90, 95, 100}) {
      values.put("amount" + v, v);
    }
    // Approve: (amount >= 20 and amount <= 50) or amount > 90 -> < 20, [20, 50], (50, 90], > 90
    Set<Comparison> approve = new LinkedHashSet<>(List.of(
        new Comparison("T", "amount", ">=", 20), new Comparison("T", "amount", "<=", 50), new Comparison("T", "amount", ">", 90)));
    assertEquals(List.of("amount0.0", "amount20.0", "amount70.0", "amount95.0"), ValueIntervalPreprocessor.representatives(values, approve));
    // no condition: one value is enough
    assertEquals(List.of("amount0.0"), ValueIntervalPreprocessor.representatives(values, Set.of()));
  }
}
