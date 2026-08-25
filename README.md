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
