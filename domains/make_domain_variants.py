# Builds the domain variants from the normal domain, so when the normal domain changes we can just run
# this again (from the repo root):
#   python domains/make_domain_variants.py
#
#   _clauses.pddl        data conditions that aren't just an AND of comparisons (nand clauses)
#   _multi.pddl          several process instances (all the traces of a log) in one problem
#   _clauses_multi.pddl  both
#
# Every change is marked in the output with ;; NAND or ;; MULTI, the rest is the normal domain.

from pathlib import Path

DOMAINS = Path(__file__).resolve().parent
BASE = DOMAINS / "domain_framed_autonomy_process_reset_time.pddl"


def replace(s, old, new, count=1):
    assert s.count(old) == count, (s.count(old), old[:80])
    return s.replace(old, new)


# Section: nand clauses

def atom_holds(present, value):
    return f"""(exists (?pn - parameter_name)
                  (and
                    (atom_param ?g ?pn)
                    {present}
                    (or
                      (and (atom_gt ?g) (> {value} (atom_value ?g)))
                      (and (atom_ge ?g) (>= {value} (atom_value ?g)))
                      (and (atom_lt ?g) (< {value} (atom_value ?g)))
                      (and (atom_le ?g) (<= {value} (atom_value ?g)))
                      (and (atom_eq ?g) (= {value} (atom_value ?g)))
                      (and (atom_ne ?g) (or (< {value} (atom_value ?g)) (> {value} (atom_value ?g))))
                    )
                  )
                )"""


def nand_block(holds):
    return f"""
          ;; NAND: the arc is blocked if one of its nand clauses is false, i.e. all its atoms hold.
          ;; (A missing value already blocks the arc through has_constraint above.)
          (forall (?s1 ?s2 - automaton_state ?c - guard_clause)
            (when (and
                (cur_s_state ?s1)
                (automaton ?s1 ?a ?s2)
                (clause_of ?c ?s1 ?s2 ?a)
                (forall (?g - guard_atom)
                  (imply (atom_of ?g ?c)
                    {holds}
                  )
                )
              )
              (invalid ?s1 ?a ?s2))
          )
"""


def with_clauses(s):
    s = """; NAND: data conditions that aren't just an AND of comparisons (or, not, ->, ...). The generator turns
; them into an AND of nand clauses (see NandGuardEncoder in pddl_gen).
""" + s
    s = replace(s, "(:types activity automaton_state trace_state parameter_name value_name constraint",
                "(:types activity automaton_state trace_state parameter_name value_name constraint\n          guard_clause guard_atom ;; NAND: the two new types\n         ")
    s = replace(s, """heap won't kaboom.
    (failure)
""", """heap won't kaboom.
    (failure)

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
""")
    s = replace(s, """    (wait)
""", """    (wait)
    ;; NAND: the value an atom compares against
    (atom_value ?g - guard_atom)
""")
    # add_parameter only prunes values with the old encoding, clause guarded arcs are checked later
    s = replace(s, """        (has_constraint ?a ?pn ?s1 ?s2)
        (not (has_added_parameter_aut ?a ?pn ?s1))
        (goal_state ?s2)
        )""", """        (has_constraint ?a ?pn ?s1 ?s2)
        (not (has_added_parameter_aut ?a ?pn ?s1))
        (goal_state ?s2)
        (not (clause_guarded ?s1 ?a ?s2)) ;; NAND: these arcs get checked in check_added_parameter_model
        )""")

    # check_added_parameter_model: same check on the value the planner picked for the added activity
    added = nand_block(atom_holds("(has_added_parameter_aut ?a ?pn ?s1)", "(added_parameter_aut ?a ?pn ?s1)"))
    i = s.index("  ;; After validating all arcs move all corresponding arcs")
    j = s.rindex("\n    )\n  )", 0, i)
    s = s[:j] + "\n" + added + s[j:]

    # validate-payload: check on the value in the trace
    traced = nand_block(atom_holds("(has_parameter ?a ?pn ?t1 ?t2)", "(trace_parameter ?a ?pn ?t1 ?t2)"))
    i = s.index("  ;; Move automaton according to the enabled arcs decided in \"validate-payload\"")
    j = s.rindex("\n        )\n      )", 0, i)
    return s[:j] + "\n" + traced + s[j:]


# Section: several instances

def with_multi(s):
    s = """; MULTI: several process instances (the traces of a log) in one problem. They don't interact: every
; instance has its own trace states, automaton states, activities and constraints (the generator renames
; them with _i<k>), so the only things to fix are the few flags that were global for the whole problem.
""" + s
    s = replace(s, "(:types activity automaton_state trace_state parameter_name value_name constraint",
                "(:types activity automaton_state trace_state parameter_name value_name constraint\n          instance ;; MULTI: one per trace of the log\n         ")
    # recovery_finished said "the prefix is over" for the whole problem, now it's per instance
    s = replace(s, "\n    (recovery_finished)\n", """
    ;; MULTI: the prefix of this instance has been replayed (it was one flag for the whole problem)
    (recovery_finished ?i - instance)
    ;; MULTI: which instance an activity / a trace state belongs to
    (activity_of ?a - activity ?i - instance)
    (trace_state_of ?t - trace_state ?i - instance)
    ;; MULTI: the event validate-payload just checked, so sync-actions moves on that one and not on an
    ;; event of another instance (whose arcs were never checked)
    (validated ?t - trace_state)
""")
    # add_action: only once the prefix of that instance is over
    s = replace(s, "\n      (recovery_finished)\n",
                "\n      (exists (?i - instance) (and (activity_of ?a ?i) (recovery_finished ?i))) ;; MULTI\n")
    # validate-payload and skip-unused: only while the prefix of that instance is still going
    s = replace(s, "(not (recovery_finished))",
                "(not (exists (?i - instance) (and (trace_state_of ?t1 ?i) (recovery_finished ?i)))) ;; MULTI", count=2)
    # sync-actions and skip-unused: the end of a trace finishes the prefix of its instance only
    s = replace(s, "(when (final_t_state ?t2) (recovery_finished))",
                "(forall (?i - instance) (when (and (final_t_state ?t2) (trace_state_of ?t2 ?i)) (recovery_finished ?i))) ;; MULTI\n"
                "      (not (validated ?t1)) ;; MULTI", count=2)
    s = replace(s, "\n          (after_sync)\n", "\n          (after_sync)\n          (validated ?t1) ;; MULTI\n")
    s = replace(s, "\n      (after_sync)\n", "\n      (after_sync)\n      (validated ?t1) ;; MULTI: the event that was validated\n")
    return s


def main():
    base = BASE.read_text(encoding="utf-8")  # universal newlines
    variants = {
        "_clauses": with_clauses(base),
        "_multi": with_multi(base),
        "_clauses_multi": with_multi(with_clauses(base)),
    }
    for suffix, text in variants.items():
        target = BASE.with_name(BASE.stem + suffix + ".pddl")
        target.write_text(text, encoding="utf-8", newline="\n")
        print("written", target.relative_to(DOMAINS.parent))


if __name__ == "__main__":
    main()
