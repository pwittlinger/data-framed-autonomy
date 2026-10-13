package Automaton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import formula.Formula.Comparison;
import formula.Formulas;
import model.Condition;
import model.Guard;
import model.OperatorType;

// Checks when a transition keeps the old encoding and when it switches to nand clauses
class TransitionGuardTest {

  private static Transition transition(String condition) {
    return new Transition(new State("s1"), new State("s2"), "A", Guard.parse(condition));
  }

  @Test
  void noConditionMeansNoGuard() {
    Transition t = transition("");
    assertEquals(List.of(), t.getReformedConditions());
    assertNull(t.getNandClauses());
  }

  @Test
  void plainAndOfComparisonsKeepsTheOldEncoding() {
    Transition t = transition("A.x > 10 and A.y <= 3");
    assertNull(t.getNandClauses());
    // > is still split into >= and != like before
    List<String> conditions = t.getReformedConditions().stream()
        .map(c -> c.parameterName + " " + c.operator + " " + c.value).sorted().toList();
    assertEquals(List.of("x BIGGER_OR_EQUAL 10", "x NOT_EQUAL 10", "y LESS_OR_EQUAL 3"), conditions);
  }

  @Test
  void orNeedsNandClauses() {
    Transition t = transition("A.x > 50 or A.x < 10");
    assertNull(t.getReformedConditions());
    // (x > 50 or x < 10) is nand(x <= 50, x >= 10)
    assertEquals(List.of(List.of("A.x >= 10", "A.x <= 50")), render(t.getNandClauses()));
  }

  @Test
  void twoValuesForTheSameSlotNeedNandClauses() {
    // The old encoding has one != slot per parameter, and x > 10 already uses it (it becomes >= 10 and != 10)
    Transition t = transition("A.x > 10 and A.x != 20");
    assertNull(t.getReformedConditions());
    assertEquals(List.of(List.of("A.x = 20"), List.of("A.x <= 10")), render(t.getNandClauses()));
  }

  @Test
  void setOperatorsAreExpanded() {
    assertEquals("((A.c = c1 or A.c = c2) and A.d != c3)",
        Formulas.render(Guard.parse("A.c in (c1, c2) and A.d is not c3")));
    assertEquals("(A.c != c1 and A.c != c2)", Formulas.render(Guard.parse("A.c not in c1, c2")));
  }

  @Test
  void negatedMissingConditionNeverFires() {
    // Chain Response uses the negated target condition: with no condition it must never fire
    Transition t = new Transition(new State("s1"), new State("s2"), "A", Guard.negate(null));
    assertNull(t.getReformedConditions());
    assertEquals(List.of(List.of()), render(t.getNandClauses()));
  }

  @Test
  void negationIsTheRealOne() {
    // not (x > 1 and y > 1) is (x <= 1 or y <= 1), not (x <= 1 and y <= 1) like the old code did
    Transition t = new Transition(new State("s1"), new State("s2"), "A", Guard.negate(Guard.parse("A.x > 1 and A.y > 1")));
    assertNotNull(t.getNandClauses());
    assertEquals(List.of(List.of("A.x > 1", "A.y > 1")), render(t.getNandClauses()));
  }

  @Test
  void doublesAreNotForcedIntoTheOldEncoding() {
    Transition t = transition("A.x >= 2.5");
    assertNull(t.getReformedConditions());
    assertTrue(t.getNandClauses().get(0).get(0).value() instanceof Double);
  }

  @Test
  void oldConditionsKeepTheActivity() {
    Condition c = transition("A.x = 4").getReformedConditions().get(0);
    assertEquals("A", c.activity);
    assertEquals(OperatorType.EQUAL, c.operator);
  }

  private static List<List<String>> render(List<List<Comparison>> clauses) {
    return clauses.stream().map(clause -> clause.stream().map(Comparison::toString).toList()).toList();
  }
}
