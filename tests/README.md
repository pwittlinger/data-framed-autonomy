# Tests

Reusable test cases for the PDDL generation and the planning domains. Each case combines one or two DECLARE models, zero to two Petri nets, a recovery strategy and optionally hard constraints, as listed in [protocol.csv](protocol.csv).

## Layout

```
tests/
  protocol.csv          one row per case: #DECL, #PN, recovery strategy, contains hard constraints
  make_cases.py         builds tests/cases from protocol.csv and the model/log files in this folder
  run_tests.py          generates, solves and evaluates the cases
  cases/
    07_decl1_pn0_reset_hard/
      *.decl, *.pnml, *.xes   copies of the models and the log the case uses
      violation_costs.txt     passed as --violation_cost_file
      test.json               options of the case (models, log, recovery, domain, extra arguments)
      run/                    created by run_tests.py, not tracked by git
  results/              CSV files written by run_tests.py
```

Case folders are named `<protocol row>_decl<#DECL>_pn<#PN>_<strategy>_<hard|soft>`.

- `#DECL` 1 uses `BasePN-0And_7_timed_data_parsed.decl`, 2 adds `BasePN-1And_3_timed_data_parsed.decl`.
- `#PN` 1 uses `BasePN-0And.pnml`, 2 adds `BasePN-1And.pnml`; with 0 the generator runs DECLARE-only.
- All cases use the log `BasePN-0And-prefix-non-conforming-3.xes` (100 prefix traces, so 100 problems per case).
- Hard cases mark `Existence_ActivityJ` (first model) and `Response_ActivityG_ActivityP` (second model) with `x`; the soft cases give the same constraints the cost 1. Change `HARD_CONSTRAINTS` in `make_cases.py` to mark others.
- The domain is chosen by strategy (`domain` in test.json) and read from `../domains`, so the cases always use the current domains.

## Running

Build the generator first (`mvn package` in `pddl_gen`), then from the repository root:

```
python tests/make_cases.py                                  # (re)build the case folders
python tests/run_tests.py                                   # all cases, all problems
python tests/run_tests.py --cases 07 13 --problems 1-3,13   # cases whose name contains 07 or 13, problems 1-3 and 13
python tests/run_tests.py --jobs 4 --timeout 300            # 4 planner runs in parallel, 300 s per problem
python tests/run_tests.py --generate-only                   # only write the problem files
```

Each case is generated in its own `run/` folder (`run/output/pddl/problem<N>.pddl`, `run/generator.log`, planner output in `run/plans/`). The results go to `results/<timestamp>_problems.csv` (status, cost, plan length, times per problem) and `results/<timestamp>_cases.csv` (counts per case). A problem's status is `solved`, `unsolvable`, `timeout` or `error` (the planner failed, e.g. while parsing or grounding).

Other options: `--jar`, `--enhsp`, `--domains`, `--planner` (default `opt-blind`), `--java-mem` (default `4g`).

## Evaluating a new implementation

Run the cases once to get a reference, change the generator or the domains, run them again and compare:

```
python tests/run_tests.py --compare tests/results/<reference>_problems.csv
```

This lists every problem whose status or cost differs from the reference.

## Adding cases

Add a row to `protocol.csv` and run `make_cases.py` again; existing case folders are refreshed, files added to them by hand are kept. A case can also be edited directly: add generator options to `extra_args` in its test.json (e.g. `["--waiting_cost", "1"]`), or put a `cost_model.txt` into its folder, which is then passed with `-c`. Without one, the generator writes a cost model with cost 0 for adding an activity or setting a value.
