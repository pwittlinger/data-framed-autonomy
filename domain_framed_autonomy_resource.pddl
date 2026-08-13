(define (domain trace-alignment)

  (:requirements :strips :typing :equality :adl :fluents :action-costs)

  (:types activity automaton_state trace_state parameter_name value_name constraint resource)


  (:predicates 
    ;; TRACES AND AUTOMATONS
    (trace ?t1 - trace_state ?a - activity ?t2 - trace_state)
    (automaton ?s1 - automaton_state ?a - activity ?s2 - automaton_state)
    (cur_t_state ?t - trace_state)
    (cur_s_state ?s - automaton_state)
    (goal_state ?s - automaton_state)
    (final_t_state ?t - trace_state)
    (violated ?c - constraint)
    (initial_state ?s1 - automaton_state) 
    
    (associated ?s1 - automaton_state ?c - constraint)
    (can_work ?a - activity ?r - resource)

    ;; PARAMETER AND CONSTRAINT DECLARATION
    (has_parameter ?a - activity ?pn - parameter_name ?t1 - trace_state ?t2 - trace_state)
    (has_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)

    ; Predicates to keep track of planner progress
    (invalid ?s1 - automaton_state ?a - activity ?s2 - automaton_state)
    (complete_sync ?a - activity)
    (after_sync)
    (after_add)
    (after_add_check)

    (recovery_finished)

    ; Declare this to indicate that such activity-parameter-value assignment exists.
    (has_substitution_value ?vn - value_name ?a - activity ?pn - parameter_name)
    ; Indicates that the new activity has a new (defined) parameter.
    (has_added_parameter_aut ?a - activity ?par - parameter_name ?s1 - automaton_state)

    ; Used in the problem definition to indicate that this state must not be reached. In that case, the trace is **automatically** failed.
    (failure_state ?s - automaton_state)
    ; Used to indicate that the trace alignment couldn't possibly complete: prune -> less branching -> heap won't kaboom.
    (failure)

  )

  (:functions
    (total_cost)

    ; There exists a value connected to the activity that occures between the two trace states.
    (trace_parameter ?a - activity ?pn - parameter_name ?t1 - trace_state ?t2 - trace_state)
    (majority_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)
    (minority_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)
    (interval_constraint_lower ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)
    (interval_constraint_higher ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)
    (equality_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)
    (inequality_constraint ?a - activity ?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)

    ;; VARIABLES SUBSTITUTION / ADDITION
    (variable_value ?var - value_name)
    (added_parameter_aut ?a - activity ?par - parameter_name ?s1 - automaton_state)
    ;; Cost functions
    (violation_cost ?c - constraint)
    (activity_cost ?a - activity ?r - resource)

  )


  ;; ADD ACTION / MOVE IN MODEL
  ;; add_action marks an activity A for addition
  ;; move_in_model_move_automata then moves all automata associated to the action A
  ;; --------------------------------------------------------------------------------------------------
    (:action add_action
    :parameters (?a - activity ?r - resource)
    :precondition (and 
      (not (after_add))
      (not (complete_sync ?a))
      (not (after_sync))
      (not (failure))
      (not (after_add_check))
      (recovery_finished)
      (can_work ?a ?r)

      (not (exists (?c - constraint) 
        (violated ?c)
      ))


      (exists (?s1 - automaton_state ?s2 - automaton_state) 
        (and
        
          (cur_s_state ?s1)
          (not (goal_state ?s1))
          (not (failure_state ?s1))
          
          (automaton ?s1 ?a ?s2)
          (not (failure_state ?s2))
        )
        )

      )
    :effect (and 
      (increase (total_cost) (activity_cost ?a ?r))
      (after_add)
      (complete_sync ?a)
  ))

  (:action add_parameter
      :parameters (?a - activity ?pn - parameter_name ?vn - value_name)
      :precondition (and 
      (complete_sync ?a)
      (after_add)
      (has_substitution_value ?vn ?a ?pn)


      
      ;(not (after_sync))
      ;(not (failure))
      (not (after_add_check))
      ;(not (after_change))
      (exists (?s1 ?s2 - automaton_state) 
      (and
        (cur_s_state ?s1)
        (not (goal_state ?s1))
        (automaton ?s1 ?a ?s2)
        (has_constraint ?a ?pn ?s1 ?s2)
        (not (has_added_parameter_aut ?a ?pn ?s1))
        (not (failure_state ?s2))
        (or
        (> (variable_value ?vn) (majority_constraint ?a ?pn ?s1 ?s2))
        (< (variable_value ?vn) (minority_constraint ?a ?pn ?s1 ?s2))

        (= (variable_value ?vn) (equality_constraint ?a ?pn ?s1 ?s2))
        (> (variable_value ?vn) (inequality_constraint ?a ?pn ?s1 ?s2))
        (< (variable_value ?vn) (inequality_constraint ?a ?pn ?s1 ?s2))

        )
        
      )
      )

      (not (exists (?s5 ?s6 - automaton_state) 
      (and
        (cur_s_state ?s5)
        (automaton ?s5 ?a ?s6)
        (has_constraint ?a ?pn ?s5 ?s6)
        (failure_state ?s6)
        (or
        (> (variable_value ?vn) (majority_constraint ?a ?pn ?s5 ?s6))
        (< (variable_value ?vn) (minority_constraint ?a ?pn ?s5 ?s6))

        (= (variable_value ?vn) (equality_constraint ?a ?pn ?s5 ?s6))
        (> (variable_value ?vn) (inequality_constraint ?a ?pn ?s5 ?s6))
        (< (variable_value ?vn) (inequality_constraint ?a ?pn ?s5 ?s6))

        )
        
      )))
      )

      :effect (and 

      (increase (total_cost) 0)
      (forall (?s3 ?s4 - automaton_state) 
        (when (and 
              (cur_s_state ?s3)
              (automaton ?s3 ?a ?s4)
              (has_constraint ?a ?pn ?s3 ?s4)
              (not (has_added_parameter_aut ?a ?pn ?s3))
              )

              (and 
              (has_added_parameter_aut ?a ?pn ?s3)
              (assign (added_parameter_aut ?a ?pn ?s3) (variable_value ?vn))
              )
        )
      )
      )
  )

  ;; This action validates if the newly added parameter (payload) to the added activity is valid.
  ;; All arcs which are not fulfilling the guards are disabled.
  
  (:action check_added_parameter_model
    :parameters (?a - activity)
    :precondition (and 
        (complete_sync ?a)
        (after_add)
        (not (after_add_check))
      )
    :effect (and 
        (after_add_check)
        (not (after_add))

      (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)
          ;  ; If parameter is missing
            (when (and 
            
              ; validate that the current selection of states is a valid arc that reads the current parameter
              (cur_s_state ?s1)
              (automaton ?s1 ?a ?s2)
              (has_constraint ?a ?pn ?s1 ?s2)
              ;; 
              (or
                ;; If the arc has a guard but the added activity no additional payload -> defaults to invalid
                (not (has_added_parameter_aut ?a ?pn ?s1))
                ;; Or it has a constraint and the guard is NOT FULFILLED
                ;; I.e. Guard [X<10] but the new value X = 5 (added_parameter_aut)
                (and 
                  (has_added_parameter_aut ?a ?pn ?s1)
                  (or 
                    (< (added_parameter_aut ?a ?pn ?s1) (majority_constraint ?a ?pn ?s1 ?s2))
                    (> (added_parameter_aut ?a ?pn ?s1) (minority_constraint ?a ?pn ?s1 ?s2))
                    (< (added_parameter_aut ?a ?pn ?s1) (interval_constraint_lower ?a ?pn ?s1 ?s2))
                    (> (added_parameter_aut ?a ?pn ?s1) (interval_constraint_higher ?a ?pn ?s1 ?s2))
                    (< (added_parameter_aut ?a ?pn ?s1) (equality_constraint ?a ?pn ?s1 ?s2))
                    (> (added_parameter_aut ?a ?pn ?s1) (equality_constraint ?a ?pn ?s1 ?s2))
                    (= (added_parameter_aut ?a ?pn ?s1) (inequality_constraint ?a ?pn ?s1 ?s2))
                  )
                )
              )
              )

            (invalid ?s1 ?a ?s2))
      )

    )
  )
  ;; After validating all arcs move all corresponding arcs
  ;; Can only be executing after validation (check_added_variables)
  (:action add_move_automata
      :parameters (?a - activity)
      :precondition (and 
        (complete_sync ?a)
        (not (after_add))
        (after_add_check)
        
        ;; Only execute if there is an arc that leads to a state that's not a fail state
        ;; and only move if the automata are not already all satisfying
        ;; -> this reduces the amount of possible (non-optimal) solutions.
        (exists (?s1 - automaton_state ?s2 - automaton_state) 
          (and
            (not (goal_state ?s1))
            (not (invalid ?s1 ?a ?s2))
            (cur_s_state ?s1)
            (automaton ?s1 ?a ?s2)
            (not (failure_state ?s2))
          )
        )
      
        )
      :effect (and 
        (increase (total_cost) 0)

        ;(increase (current_timestamp)0.1)
        (not (after_add_check))

      
        ;; Move all automata, starting with the arcs leading to non-fail states
        (forall (?s1 - automaton_state ?s2 - automaton_state)
          (when (and
            (automaton ?s1 ?a ?s2)
            (cur_s_state ?s1)
            (not (failure_state ?s2))
            (not (invalid ?s1 ?a ?s2))
            ) 
            (and
              (not (cur_s_state ?s1))
              (cur_s_state ?s2)
            )
          )
        )

      (forall (?s1 - automaton_state ?s2 - automaton_state ?c - constraint)
        (when (and
          (not (invalid ?s1 ?a ?s2))
          (automaton ?s1 ?a ?s2)
          (cur_s_state ?s1)
          (failure_state ?s2)
          (associated ?s1 ?c)
          (associated ?s2 ?c)
        ) (and
          (not (cur_s_state ?s1))
          (cur_s_state ?s2)
          (failure)
          (violated ?c)
        ))
      )

     

      ;; Reset the flag that a payload is added
      ;; Needed since the same activity could be added multiple times with different values
      ;; Not possible to reset a numeric fluent to undefined, as it HAS to be set at the end of the plan if it gets set at any point
      (forall (?s1 - automaton_state ?pn - parameter_name)
            (when (has_added_parameter_aut ?a ?pn ?s1) (not (has_added_parameter_aut ?a ?pn ?s1)))
      )

      ;; Reset all Arcs so we can insert the same activity multiple times.
      (forall (?s1 - automaton_state ?s2 - automaton_state)
        (when
          (and 
          (automaton ?s1 ?a ?s2)
          (invalid ?s1 ?a ?s2)
          )
          (not (invalid ?s1 ?a ?s2)) )
      )


      (not (complete_sync ?a))
  )
  )

  ;; SYNC OPERATIONS
  ;; ----------------------------------------------------------------------------------------------------
  ;; Action 'validate-payload' checks if the guards are satisfied
  ;; It sets all arcs that are "enabled" to '(not (invalid))',
  ;; and all disabled arcs to "invalid"
  (:action validate-payload
      :parameters (?t1 - trace_state ?a - activity ?t2 - trace_state)
      :precondition (and 
        (cur_t_state ?t1) 
        (trace ?t1 ?a ?t2) 
        (not (after_sync))
        (not (after_add))
        (not (failure))
        (not (complete_sync ?a))
        (not (after_add_check))
        ;(= current_timestamp (timestamp ?t1 ?t2))
        ; has_increased indicates if the waiting time has been increased
        ; only possible when adding, not when synching
        
        ;; I split up the violation flag in two steps
        ;; If I only say "not failure" it might keep a constraint violated if multiple become violated at the same time
        (not (exists (?c - constraint) 
          (violated ?c)
        ))
        
        (not (recovery_finished))

        ;(exists (?s1 - automaton_state ?s2 - automaton_state ) 
        ;  (and
        ;    (cur_s_state ?s1)
        ;    (not (failure_state ?s1))
        ;    (not (goal_state ?s1))
        ;    (automaton ?s1 ?a ?s2)
        ;    (not (invalid ?s1 ?a ?s2))
        ;    ;(not (failure_state ?s2))
        ;  )
        ;)
        
        )
      :effect (and 
          (increase (total_cost) 0)
          (after_sync)
          ;Check if case parameter is missing
          ;; The "nested" when seems to save time as we do not need to iterate 6+ times over all combinations
          (forall (?pn - parameter_name ?s1 - automaton_state ?s2 - automaton_state)
            ;  ; If parameter is missing
            (when 
              (and 
              ;(not (invalid ?s1 ?a ?s2))
                (cur_s_state ?s1)
                (automaton ?s1 ?a ?s2)
                (has_constraint ?a ?pn ?s1 ?s2)
                (or
                  (not (has_parameter ?a ?pn ?t1 ?t2))
                  (< (trace_parameter ?a ?pn ?t1 ?t2) (majority_constraint ?a ?pn ?s1 ?s2))
                  (> (trace_parameter ?a ?pn ?t1 ?t2) (minority_constraint ?a ?pn ?s1 ?s2))
                  (< (trace_parameter ?a ?pn ?t1 ?t2) (interval_constraint_lower ?a ?pn ?s1 ?s2))
                  (> (trace_parameter ?a ?pn ?t1 ?t2) (interval_constraint_higher ?a ?pn ?s1 ?s2))
                  (< (trace_parameter ?a ?pn ?t1 ?t2) (equality_constraint ?a ?pn ?s1 ?s2))
                  (> (trace_parameter ?a ?pn ?t1 ?t2) (equality_constraint ?a ?pn ?s1 ?s2))
                  (= (trace_parameter ?a ?pn ?t1 ?t2) (inequality_constraint ?a ?pn ?s1 ?s2))
                )
              )
              
              (invalid ?s1 ?a ?s2)
            )
          )   

        )
      )
  
  ;; Move automaton according to the enabled arcs decided in "validate-payload"
  (:action sync-actions
    :parameters (?t1 ?t2 - trace_state ?a - activity)
    :precondition (and 
      
      (not (after_add))
      (not (failure))
      (not (after_add_check))
      (not (complete_sync ?a))
      ;(= current_timestamp (timestamp ?t1 ?t2))
      (after_sync)

      (cur_t_state ?t1)
      (trace ?t1 ?a ?t2)     

      ;(exists (?s1 - automaton_state ?s2 - automaton_state) 
      ;  (and
      ;  (cur_s_state ?s1)
      ;  ;(not (goal_state ?s1))
      ;  ;(not (failure_state ?s1))
      ;  (automaton ?s1 ?a ?s2)
      ;  ;(not (invalid ?s1 ?a ?s2))
      ;  ;(not (failure_state ?s2))
      ;  )
      ;)
      
 )

    :effect (and 
      (increase (total_cost) 0)


      (not (cur_t_state ?t1)) 
      (cur_t_state ?t2)
      (when (final_t_state ?t2) (recovery_finished))
      (not (after_sync))
      ;(assign (current_timestamp) (timestamp ?t1 ?t2))
      
      ; Move all enabled automata that are ending in a valid state
      (forall (?s1 - automaton_state ?s2 - automaton_state)
        (when (and
          (not (invalid ?s1 ?a ?s2))
          (automaton ?s1 ?a ?s2)
          (cur_s_state ?s1)
          (not (failure_state ?s2))
        ) (and
          (not (cur_s_state ?s1))
          (cur_s_state ?s2)
        ))
      )

      ; Move all enabled automata that are ending in a fail state
      (forall (?s1 - automaton_state ?s2 - automaton_state ?c - constraint)
        (when (and
          (not (invalid ?s1 ?a ?s2))
          (automaton ?s1 ?a ?s2)
          (cur_s_state ?s1)
          (failure_state ?s2)
          (associated ?s1 ?c)
          (associated ?s2 ?c)
        ) (and
          (not (cur_s_state ?s1))
          (cur_s_state ?s2)
          (failure)
          (violated ?c)
        ))
      )

      (forall (?s1 - automaton_state ?s2 - automaton_state) 
        (when (and 
          (invalid ?s1 ?a ?s2)
          (automaton ?s1 ?a ?s2)
          ) ; Without the when enclosing, it crashes.
          (not (invalid ?s1 ?a ?s2))
        )
      )
    )
  )


  (:action validate-failure
      :parameters (?c - constraint)
      :precondition (and
          ;(failure)
          (not (after_sync))
          (not (after_add))
          (not (after_add_check))

          (exists (?s1 - automaton_state)
          (and
          (cur_s_state ?s1)
          (associated ?s1 ?c)
          (not (goal_state ?s1))
          ;(failure_state ?s1)
          
          )
           )
         )
      :effect (and 
      ;; Allow the reset at any state
      ;; Possibly relevant to abort early

        ;(forall (?s1 - automaton_state) 
        ;(when (and
        ;  (cur_s_state ?s1)
        ;  (associated ?s1 ?c)
        ;  (not (goal_state ?s1))
        ;;(failure_state ?s1)
        ;) 
        ;(violated ?c))
        ;)

        (violated ?c)
      )
  )
  


  (:action reset
      :parameters (?c - constraint)
      :precondition (and 
          (violated ?c)
          ;(failure)
          ;(not (after_sync))
          ;(not (after_add))
          ;(not (after_add_check))
          )
      :effect (and 

      (increase (total_cost) (violation_cost ?c))
      
      (forall (?s1 ?s2 - automaton_state)
        (when (and
            (cur_s_state ?s1)
            (not (goal_state ?s1))
            ;(failure_state ?s1)
            (initial_state ?s2)
            (associated ?s1 ?c)
            (associated ?s2 ?c)
            (not (failure_state ?s2))
            )
            (and
            (not (cur_s_state ?s1))
            (cur_s_state ?s2)
            )  
        )
        )
      (not (failure))
      (not (violated ?c))
      )
  )

)

