package translations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import model.RecoveryStrategy;

/**
 * The domain variants, made from the normal domain of a recovery strategy (packaged in the jar) when the
 * generator exports them, so they follow the normal domains whenever those change:
 * - clauses: arcs can be guarded by nand clauses (data conditions that aren't just an AND of comparisons)
 * - multi: several process instances (the traces of a log) in one problem
 * Every change is marked in the output with ;; NAND or ;; MULTI, the rest is the normal domain.
 */
public final class DomainVariants {

  private DomainVariants() {}

  /** The normal domain of the strategy, as packaged in the jar. */
  public static String base(RecoveryStrategy strategy) throws IOException {
    String name = strategy.getDomainFileName();
    try (InputStream in = DomainVariants.class.getResourceAsStream("/domains/" + name)) {
      if (in == null) {
        throw new IOException("The domain is missing from the jar (/domains/" + name + ")");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }

  /** File name of a variant, e.g. domain_..._reset_time_costs_clauses.pddl. */
  public static String fileName(RecoveryStrategy strategy, boolean clauses, boolean multi) {
    String stem = strategy.getDomainFileName().replaceFirst("\\.pddl$", "");
    return stem + (clauses ? "_clauses" : "") + (multi ? "_multi" : "") + ".pddl";
  }

  public static String withClauses(String s) {
    s = "; NAND: data conditions that aren't just an AND of comparisons (or, not, ->, ...). The generator turns\n"
        + "; them into an AND of nand clauses (see NandGuardEncoder in pddl_gen).\n" + s;
    s = replace(s, TYPES, TYPES + "\n          guard_clause guard_atom ;; NAND: the two new types\n         ", 1);
    s = replace(s, "heap won't kaboom.\n    (failure)\n", "heap won't kaboom.\n    (failure)\n" + CLAUSE_PREDICATES, 1);
    s = replace(s, "    (wait)\n", "    (wait)\n    ;; NAND: the value an atom compares against\n    (atom_value ?g - guard_atom)\n", 1);
    // add_parameter only prunes values with the old encoding, clause guarded arcs are checked later
    s = replace(s, ADD_PARAMETER_GUARD, ADD_PARAMETER_GUARD.replace("(goal_state ?s2)\n",
        "(goal_state ?s2)\n        (not (clause_guarded ?s1 ?a ?s2)) ;; NAND: these arcs get checked in check_added_parameter_model\n"), 1);

    // check_added_parameter_model: same check on the value the planner picked for the added activity
    String added = nandBlock(atomHolds("(has_added_parameter_aut ?a ?pn ?s1)", "(added_parameter_aut ?a ?pn ?s1)"));
    s = insertBefore(s, "  ;; After validating all arcs move all corresponding arcs", "\n    )\n  )", "\n" + added);

    // validate-payload: the check on the value in the trace
    String traced = nandBlock(atomHolds("(has_parameter ?a ?pn ?t1 ?t2)", "(trace_parameter ?a ?pn ?t1 ?t2)"));
    return insertBefore(s, "  ;; Move automaton according to the enabled arcs decided in \"validate-payload\"", "\n        )\n      )", "\n" + traced);
  }

  public static String withMulti(String s) {
    s = "; MULTI: several process instances (the traces of a log) in one problem. They don't interact: every\n"
        + "; instance has its own trace states, automaton states, activities and constraints (the generator renames\n"
        + "; them with _i<k>), so the only things to fix are the few flags that were global for the whole problem.\n" + s;
    s = replace(s, TYPES, TYPES + "\n          instance ;; MULTI: one per trace of the log\n         ", 1);
    // recovery_finished said "the prefix is over" for the whole problem, now it's per instance
    s = replace(s, "\n    (recovery_finished)\n", MULTI_PREDICATES, 1);
    // add_action: only once the prefix of that instance is over
    s = replace(s, "\n      (recovery_finished)\n",
        "\n      (exists (?i - instance) (and (activity_of ?a ?i) (recovery_finished ?i))) ;; MULTI\n", 1);
    // validate-payload and skip-unused: only while the prefix of that instance is still going
    s = replace(s, "(not (recovery_finished))",
        "(not (exists (?i - instance) (and (trace_state_of ?t1 ?i) (recovery_finished ?i)))) ;; MULTI", 2);
    // sync-actions and skip-unused: the end of a trace finishes the prefix of its instance only
    s = replace(s, "(when (final_t_state ?t2) (recovery_finished))",
        "(forall (?i - instance) (when (and (final_t_state ?t2) (trace_state_of ?t2 ?i)) (recovery_finished ?i))) ;; MULTI\n"
            + "      (not (validated ?t1)) ;; MULTI", 2);
    s = replace(s, "\n          (after_sync)\n", "\n          (after_sync)\n          (validated ?t1) ;; MULTI\n", 1);
    return replace(s, "\n      (after_sync)\n", "\n      (after_sync)\n      (validated ?t1) ;; MULTI: the event that was validated\n", 1);
  }

  private static final String TYPES = "(:types activity automaton_state trace_state parameter_name value_name constraint";

  private static final String ADD_PARAMETER_GUARD = "        (has_constraint ?a ?pn ?s1 ?s2)\n"
      + "        (not (has_added_parameter_aut ?a ?pn ?s1))\n"
      + "        (goal_state ?s2)\n"
      + "        )";

  private static final String CLAUSE_PREDICATES = """

    ;; NAND: an arc can have a list of nand clauses as guard. clause_of links a clause to its arc,
    ;; atom_of an argument (atom) to its clause. An atom is "parameter <op> value".
    (clause_guarded ?s1 - automaton_state ?a - activity ?s2 - automaton_state)
    (clause_of ?c - guard_clause ?s1 ?s2 - automaton_state ?a - activity)
    (atom_of ?g - guard_atom ?c - guard_clause)
    (atom_param ?g - guard_atom ?pn - parameter_name)
    (atom_gt ?g - guard_atom)
    (atom_ge ?g - guard_atom)
    (atom_lt ?g - guard_atom)
    (atom_le ?g - guard_atom)
    (atom_eq ?g - guard_atom)
    (atom_ne ?g - guard_atom)
""";

  private static final String MULTI_PREDICATES = """

    ;; MULTI: the prefix of this instance has been replayed (it was one flag for the whole problem)
    (recovery_finished ?i - instance)
    ;; MULTI: which instance an activity / a trace state belongs to
    (activity_of ?a - activity ?i - instance)
    (trace_state_of ?t - trace_state ?i - instance)
    ;; MULTI: the event validate-payload just checked, so sync-actions moves on that one and not on an
    ;; event of another instance (whose arcs were never checked)
    (validated ?t - trace_state)
""";

  private static String atomHolds(String present, String value) {
    return """
(exists (?pn - parameter_name)
                  (and
                    (atom_param ?g ?pn)
                    %1$s
                    (or
                      (and (atom_gt ?g) (> %2$s (atom_value ?g)))
                      (and (atom_ge ?g) (>= %2$s (atom_value ?g)))
                      (and (atom_lt ?g) (< %2$s (atom_value ?g)))
                      (and (atom_le ?g) (<= %2$s (atom_value ?g)))
                      (and (atom_eq ?g) (= %2$s (atom_value ?g)))
                      (and (atom_ne ?g) (or (< %2$s (atom_value ?g)) (> %2$s (atom_value ?g))))
                    )
                  )
                )""".formatted(present, value);
  }

  private static String nandBlock(String holds) {
    return """

          ;; NAND: the arc is blocked if one of its nand clauses is false, i.e. all its atoms hold.
          ;; (A missing value already blocks the arc through has_constraint above.)
          (forall (?s1 ?s2 - automaton_state ?c - guard_clause)
            (when (and
                (cur_s_state ?s1)
                (automaton ?s1 ?a ?s2)
                (clause_of ?c ?s1 ?s2 ?a)
                (forall (?g - guard_atom)
                  (imply (atom_of ?g ?c)
                    %s
                  )
                )
              )
              (invalid ?s1 ?a ?s2))
          )
""".formatted(holds);
  }

  // Puts text right before the last "end" that comes before the marker
  private static String insertBefore(String s, String marker, String end, String text) {
    int i = s.indexOf(marker);
    int j = i < 0 ? -1 : s.lastIndexOf(end, i - end.length());
    if (j < 0) {
      throw new IllegalStateException("Unexpected domain, can't find " + marker.strip());
    }
    return s.substring(0, j) + text + s.substring(j);
  }

  private static String replace(String s, String old, String replacement, int expected) {
    int found = 0;
    for (int i = s.indexOf(old); i >= 0; i = s.indexOf(old, i + old.length())) {
      found++;
    }
    if (found != expected) {
      throw new IllegalStateException("Unexpected domain: expected " + expected + " times " + old.strip().lines().findFirst().orElse("")
          + ", found " + found);
    }
    return s.replace(old, replacement);
  }
}
