import os
import sys
import itertools
import pm4py
import re
from Declare4Py.ProcessModels.DeclareModel import DeclareModel
from Declare4Py.ProcessMiningTasks.Discovery.DeclareMiner import DeclareMiner
from Declare4Py.D4PyEventLog import D4PyEventLog
from copy import deepcopy
from pm4py.algo.simulation.playout.petri_net import algorithm as simulator
#dir = os.path.dirname(os.path.abspath(__file__))
dir = os.path.abspath(os.path.dirname(sys.argv[0]))





#loggen = r"C:\Users\paulw\OneDrive - Scientific Network South Tyrol\model-interplay-loggen-code\src\main\MainCmd.java"
#variable_vars = r"C:\Users\paulw\Desktop\numeric-PDDL_generator\pddl_gen\src\main\resources\input\variable_values_multi_model.txt"
variable_vars = r"C:\Users\paulw\Desktop\data-framed-autonomy\numeric-PDDL_generator\pddl_gen\src\main\resources\input\variable_values_multi_model.txt"
#cmd = r'java -jar '

activity_pattern = r"Activity[a-zA-Z]{1,3}"
activation_pattern = r"A\.[a-zA-Z]+\d*"
target_pattern = r"T\.[a-zA-Z]+\d*"


if __name__ == "__main__":

    decl_path = os.path.join(dir, "declare")
    pn_path = os.path.join(dir, "petrinet")
    log_path = os.path.join(dir, "logs")

    decl_f = os.listdir(decl_path)
    pn_f = os.listdir(pn_path)

    decl_f = [f for f in decl_f if f[-4:] == "decl"]
    pn_f = [f for f in pn_f if f[-4:] == "pnml"]

    pns = dict()
    logs = []

    #ntraces = [50, 100, 200, 500]


    for p_ in pn_f:
        pname = p_[:-5]

        if not (os.path.exists(os.path.join(decl_path, pname))):
            continue
        
        
        declmods = os.listdir(os.path.join(decl_path, pname))
        #print(p_, p_[:-5])
        print(declmods)

        for f in declmods:
            if f[-4:] != "decl" or ("parsed" in f):
                continue


            with open(os.path.join(decl_path,pname, f)) as f_:
                fcontent = f_.read()

            modelString = ""

            act_matches = re.findall(activity_pattern, fcontent)


            conds = dict()

            for line in fcontent.splitlines():
                am_ = re.findall(activity_pattern, line)
                activation_matches = re.findall(activation_pattern, line)
                target_matches = re.findall(target_pattern, line)

                ind1 = 1
                ind0 = 0

                if "Precedence" in line:
                    ind0 = 1
                    ind1 = 0

                if am_[ind0] not in conds and len(activation_matches)>0:
                    conds[am_[ind0]] = [activation_matches[0].strip().replace("A.", "")]
                elif (am_[ind0] in conds) and len(activation_matches)>0:
                    conds[am_[ind0]].append(activation_matches[0].strip().replace("A.", ""))

                if (len(am_)>1) and am_[ind1] not in conds  and (len(target_matches)>0):
                    conds[am_[ind1]] = [target_matches[0].strip().replace("T.", "")]
                elif (len(am_)>1) and (am_[ind1] in conds) and (len(target_matches)>0):
                    conds[am_[ind1]].append(target_matches[0].strip().replace("T.", ""))

                #print(line)

            for a_ in set(act_matches):
                modelString += f"activity {a_}\n"
                if a_ in conds:
                    vars = set(conds[a_])
                    s_ = ", ".join(list(vars))
                    modelString += f"bind {a_}: {s_} \n"
                
                
                #print(f"activity {a_}")
                
            modelString += "integer: integer between 0 and 100 \n"
            modelString += "categorical: c1, c2, c3\n"
            modelString += fcontent

            fname = f[:-5]

            #with open(os.path.join(decl_path,pname, fname+"_parsed.decl"), "w") as fw_:
            #    fw_.write(modelString)


            with open(variable_vars) as vf_:
                vc_ = vf_.read()

            #print(vc_)
            
            mapping_string = ""

            for a_ in conds:
                vars = set(conds[a_])
                for v in vars:
                    if "integer" in v:
                        mapping_string += f"v0 {a_} integer\n"
                        mapping_string += f"v5 {a_} integer\n"
                        mapping_string += f"v15 {a_} integer\n"
                        mapping_string += f"v30 {a_} integer\n"

                    if "categorical" in v:
                        mapping_string += f"c1 {a_} categorical\n"
                        mapping_string += f"c2 {a_} categorical\n"
                        mapping_string += f"c3 {a_} categorical\n"
                mapping_string += "\n"

            print(mapping_string)

            with open(os.path.join(dir, "variable_subs", f"variable_substitutions_{f}.txt"), "w") as write_file_mapping:
                write_file_mapping.write(mapping_string)
            #print(modelString)
                
            #print(conds)
            #print(act_matches, activation_matches, target_matches)

        
        #print(fcontent)

        #break


        #print(plogs)


        