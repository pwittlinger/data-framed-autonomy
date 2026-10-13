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

The violation cost options only take effect together with a Petri net (-p). The violation cost file has one line per constraint. A DECLARE constraint is identified by the file name of its DECLARE model and its name in the PDDL files (template, then activation, then target activity). For Precedence-type templates the activation is the second activity in the .decl file, so `Precedence[ActivityF, ActivityD]` is `Precedence_ActivityD_ActivityF`. A Petri net is identified by its file name:

```
<DECL_FILE_NAME>|<CONSTRAINT_IDENTIFIER> <violation_cost>
<PNML_FILE_NAME> <violation_cost>
```

```
BasePN-0And_3_timed_data_parsed.decl|Response_ActivityG_ActivityP 4
BasePN-0And_3_timed_data_parsed.decl|Precedence_ActivityD_ActivityF 70
BasePN-0And_3_timed_data_parsed.decl|Not_Response_ActivityM_ActivityO 50
BasePN-0And.pnml 5
```

Costs are whole numbers of at least 0. Constraints that are not listed keep the value of --violation_cost (or 1). If a DECLARE file contains the same constraint more than once, its name sets the cost of every copy, while the numbered name from the PDDL (e.g. `Response_ActivityG_ActivityP_2`) sets the cost of one copy only.

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
