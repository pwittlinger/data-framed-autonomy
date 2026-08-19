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
