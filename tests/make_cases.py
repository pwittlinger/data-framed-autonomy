"""Builds one self-contained test case folder per row of protocol.csv under tests/cases.

    python tests/make_cases.py

Each case folder gets copies of the models and the log it uses, a violation_costs.txt and a
test.json with the generator options. Running it again refreshes these files; anything else in a
case folder (e.g. its run/ folder or an added cost_model.txt) is left alone.
"""
import csv
import json
import os
import shutil

TESTS_DIR = os.path.dirname(os.path.abspath(__file__))
CASES_DIR = os.path.join(TESTS_DIR, "cases")

# Models taken for #DECL = 1 and 2 and for #PN = 1 and 2, in this order
DECLARE_MODELS = ["BasePN-0And_7_timed_data_parsed.decl", "BasePN-1And_3_timed_data_parsed.decl"]
PETRI_NETS = ["BasePN-0And.pnml", "BasePN-1And.pnml"]
LOG = "BasePN-0And-prefix-non-conforming-3.xes"

# Constraints marked x (hard) in the cases with hard constraints, per DECLARE model.
# The cases without hard constraints list the same constraints with SOFT_COST instead.
HARD_CONSTRAINTS = {
    "BasePN-0And_7_timed_data_parsed.decl": ["Existence_ActivityJ"],
    "BasePN-1And_3_timed_data_parsed.decl": ["Response_ActivityG_ActivityP"],
}
SOFT_COST = 1

# Domain used per recovery strategy, looked up in the domains folder by run_tests.py
DOMAINS = {
    "No-Recovery": "domain_framed_autonomy_process_no-recovery_time_costs.pddl",
    "Reset": "domain_framed_autonomy_process_reset_time_costs.pddl",
    "Discard": "domain_framed_autonomy_process_discard_time_costs.pddl",
    "Skip": "domain_framed_autonomy_process_skip_time_costs.pddl",
}


def read_protocol():
    with open(os.path.join(TESTS_DIR, "protocol.csv"), newline="") as f:
        return [row for row in csv.DictReader(f) if row["decl"].strip()]


def make_case(number, row):
    num_decl, num_pn = int(row["decl"]), int(row["pn"])
    recovery = row["recovery"].strip()
    hard = row["hard"].strip().lower() == "yes"

    name = f"{number:02d}_decl{num_decl}_pn{num_pn}_{recovery.lower()}_{'hard' if hard else 'soft'}"
    case_dir = os.path.join(CASES_DIR, name)
    os.makedirs(case_dir, exist_ok=True)

    declare_models = DECLARE_MODELS[:num_decl]
    petri_nets = PETRI_NETS[:num_pn]
    for file_name in declare_models + petri_nets + [LOG]:
        shutil.copyfile(os.path.join(TESTS_DIR, file_name), os.path.join(case_dir, file_name))

    with open(os.path.join(case_dir, "violation_costs.txt"), "w", newline="\n") as f:
        for model in declare_models:
            for constraint in HARD_CONSTRAINTS.get(model, []):
                f.write(f"{model}|{constraint} {'x' if hard else SOFT_COST}\n")

    config = {
        "protocol_row": number,
        "declare": declare_models,
        "petri_nets": petri_nets,
        "log": LOG,
        "recovery": recovery,
        "hard_constraints": hard,
        "violation_cost_file": "violation_costs.txt",
        "domain": DOMAINS[recovery],
        "extra_args": [],
    }
    with open(os.path.join(case_dir, "test.json"), "w", newline="\n") as f:
        json.dump(config, f, indent=2)
        f.write("\n")
    return name


if __name__ == "__main__":
    names = [make_case(number, row) for number, row in enumerate(read_protocol(), start=1)]
    print(f"{len(names)} test cases in {CASES_DIR}")

    unknown = sorted(set(os.listdir(CASES_DIR)) - set(names)) if os.path.isdir(CASES_DIR) else []
    if unknown:
        print("Folders not in protocol.csv (left untouched): " + ", ".join(unknown))
