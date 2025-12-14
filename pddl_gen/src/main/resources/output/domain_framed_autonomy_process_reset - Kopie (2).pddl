(define (domain trace-alignment)

  (:requirements :strips :typing :equality :adl :fluents :action-costs :time)

  (:types activity automaton_state trace_state parameter_name value_name constraint)

  ; ; Constants for prob
  ; (:constants
  ;   t0 t1 t2 t3 t4 t5 t6 t7 t8 t9 t10 - trace_state
  ;   s10 s11 s20 s21 s30 s31 s40 s41 s50 s51 s60 s61 s62 s70 s71 s72 s73 s80 s81 s82 - automaton_state
  ;   A B C D E F G - activity
  ;   x y z - parameter_name
  ;   a_x20 a_x40 a_y4 a_y6 a_z0 a_z1 c40 c30 c20 d10 d20 d40 e_x20 e_z0 e_z1 - value_name
  ; )

  ; ; Constants for prob2
  ; (:constants
  ;   t0 t1 t2 t3 t4 t5 t6 t7 t8 t9 t10 - trace_state
  ;   s10 s11 s20 s21 s30 s31 s40 s41 s50 s51 s52 s60 s61 s62 s70 s71 s80 s81 s100 s101 - automaton_state
  ;   a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 - activity
  ;   int cat - parameter_name
  ;   int5 int10 int15 cat1 cat2 cat3 - value_name
  ; )

  ;; Majority: >=
  ;; Minority: <=
  ;; Interval: [, ]
  ;; Equality: ==
  ;; Inequality: !=
  ;; If you want only > x, do conditions >= x && != x

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
  
    (clock ?s1 ?s2 - automaton_state)

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
    ;; TIME CONDITIONS
    (timestamp ?t1 ?t2 - trace_state)
    (min_t_condition ?s1 - automaton_state ?a - activity ?s2 - automaton_state)
    (max_t_condition ?s1 - automaton_state ?a - activity ?s2 - automaton_state)
    (start_clock ?c - constraint)
    (current_timestamp)
    (wait)
  )


  ;; ADD ACTION / MOVE IN MODEL
  ;; add_action marks an activity A for addition
  ;; move_in_model_move_automata then moves all automata associated to the action A
  ;; --------------------------------------------------------------------------------------------------
    (:action add_action
    :parameters (?a - activity)
    :precondition (and 
      (not (after_add))
      (not (complete_sync ?a))
      (not (after_sync))
      (not (failure))
      (not (after_add_check))
      (recovery_finished)

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
      (increase (total_cost) 0)
      (after_add)
      (complete_sync ?a)
  ))

  (:action add_parameter
      :parameters (?a - activity ?s1 - automaton_state ?pn - parameter_name ?vn - value_name)
      :precondition (and 
      (complete_sync ?a)
      (after_add)
      (has_substitution_value ?vn ?a ?pn)
      (cur_s_state ?s1)
      
      (not (goal_state ?s1))

      (not (has_added_parameter_aut ?a ?pn ?s1))
      (not (after_add_check))
      ; Only add a parameter if it is necessary
      ; And if it satisfies at least one guard
      (exists (?s2 - automaton_state) 
      (and
    
        (automaton ?s1 ?a ?s2)
        (has_constraint ?a ?pn ?s1 ?s2)
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
      )

      :effect (and 

      (increase (total_cost) 0)
      (has_added_parameter_aut ?a ?pn ?s1)
      (assign (added_parameter_aut ?a ?pn ?s1) (variable_value ?vn)))
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

      (forall (?s1 - automaton_state ?s2 - automaton_state ?c - constraint)
            (when (and
                (cur_s_state ?s1)
                (automaton ?s1 ?a ?s2)
                ;(not (invalid ?s1 ?a ?s2))
                (associated ?s1 ?c)
                (associated ?s2 ?c)
                ;(not (clock ?s1 ?s2))

                (or
                ;(> (- (+(current_timestamp)0.1) (start_clock ?c) ) (max_t_condition ?s1 ?a ?s2))
                ;(< (- (+(current_timestamp)0.1) (start_clock ?c) ) (min_t_condition ?s1 ?a ?s2))
                (< (current_timestamp) (+ (min_t_condition ?s1 ?a ?s2) (start_clock ?c) ))
                (> (current_timestamp) (+ (max_t_condition ?s1 ?a ?s2) (start_clock ?c) )) 
                ;(> (- (current_timestamp) (start_clock ?c)) (max_t_condition ?s1 ?a ?s2))
                ;(< (- (current_timestamp) (start_clock ?c)) (min_t_condition ?s1 ?a ?s2))
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

         (forall (?s1 - automaton_state ?s2 - automaton_state ?c - constraint)
        (when (and
          (not (invalid ?s1 ?a ?s2))
          (automaton ?s1 ?a ?s2)
          (cur_s_state ?s1)
          ;(not (failure_state ?s2))
          (clock ?s1 ?s2)
          (associated ?s1 ?c)
          (associated ?s2 ?c)

          ;(<= (- (current_timestamp) (start_clock ?c)) (max_t_condition ?s1 ?a ?s2))
          ;(>= (- (current_timestamp) (start_clock ?c)) (min_t_condition ?s1 ?a ?s2))

          ;(<= (+(current_timestamp)0.1) (+ (max_t_condition ?s1 ?c ?s2) (start_clock ?c)))
          ;(>= (+(current_timestamp)0.1) (+ (min_t_condition ?s1 ?c ?s2) (start_clock ?c)))

          (<= (current_timestamp) (+ (max_t_condition ?s1 ?a ?s2) (start_clock ?c)))
          (>= (current_timestamp) (+ (min_t_condition ?s1 ?a ?s2) (start_clock ?c)))
          

        ) (and
          ;(assign (start_clock ?c) (+(current_timestamp)0.1))
          (assign (start_clock ?c) (current_timestamp))
        ))
      )

      
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
	  
      ;(forall (?s1 - automaton_state ?s2 - automaton_state)
      ;  (when (and
      ;    (not (invalid ?s1 ?a ?s2))
      ;    (automaton ?s1 ?a ?s2)
      ;    (cur_s_state ?s1)
      ;    (failure_state ?s2)
      ;  ) (and
      ;    (not (cur_s_state ?s1))
      ;    (cur_s_state ?s2)
      ;    (failure)    
      ;  ))
      ;)

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
        (= current_timestamp (timestamp ?t1 ?t2))
        ; has_increased indicates if the waiting time has been increased
        ; only possible when adding, not when synching
        
        ;; I split up the violation flag in two steps
        ;; If I only say "not failure" it might keep a constraint violated if multiple become violated at the same time
        (not (exists (?c - constraint) 
          (violated ?c)
        ))
        
        (not (recovery_finished))

        (exists (?s1 - automaton_state ?s2 - automaton_state ) 
          (and
            (cur_s_state ?s1)
            ;(not (failure_state ?s1))
            ;(not (goal_state ?s1))
            (automaton ?s1 ?a ?s2)
            ;(not (invalid ?s1 ?a ?s2))
            ;(not (failure_state ?s2))
          )
        )
        
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

          (forall (?s1 - automaton_state ?s2 - automaton_state ?c - constraint)
            (when (and
                (cur_s_state ?s1)
                (automaton ?s1 ?a ?s2)
                ;(not (invalid ?s1 ?a ?s2))
                (associated ?s1 ?c)
                (associated ?s2 ?c)
                ;(clock ?s1 ?s2)

                (or

                ;(> (- (current_timestamp) (start_clock ?c)) (max_t_condition ?s1 ?a ?s2))
                ;(< (- (current_timestamp) (start_clock ?c)) (min_t_condition ?s1 ?a ?s2))
                ;(> (- (timestamp ?t1 ?t2) (start_clock ?c) ) (max_t_condition ?s1 ?a ?s2))
                ;(< (- (timestamp ?t1 ?t2) (start_clock ?c) ) (min_t_condition ?s1 ?a ?s2))
                ;(< (+(current_timestamp)0.1) (+ (min_t_condition ?s1 ?a ?s2) (start_clock ?c) ))
                ;(> (+(current_timestamp)0.1) (+ (max_t_condition ?s1 ?a ?s2) (start_clock ?c) )) 
                (< (current_timestamp) (+ (min_t_condition ?s1 ?a ?s2) (start_clock ?c) ))
                (> (current_timestamp) (+ (max_t_condition ?s1 ?a ?s2) (start_clock ?c) )) 
                )
                ) 
            
            (invalid ?s1 ?a ?s2))
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
      (= current_timestamp (timestamp ?t1 ?t2))
      (after_sync)

      (cur_t_state ?t1)
      (trace ?t1 ?a ?t2)     

      (exists (?s1 - automaton_state ?s2 - automaton_state) 
        (and
        (cur_s_state ?s1)
        ;(not (goal_state ?s1))
        ;(not (failure_state ?s1))
        (automaton ?s1 ?a ?s2)
        ;(not (invalid ?s1 ?a ?s2))
        ;(not (failure_state ?s2))
        )
      )
      
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

      ; Handle time conditions
      (forall (?s1 - automaton_state ?s2 - automaton_state ?c - constraint)
        (when (and
          (not (invalid ?s1 ?a ?s2))
          (automaton ?s1 ?a ?s2)
          (cur_s_state ?s1)
          ;(not (failure_state ?s2))
          (clock ?s1 ?s2)
          (associated ?s1 ?c)
          (associated ?s2 ?c)

          ;(<= (-(current_timestamp) (start_clock ?c)) (max_t_condition ?s1 ?a ?s2))
          ;(>= (-(current_timestamp) (start_clock ?c)) (min_t_condition ?s1 ?a ?s2))
          
          ;(<= (timestamp ?t1 ?t2) (+ (start_clock ?c) (max_t_condition ?s1 ?c ?s2)))
          ;(>= (timestamp ?t1 ?t2) (+ (start_clock ?c) (min_t_condition ?s1 ?c ?s2)))

          (<= (current_timestamp) (+ (start_clock ?c) (max_t_condition ?s1 ?a ?s2)))
          (>= (current_timestamp) (+ (start_clock ?c) (min_t_condition ?s1 ?a ?s2)))

        ) (and
          (assign (start_clock ?c) (current_timestamp))
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
        (forall (?s1 - automaton_state) 
        (when (and
          (cur_s_state ?s1)
          (associated ?s1 ?c)
          (not (goal_state ?s1))
        ;(failure_state ?s1)
        ) 
        (violated ?c))
        )
      )
  )
  


  (:action reset
      :parameters (?c - constraint)
      :precondition (and 
          (violated ?c)
          ;(failure)
          (not (after_sync))
          (not (after_add))
          (not (after_add_check))
          )
      :effect (and 

      (increase (total_cost) 1000)
      
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
  
  (:action skip-unused
      :parameters (?t1 - trace_state ?a - activity ?t2 - trace_state)
      :precondition (and 
      ;(= current_timestamp (timestamp ?t1 ?t2))
      (cur_t_state ?t1) 
      (trace ?t1 ?a ?t2)
      (not (recovery_finished))
      (not (exists (?s1 - automaton_state ?s2 - automaton_state) 
        (and
        
        (automaton ?s1 ?a ?s2)
        (not (failure_state ?s2))
        )
      ))
      )
      :effect (and 
      (increase (total_cost) 0)
      (not (cur_t_state ?t1)) 
      (cur_t_state ?t2)
      (when (final_t_state ?t2) (recovery_finished))
      ;(assign (current_timestamp) (timestamp ?t1 ?t2))
      )
  )

   
  (:process waiting
        :parameters ()
        :precondition (and
                (not (after_sync))
                (not (after_add))
                (not (failure))
                
                (not (after_add_check))
                
                (or
                (exists (?s1 ?s2 - automaton_state ?a - activity ?n - constraint) 
                    (and
                        ;(cur_s_state ?s1)
                        (associated ?s1 ?n)
                        (associated ?s2 ?n)
                        (automaton ?s1 ?a ?s2)
                        (not (invalid ?s1 ?a ?s2))
                        ;(not (goal_state ?s1))
                        (not (violated ?n))
                        (not (complete_sync ?a))
                        ;(>= (current_timestamp) (+(min_t_condition ?s1 ?a ?s2) (start_clock ?n)))
                        ;(< (current_timestamp) (+ (min_t_condition ?s1 ?a ?s2) (start_clock ?n)))
                        (<= (current_timestamp) (+ (max_t_condition ?s1 ?a ?s2) (start_clock ?n)))
                        
                    )
                )

                (exists (?t1 ?t2 - trace_state ?a - activity) 
                    (and
                    (< current_timestamp (timestamp ?t1 ?t2))
                    (cur_t_state ?t1)
                    (trace ?t1 ?a ?t2)
                    (not (complete_sync ?a))
                    )
                    )
                )

            
            ) ; or specify a real condition
        :effect (and 
            ; Increase the current timestamp by the fixed progression rate.
            ; This also means the timestamp needs to be rounded to the nearest first digit, otherwise this approach won't work
            ; or rather we need to improve precision (making planning more difficult)
            (increase (current_timestamp) (* #t 0.1))
            )


        
    )

    (:process waiting_add
        :parameters ()
        :precondition (and
                (not (after_sync))
                (not (after_add))
                (not (failure))
                
                (not (after_add_check))
                
                (exists (?s1 ?s2 - automaton_state ?a - activity ?n - constraint) 
                    (and
                        ;(cur_s_state ?s1)
                        (associated ?s1 ?n)
                        (associated ?s2 ?n)
                        (automaton ?s1 ?a ?s2)
                        ;(not (invalid ?s1 ?a ?s2))
                        ;(not (goal_state ?s1))
                        ;(not (violated ?n))
                        ;(not (complete_sync ?a))
                        ;(>= (current_timestamp) (+(min_t_condition ?s1 ?a ?s2) (start_clock ?n)))
                        ;(< (current_timestamp) (+ (min_t_condition ?s1 ?a ?s2) (start_clock ?n)))
                        (<= (current_timestamp) (+(max_t_condition ?s1 ?a ?s2) (start_clock ?n)))
                        
                    )
                )

            
            ) ; or specify a real condition
        :effect (and 
            ; Increase the current timestamp by the fixed progression rate.
            ; This also means the timestamp needs to be rounded to the nearest first digit, otherwise this approach won't work
            ; or rather we need to improve precision (making planning more difficult)
            (increase (current_timestamp) (* #t 0.1))
            )


        
    )
)

