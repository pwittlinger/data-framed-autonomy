"""Runs the test cases in tests/cases: generates the PDDL problems with the jar, solves each problem
with ENHSP and the case's domain, and writes the outcome to tests/results.

    python tests/run_tests.py                                    all cases, all problems
    python tests/run_tests.py --cases 07 13 --problems 1-3,13   cases whose name contains 07 or 13
    python tests/run_tests.py --compare tests/results/<earlier run>_problems.csv

Every case is run in its own run/ folder (generator output, side files and planner logs), which is
cleared at the start of the case.
"""
import argparse
import csv
import json
import os
import re
import shutil
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.dirname(TESTS_DIR)
CASES_DIR = os.path.join(TESTS_DIR, "cases")
RESULTS_DIR = os.path.join(TESTS_DIR, "results")

PROBLEM_FIELDS = ["case", "problem", "status", "cost", "plan_length", "elapsed_time", "planning_ms",
                  "expanded_nodes", "wall_s"]
CASE_FIELDS = ["case", "declare", "petri_nets", "recovery", "hard_constraints", "generator_exit", "problems",
               "solved", "unsolvable", "timeout", "error", "mean_cost"]


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--cases", nargs="*", help="only cases whose folder name contains one of these strings")
    parser.add_argument("--problems", help="only these problem numbers, e.g. 1-5,13 (default: all)")
    parser.add_argument("--jar", default=os.path.join(REPO_DIR, "pddl_gen", "target", "pddl_gen-1.0-SNAPSHOT-launcher.jar"))
    parser.add_argument("--enhsp", default=os.path.join(REPO_DIR, "enhsp.jar"))
    parser.add_argument("--domains", default=os.path.join(REPO_DIR, "domains"), help="folder with the domain files")
    parser.add_argument("--planner", default="opt-blind", help="ENHSP planner configuration (default opt-blind)")
    parser.add_argument("--timeout", type=float, default=120, help="seconds per problem (default 120)")
    parser.add_argument("--java-mem", default="4g", help="maximum heap of each planner run (default 4g)")
    parser.add_argument("--jobs", type=int, default=1, help="planner runs in parallel (default 1)")
    parser.add_argument("--generate-only", action="store_true", help="only generate the problem files")
    parser.add_argument("--compare", help="problems CSV of an earlier run, lists the problems whose outcome changed")
    return parser.parse_args()


def problem_selection(spec):
    if not spec:
        return None
    numbers = set()
    for part in spec.split(","):
        start, _, end = part.strip().partition("-")
        numbers.update(range(int(start), int(end or start) + 1))
    return numbers


def load_cases(filters):
    names = sorted(n for n in os.listdir(CASES_DIR) if os.path.isfile(os.path.join(CASES_DIR, n, "test.json")))
    if filters:
        names = [n for n in names if any(f in n for f in filters)]
    cases = []
    for name in names:
        with open(os.path.join(CASES_DIR, name, "test.json")) as f:
            cases.append((name, json.load(f)))
    return cases


def generate(name, config, args):
    case_dir = os.path.join(CASES_DIR, name)
    run_dir = os.path.join(case_dir, "run")
    shutil.rmtree(run_dir, ignore_errors=True)
    os.makedirs(os.path.join(run_dir, "plans"))

    def in_case(file_name):
        return os.path.join(case_dir, file_name)

    cmd = ["java", "-jar", args.jar,
           "-d", ",".join(in_case(f) for f in config["declare"]),
           "-l", in_case(config["log"]),
           "--recovery", config["recovery"]]
    if config["petri_nets"]:
        cmd += ["-p", ",".join(in_case(f) for f in config["petri_nets"])]
    if config.get("violation_cost_file"):
        cmd += ["--violation_cost_file", in_case(config["violation_cost_file"])]
    # Optional, without it the generator writes a cost model with 0 for every activity
    if os.path.isfile(in_case("cost_model.txt")):
        cmd += ["-c", in_case("cost_model.txt")]
    cmd += config.get("extra_args", [])

    # The generator writes its output and side files to the working directory
    with open(os.path.join(run_dir, "generator.log"), "w") as log:
        log.write(" ".join(cmd) + "\n\n")
        log.flush()
        exit_code = subprocess.run(cmd, cwd=run_dir, stdout=log, stderr=subprocess.STDOUT).returncode

    pddl_dir = os.path.join(run_dir, "output", "pddl")
    problems = []
    if os.path.isdir(pddl_dir):
        for file_name in os.listdir(pddl_dir):
            match = re.fullmatch(r"problem(\d+)\.pddl", file_name)
            if match:
                problems.append((int(match.group(1)), os.path.join(pddl_dir, file_name)))
    return exit_code, sorted(problems)


def first_number(pattern, text):
    match = re.search(pattern, text)
    return match.group(1) if match else ""


def solve(name, config, number, problem_file, args):
    domain = os.path.join(args.domains, config["domain"])
    cmd = ["java", f"-Xmx{args.java_mem}", "-jar", args.enhsp, "-o", domain, "-f", problem_file, "-planner", args.planner]
    start = time.time()
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=args.timeout)
        output, timed_out = proc.stdout + proc.stderr, False
    except subprocess.TimeoutExpired as e:
        # The partial output is bytes on some platforms and str on others (Windows), whatever text=True says
        output, timed_out = e.stdout or "", True
        if isinstance(output, bytes):
            output = output.decode(errors="replace")
    wall = time.time() - start

    with open(os.path.join(CASES_DIR, name, "run", "plans", f"problem{number}.txt"), "w") as f:
        f.write(output)

    if timed_out:
        status = "timeout"
    elif "Problem Solved" in output:
        status = "solved"
    elif "Exception" in output:
        # ENHSP also prints "Unsolvable Problem" when parsing or grounding fails
        status = "error"
    elif "Problem unsolvable" in output:
        status = "unsolvable"
    else:
        status = "error"

    return {
        "case": name,
        "problem": number,
        "status": status,
        "cost": first_number(r"Metric \(Search\):(-?[\d.]+)", output) if status == "solved" else "",
        "plan_length": first_number(r"Plan-Length:(\d+)", output),
        "elapsed_time": first_number(r"Elapsed Time: ([\d.]+)", output),
        "planning_ms": first_number(r"Planning Time \(msec\): (\d+)", output),
        "expanded_nodes": first_number(r"Expanded Nodes:(\d+)", output),
        "wall_s": f"{wall:.1f}",
    }


def summarize(name, config, exit_code, rows):
    costs = [float(r["cost"]) for r in rows if r["status"] == "solved" and r["cost"]]
    summary = {
        "case": name,
        "declare": len(config["declare"]),
        "petri_nets": len(config["petri_nets"]),
        "recovery": config["recovery"],
        "hard_constraints": "Yes" if config["hard_constraints"] else "No",
        "generator_exit": exit_code,
        "problems": len(rows),
        "mean_cost": f"{sum(costs) / len(costs):.2f}" if costs else "",
    }
    for status in ["solved", "unsolvable", "timeout", "error"]:
        summary[status] = sum(1 for r in rows if r["status"] == status)
    return summary


def write_csv(path, fields, rows):
    with open(path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)


def compare(earlier_file, rows):
    with open(earlier_file, newline="") as f:
        earlier = {(r["case"], r["problem"]): r for r in csv.DictReader(f)}
    changed = 0
    for row in rows:
        before = earlier.get((row["case"], str(row["problem"])))
        if before is None:
            continue
        if before["status"] != row["status"] or before["cost"] != row["cost"]:
            changed += 1
            print(f"  {row['case']} problem{row['problem']}: {before['status']} {before['cost']}"
                  f" -> {row['status']} {row['cost']}")
    compared = sum(1 for r in rows if (r["case"], str(r["problem"])) in earlier)
    print(f"{changed} of {compared} compared problems changed")


def main():
    args = parse_args()
    selected = problem_selection(args.problems)
    cases = load_cases(args.cases)
    if not cases:
        print(f"No test cases found in {CASES_DIR}, run make_cases.py first")
        return

    # Generate sequentially, then solve all problems of all cases with the requested parallelism
    tasks, exit_codes = [], {}
    for name, config in cases:
        exit_code, problems = generate(name, config, args)
        exit_codes[name] = exit_code
        if selected is not None:
            problems = [p for p in problems if p[0] in selected]
        print(f"{name}: generator exit {exit_code}, {len(problems)} problems")
        tasks += [(name, config, number, problem_file) for number, problem_file in problems]
    if args.generate_only:
        return

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        rows = list(pool.map(lambda t: solve(*t, args), tasks))

    summaries = [summarize(name, config, exit_codes[name], [r for r in rows if r["case"] == name])
                 for name, config in cases]

    os.makedirs(RESULTS_DIR, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    write_csv(os.path.join(RESULTS_DIR, f"{stamp}_problems.csv"), PROBLEM_FIELDS, rows)
    write_csv(os.path.join(RESULTS_DIR, f"{stamp}_cases.csv"), CASE_FIELDS, summaries)

    print()
    print(f"{'case':<36} {'gen':>3} {'probs':>5} {'solved':>6} {'unsolv':>6} {'tmout':>5} {'error':>5} {'mean cost':>9}")
    for s in summaries:
        print(f"{s['case']:<36} {s['generator_exit']:>3} {s['problems']:>5} {s['solved']:>6} {s['unsolvable']:>6}"
              f" {s['timeout']:>5} {s['error']:>5} {s['mean_cost']:>9}")
    print(f"\nResults: {os.path.join(RESULTS_DIR, stamp)}_problems.csv / _cases.csv")

    if args.compare:
        print(f"\nCompared with {args.compare}:")
        compare(args.compare, rows)


if __name__ == "__main__":
    main()
