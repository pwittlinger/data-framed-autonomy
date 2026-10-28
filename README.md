# About

"Framed Autonomy" in AI-Augmented Business Process Management Systems refers to the systems capability to autonomously and independently select which actions to perform.
This work builds on the ideas presented in [Acitelli et. al (2025)](https://doi.org/10.1016/j.is.2025.102573), and extends them by considering a multi-perspective extension of DECLARE process models.

## Highlights
We extended the DECLARE constraints to handle the following MP-DECLARE conditions:
- Variable to Constant activation constraints
- Variable to Constant target constraints
- Temporal constraints

### Inputs:

Required Inputs: 
- -d,--declare <arg>     Path to (MP-)DECLARE model(s), comma-separated for
                        multiple
- -l,--log <arg>         Input event log path

Optional Inputs:

 - -a,--varAssign <arg>   Path to the variable mapping (which activity in
                        frame is associated with which variable value)
 - -c,--cost <arg>        Path to cost model file (contains cost for each
                        activity in the process frame)
 
 - -i,--multiinstance     Flatten the Eventlog into a single PDDL file
 
 - -o,--output <arg>      Path to the output directory (default in current
                        path)
 - -p,--petri <arg>       Path to Petri-net model(s), comma-separated for
                        multiple
 - -s,--varSub <arg>      Path to the variable substitution
 - --violation_cost <arg>  Violation cost of every constraint (default 1)
 - --violation_cost_file <arg>  Path to the violation cost file, which sets
                        the violation cost of individual constraints
                        or marks them as hard (x)
 - --recovery <arg>       Recovery strategy for violated constraints:
                        Reset (default), No-Recovery, Discard or Skip
 - -w,--waiting_cost <arg>  Waiting cost per time unit, a positive number
                        (default 10)
 - --ignore_time_conditions  Ignore all time conditions: the constraints
                        get no time windows and every event of the
                        trace is at timestamp 0.0

The violation cost and waiting cost options apply to DECLARE-only runs and to runs with Petri nets (-p) alike. As the waiting process advances the timestamp by 0.1 per step, waiting one time unit costs 10. A different waiting cost is applied by scaling all other action costs instead: the problem files get the factor `--waiting_cost / 10` as `waiting_cost`, by which the domain divides the other costs. `--waiting_cost 1` is written as `(= (waiting_cost) 0.1)`, the default as `(= (waiting_cost) 1)`. The cost values in the problem files themselves are not scaled. The violation cost file has one line per constraint. A DECLARE constraint is identified by the file name of its DECLARE model and its name in the PDDL files (template, then activation, then target activity). For Precedence-type templates the activation is the second activity in the .decl file, so `Precedence[ActivityF, ActivityD]` is `Precedence_ActivityD_ActivityF`. A Petri net is identified by its file name:

```
<DECL_FILE_NAME>|<CONSTRAINT_IDENTIFIER> <violation_cost>
<PNML_FILE_NAME> <violation_cost>
```

```
BasePN-0And_3_timed_data_parsed.decl|Response_ActivityG_ActivityP x
BasePN-0And_3_timed_data_parsed.decl|Precedence_ActivityD_ActivityF 70
BasePN-0And_3_timed_data_parsed.decl|Not_Response_ActivityM_ActivityO 50
BasePN-0And.pnml 5
```

Costs are whole numbers of at least 0. Constraints that are not listed keep the value of --violation_cost (or 1). If a DECLARE file contains the same constraint more than once, its name sets the cost of every copy, while the numbered name from the PDDL (e.g. `Response_ActivityG_ActivityP_2`) sets the cost of one copy only.

A cost of `x` (or `X`) marks the constraint as hard: the recovery strategy does not apply to it, as if it had the strategy No-Recovery. Its failure states stay non-accepting and cannot be left, so violating it makes the problem unsolvable, while all other constraints still follow --recovery. Petri nets can be marked as hard in the same way. A hard constraint keeps its violation cost (from --violation_cost, or an earlier line), which is never charged.

The recovery strategy decides what happens once a constraint is permanently violated, i.e. its automaton reaches a failure state:

- Reset (default): the constraint is reset to its initial state and can be violated again later.
- No-Recovery: the failure state is non-accepting and cannot be left, so a violation makes the problem unsolvable.
- Discard: the constraint is ignored after its violation, so its failure states also become accepting.
- Skip: events that would violate the constraint are skipped for it, it stays in the state before the violation.

In the problem files only Discard differs: its failure states are additionally declared as `goal_state` and added to the goal condition (they remain `failure_state`). Reset, No-Recovery and Skip produce the same problem files and are told apart by the domain file.

Hard constraints are handled in the problem files only, the domain files stay the same:

- The states of a hard constraint get no `initial_state`, so the reset action has no state to move it back to.
- With Discard, the failure states of a hard constraint are not added to the accepting states.
- With Skip, the failure states of a hard constraint are not declared as `failure_state`, because the Skip domain does not move an automaton into a `failure_state`. The automaton then moves into the failure state as into any other state and stays there, as it is non-accepting and has no outgoing arcs.

With --ignore_time_conditions the time conditions of the DECLARE constraints are ignored, in DECLARE-only runs and in runs with Petri nets alike. The constraints lose their time windows before their automata are built, so:

- The problem files contain no `clock`, `start_clock`, `min_t_condition` or `max_t_condition`.
- The automata lose the states and arcs that only the time windows need, e.g. Precedence has 3 states instead of 4.
- Every event of the trace gets the timestamp 0.0, e.g. `(= (timestamp t0 t1) 0.0)`. The domains still compare `current_timestamp` with the timestamp of each event, so the problems are solved with the same domain files. Time never advances, so no waiting costs are charged.

The option can also be used with models that have no time conditions. The log still needs a `time:timestamp` on every event.

 Example:

```
 java -jar target/pddl_gen-1.0-SNAPSHOT.jar -d declare/BasePN-0And/BasePN-0And_1_timed_data_parsed.decl -p petrinet/BasePN-0And.pnml -l logs/BasePN-0AND.xes
```

This will create the PDDL files under the folder "output/pddl" in the root directory of the project.

If you provide a relative path for the input variables, the folder structure of this project will be used. If you instead provide an absolute path, you can refer to files anywhere on your file system.

## Experiments

We provide a set of example process specifciations in order to try the tool yourself.
All input files are located under "pddl_gen\src\main\resources\input".

Make sure to add the compiled AI-planner [ENHSP](https://github.com/hstairs/enhsp/tree/enhsp-20) into the root directory of this project.

If you are using a Windows machine, make sure to install and setup Windows Subsystem for Linux (WSL). If it is your first time using WSL, your machine will require a restart.

To generate the PDDL files, go into the root directory of the project and execute

```
python create_pddl_files.py
```

You can execute the script "run_planner_benchmark.sh" (Linux) or "run_planner_benchmark_wsl.sh" (Windows) to run the set of experiments on the newly created files.
