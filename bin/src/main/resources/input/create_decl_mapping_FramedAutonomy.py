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


activity_pattern = r"Activity[a-zA-Z]{1,3}"
activation_pattern = r"A\.[a-zA-Z]+\d*"
target_pattern = r"T\.[a-zA-Z]+\d*"

data_conditions = ["|A.integer > 10 |T.integer < 10 |",
"|A.categorical is c1 |T.categorical is c2 |",
"|A.integer > 20 |T.integer < 10 |",
"|A.integer < 20 |",
"|A.integer < 5 |T.categorical is c1 |",
"|A.categorical is c2 |",
"|A.integer > 10 |T.integer > 10 |"]

time_conditions = ["ActivityP,0,100,h/ActivityG,2,5,h",
"ActivityP,0,100,h/ActivityG,2,5,h",
"ActivityP,0,100,h/ActivityG,2,5,h",
"ActivityB,0,100,h",
"ActivityP,0,100,h/ActivityG,2,5,h",
"ActivityB,0,100,h",
"ActivityP,0,100,h/ActivityG,2,5,h"]

#modelTypes = ["data","timed_data", "timed"]

modelTypes = [
    [True, False],
    [True, True],
    [False, True]
]


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

   

    # Iterate through all petri nets in the project folder
    for p_ in pn_f:
        pname = p_[:-5]

        # Check that the DECLARE models exist
        if not (os.path.exists(os.path.join(decl_path, pname))):
            continue
        
        
        declmods = os.listdir(os.path.join(decl_path, pname))
        
        print(declmods)

        for f in declmods:
            # Skip all non-decl files or the ones that have already been processed
            # Avoids potential errors during run-time
            if f[-4:] != "decl" or ("parsed" in f):
                continue

            nConstraints = f[:-5].split("_")[-1] # removes the file extension, spliuts on underscore, and selects the last element


            with open(os.path.join(decl_path,pname, f)) as f_:
                fcontent = f_.read()

            modelString = ""

            act_matches = re.findall(activity_pattern, fcontent)


            conds = dict()


            for i,lcopy in zip(range(int(nConstraints)),fcontent.splitlines()):
                line = lcopy + data_conditions[i]
                am_ = re.findall(activity_pattern, line)
                activation_matches = re.findall(activation_pattern, line)
                target_matches = re.findall(target_pattern, line)

                ind1 = 1
                ind0 = 0

                if "Precedence" in line:
                    ind0 = 1
                    ind1 = 0

                print(f, am_)

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
            #modelString += fcontent



            fname = f[:-5]
            fnamecopy = fname

            for mType in modelTypes:
                bData = mType[0]
                bTime = mType[1]
                modelStringc = modelString
                
                fnamecopy = fname

                if bTime:
                    fnamecopy += "_timed"
                if bData:
                    fnamecopy+= "_data"

                print(fname)
               

                for i,line in zip(range(int(nConstraints)),fcontent.splitlines()):
                    
                    if bData and (not bTime):
                        line+= data_conditions[i]

                    if (not bData) and bTime:
                        if (len(time_conditions[i].split("/"))>1): # Unary constraint
                            line += "| | | "+time_conditions[i]
                        else:
                            line += "| | "+time_conditions[i]
                    if bData and bTime:
                        line += data_conditions[i]+" "+time_conditions[i]

                    modelStringc += line+"\n"

                    
                with open(os.path.join(decl_path,pname, fnamecopy+"_parsed.decl"), "w") as fw_:
                    fw_.write(modelStringc)

            
            #with open(os.path.join(decl_path,pname, fname+"_parsed.decl"), "w") as fw_:
            #    fw_.write(modelString)



        