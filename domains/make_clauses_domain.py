# Builds domain_framed_autonomy_process_reset_time_clauses.pddl from the normal domain, so when the
# normal domain changes we can just run this again:
#   python domains/make_clauses_domain.py domains/domain_framed_autonomy_process_reset_time.pddl domains/domain_framed_autonomy_process_reset_time_clauses.pddl

import sys
src, dst = sys.argv[1], sys.argv[2]
s = open(src, encoding='utf-8').read()  # universal newlines

def replace_once(old, new):
    global s
    assert s.count(old) == 1, (s.count(old), old[:80])
    s = s.replace(old, new)

header = """; Same as domain_framed_autonomy_process_reset_time.pddl, plus support for data conditions that
; aren't just an AND of comparisons (or, not, ->, ...). The generator turns those conditions into an
; AND of nand clauses (see NandGuardEncoder in pddl_gen) and writes this domain next to the problems
; when a model needs it. Everything below that isn't marked with ;; NAND is unchanged.
"""
s = header + s

replace_once("(:types activity automaton_state trace_state parameter_name value_name constraint)",
             "(:types activity automaton_state trace_state parameter_name value_name constraint\n          guard_clause guard_atom) ;; NAND: the two new types")

replace_once("""heap won't kaboom.
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

replace_once("""    (wait)
""", """    (wait)
    ;; NAND: the value an atom compares against
    (atom_value ?g - guard_atom)
""")

# add_parameter only prunes values with the old encoding, clause guarded arcs are checked later
replace_once("""        (has_constraint ?a ?pn ?s1 ?s2)
        (not (has_added_parameter_aut ?a ?pn ?s1))
        (goal_state ?s2)
        )""", """        (has_constraint ?a ?pn ?s1 ?s2)
        (not (has_added_parameter_aut ?a ?pn ?s1))
        (goal_state ?s2)
        (not (clause_guarded ?s1 ?a ?s2)) ;; NAND: these arcs get checked in check_added_parameter_model
        )""")

def atom_holds(value):
    return f"""(exists (?pn - parameter_name)
                  (and
                    (atom_param ?g ?pn)
                    {value[0]}
                    (or
                      (and (atom_gt ?g) (> {value[1]} (atom_value ?g)))
                      (and (atom_ge ?g) (>= {value[1]} (atom_value ?g)))
                      (and (atom_lt ?g) (< {value[1]} (atom_value ?g)))
                      (and (atom_le ?g) (<= {value[1]} (atom_value ?g)))
                      (and (atom_eq ?g) (= {value[1]} (atom_value ?g)))
                      (and (atom_ne ?g) (or (< {value[1]} (atom_value ?g)) (> {value[1]} (atom_value ?g))))
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

# check_added_parameter_model: same check on the value the planner picked for the added activity
added = nand_block(atom_holds(("(has_added_parameter_aut ?a ?pn ?s1)", "(added_parameter_aut ?a ?pn ?s1)")))
anchor = "  ;; After validating all arcs move all corresponding arcs"
i = s.index(anchor)
j = s.rindex("\n    )\n  )", 0, i)
s = s[:j] + "\n" + added + s[j:]

# validate-payload: check on the value in the trace
traced = nand_block(atom_holds(("(has_parameter ?a ?pn ?t1 ?t2)", "(trace_parameter ?a ?pn ?t1 ?t2)")))
anchor = "  ;; Move automaton according to the enabled arcs decided in \"validate-payload\""
i = s.index(anchor)
j = s.rindex("\n        )\n      )", 0, i)
s = s[:j] + "\n" + traced + s[j:]

open(dst, 'w', encoding='utf-8', newline='\n').write(s)
print("written", dst)
