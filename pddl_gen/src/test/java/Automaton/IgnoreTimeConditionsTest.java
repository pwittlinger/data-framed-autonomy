package Automaton;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import model.DeclareConstraint;
import model.DeclareTemplate;

// --ignore_time_conditions: the automata get no clocks, no timed arcs and none of the states the windows need
class IgnoreTimeConditionsTest {

  private static final List<String> ACTIVITIES = List.of("A", "B", "C");

  // The templates the automata are built for
  private static final List<DeclareTemplate> TEMPLATES = List.of(DeclareTemplate.Absence, DeclareTemplate.Existence,
      DeclareTemplate.Exactly1, DeclareTemplate.Responded_Existence, DeclareTemplate.Response, DeclareTemplate.Not_Response,
      DeclareTemplate.Chain_Response, DeclareTemplate.Exclusive_Choice, DeclareTemplate.Precedence, DeclareTemplate.Not_Precedence);

  private static DeclareConstraint timed(DeclareTemplate template) {
    boolean unary = template == DeclareTemplate.Absence || template == DeclareTemplate.Existence || template == DeclareTemplate.Exactly1;
    DeclareConstraint dc = new DeclareConstraint(template, "A", null, unary ? null : "B", null);
    dc.assignTimeConditions("A,1,10,h/B,2,5,h");
    return dc;
  }

  private static boolean hasTimedArc(Automaton automaton) {
    return automaton.getTransitions().stream().anyMatch(t -> t.getMinTimeCondition() > -1.0 || t.getMaxTimeCondition() > -1.0);
  }

  @Test
  void windowsAreGone() {
    DeclareConstraint dc = timed(DeclareTemplate.Response);
    assertTrue(dc.hasExplicitTargetWindow());
    dc.ignoreTimeConditions();
    assertNull(dc.getActivationTimeConditions());
    assertNull(dc.getTargetTimeConditions());
    assertFalse(dc.hasExplicitTargetWindow());
  }

  @Test
  void noClocksAndNoTimedArcs() {
    for (DeclareTemplate template : TEMPLATES) {
      DeclareConstraint dc = timed(template);
      dc.ignoreTimeConditions();
      Automaton automaton = new Automaton(ACTIVITIES, "s_", dc);
      assertEquals(List.of(), dc.getClockConditions(), template.toString());
      assertFalse(hasTimedArc(automaton), template.toString());
    }
  }

  @Test
  void withoutWindowsToo() {
    // No time conditions in the .decl file: still no error, and nothing timed
    for (DeclareTemplate template : TEMPLATES) {
      DeclareConstraint dc = new DeclareConstraint(template, "A", null, "B", null);
      dc.ignoreTimeConditions();
      assertFalse(hasTimedArc(new Automaton(ACTIVITIES, "s_", dc)), template.toString());
    }
  }

  @Test
  void precedenceLosesTheTimedState() {
    // With windows Precedence has a 4th state, reached by the activation after the target within its window
    Automaton withTime = new Automaton(ACTIVITIES, "s_", timed(DeclareTemplate.Precedence));
    assertEquals(4, withTime.getStates().size());
    assertEquals(3, withTime.getTransitions().size());

    DeclareConstraint dc = timed(DeclareTemplate.Precedence);
    dc.ignoreTimeConditions();
    Automaton withoutTime = new Automaton(ACTIVITIES, "s_", dc);
    assertEquals(List.of("s_1", "s_2", "s_3"), withoutTime.getStates().stream().map(s -> s.name).toList());
    assertEquals(2, withoutTime.getTransitions().size());
  }
}
