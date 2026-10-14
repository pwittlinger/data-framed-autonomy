package translations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MultiInstanceMergerTest {

  // Same layout as the problems of our generators, trimmed down
  private static String problem(String activity, boolean emptyTrace) {
    return "(define (problem prob-trace)\n"
        + "  (:domain trace-alignment)\n\n"
        + "  (:objects\n"
        + "    t0 t1 - trace_state\n"
        + "    s1_1 s1_2 - automaton_state\n"
        + "    " + activity + " - activity\n"
        + "    amount - parameter_name\n"
        + "    amount500 - value_name\n"
        + "    Response_A_B - constraint\n"
        + "  )\n"
        + "  (:init\n"
        + "    (= (total_cost) 0)\n"
        + "    (= (variable_value amount500) 50)\n"
        + (emptyTrace ? "    (recovery_finished)\n" : "    (trace t0 " + activity + " t1)\n")
        + "    (automaton s1_1 " + activity + " s1_2)\n"
        + "  )\n"
        + "  ;; GOAL STATES\n"
        + "  (:goal (and\n"
        + "    (cur_t_state t1)\n"
        + "    (or\n"
        + "      (cur_s_state s1_1)\n"
        + "    )\n"
        + "    (not (failure))\n"
        + "  ))\n\n"
        + "  (:metric minimize (total_cost))\n"
        + ")\n";
  }

  @Test
  void everyInstanceGetsItsOwnObjects() {
    String merged = MultiInstanceMerger.merge(List.of(problem("A", false), problem("A", true)));
    assertTrue(merged.contains("t0_i1 t1_i1 t0_i2 t1_i2 - trace_state"));
    assertTrue(merged.contains("A_i1 A_i2 - activity"));
    assertTrue(merged.contains("Response_A_B_i1 Response_A_B_i2 - constraint"));
    assertTrue(merged.contains("i1 i2 - instance"));
    assertTrue(merged.contains("(trace t0_i1 A_i1 t1_i1)"));
    assertTrue(merged.contains("(automaton s1_1_i2 A_i2 s1_2_i2)"));
    assertTrue(merged.contains("(activity_of A_i2 i2)"));
    assertTrue(merged.contains("(trace_state_of t1_i1 i1)"));
  }

  @Test
  void parametersAndValuesAreShared() {
    String merged = MultiInstanceMerger.merge(List.of(problem("A", false), problem("A", false)));
    assertTrue(merged.contains("    amount - parameter_name"));
    assertTrue(merged.contains("    amount500 - value_name"));
    // Facts that are the same in every instance only go in once
    assertEquals(1, count(merged, "(= (variable_value amount500) 50)"));
    assertEquals(1, count(merged, "(= (total_cost) 0)"));
    assertEquals(1, count(merged, "(not (failure))"));
  }

  @Test
  void anEmptyTraceFinishesOnlyItsOwnPrefix() {
    String merged = MultiInstanceMerger.merge(List.of(problem("A", false), problem("A", true)));
    assertTrue(merged.contains("(recovery_finished i2)"));
    assertFalse(merged.contains("(recovery_finished)"));
    assertFalse(merged.contains("(recovery_finished i1)"));
  }

  @Test
  void goalsOverMoreLinesStayWhole() {
    String merged = MultiInstanceMerger.merge(List.of(problem("A", false), problem("A", false)));
    // Both instances keep their own (or ...) block, the "(or" and ")" lines aren't merged away
    assertEquals(2, count(merged, "    (or\n"));
    assertTrue(merged.contains("(cur_s_state s1_1_i1)"));
    assertTrue(merged.contains("(cur_s_state s1_1_i2)"));
  }

  private static int count(String text, String part) {
    int n = 0;
    for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
      n++;
    }
    return n;
  }
}
