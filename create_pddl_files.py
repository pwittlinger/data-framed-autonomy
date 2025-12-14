import os
import sys
import subprocess
import time
import pm4py





input_path = r"C:\Users\paulw\Desktop\data-framed-autonomy\numeric-PDDL_generator\pddl_gen\src\main\resources\input"
output_path = r"C:\Users\paulw\Desktop\data-framed-autonomy\numeric-PDDL_generator\pddl_gen\src\main\resources\output\pddl"
cost_model = "cost_model.txt"
variable_values = "variable_values.txt"
jar_path = r"C:\Users\paulw\Desktop\data-framed-autonomy\numeric-PDDL_generator\pddl_gen\target\pddl_gen-1.0-SNAPSHOT-launcher.jar"


#cmd = r'java -jar '


if __name__ == "__main__":

    decl_path = os.path.join(input_path, "declare")
    pn_path = os.path.join(input_path, "petrinet")
    log_path = os.path.join(input_path, "logs")

    decl_f = os.listdir(decl_path)
    pn_f = os.listdir(pn_path)

    decl_f = [f for f in decl_f if f[-4:] == "decl"]
    pn_f = [f for f in pn_f if f[-4:] == "pnml"]

    pns = dict()
    logs = []

    # Create all the cost models for each transition:
    # change: 1
    # addition: 2 
    # add_param: 1
    # delete: 2
    # 
    if "cost_models" not in os.listdir(input_path):
        os.mkdir(os.path.join(input_path, "cost_models"))    
        for pn in pn_f:
            pm = pm4py.read_pnml(os.path.join(pn_path, pn))
            pnname = pn[:-5]
            with open(os.path.join(input_path, "cost_models", f"cost_model-{pnname}.txt"), "w+") as f:
                f.write("\n".join([t.label+" 1 2 1 2" for t in pm[0].transitions]))

    for p_ in pn_f:
        pname = p_[:-5]

        # Checking that a folder for the declare model exists with the same name as the petri net
        if not (os.path.exists(os.path.join(decl_path, pname))):
            continue
        
        declmods = os.listdir(os.path.join(decl_path, pname))

        # Does log exists?
        if not (os.path.exists(os.path.join(log_path, f"{pname}.xes"))):
            continue   

        for f in declmods:
            
            # Checking for the extension of the files (only .decl)
            # And ensuring that only "parsed" (instantiated) models are taken
            if f[-4:] != "decl" or ("parsed" not in f):
                continue

            #print(pname, f[-4:])

            var_sub_file = f.split("_parsed")[0]+".decl.txt"
            var_sub_file = var_sub_file.replace("_timed", "").replace("_data", "")
            var_sub_file = f"variable_substitutions_{var_sub_file}"


            decl_loc = f"declare\\{pname}\\{f}"
            pn_loc = f"petrinet\\{p_}"
            log_loc = f"logs\\{pname}.xes"
            var_sub_loc = f"variable_subs/{var_sub_file}"


            all_logs = [
                #log_loc,

                f"logs/{pname}-prefix-conforming-0.xes",
                f"logs/{pname}-prefix-conforming-1.xes",
                f"logs/{pname}-prefix-conforming-3.xes",
                f"logs/{pname}-prefix-conforming-4.xes",
                
                f"logs/{pname}-prefix-non-conforming-1.xes",
                f"logs/{pname}-prefix-non-conforming-3.xes",
                f"logs/{pname}-prefix-non-conforming-4.xes",
   

            ]


            for l in all_logs:
                #new_folder_name = f.replace("_parsed.decl", "").replace("_", "-d")
                f2 = f
                #if "BasePN" in f:
                #    f2 = f[f.find("_"):]
                
                if (len(l.split("/"))>1):
                    new_folder_name = l.split("/")[1]
                    #new_folder_name = (new_folder_name+"-d"+(f.replace("_parsed.decl", "").replace("_", "-d").split("-d")[1])).replace(".xes", "")
                    new_folder_name = (new_folder_name.replace(".xes", "")+"-"+(f2.replace("_parsed.decl", "").replace(".xes", "")))
                else:
                    new_folder_name = f.replace("_parsed.decl", "").replace("_", "-d")

                new_folder_path = os.path.join(output_path, new_folder_name)
                print(pname, f[-4:], new_folder_path)

                
                
                if not (os.path.exists(new_folder_path)):
                    os.mkdir(new_folder_path)
                    time.sleep(5)
                else:
                    continue

                cost_model = f"cost_models/cost_model-{pname}.txt"
                
                subprocess.call(['java', '-jar', jar_path, decl_loc, pn_loc, l, variable_values, var_sub_loc, cost_model])


                #output_path = r"C:\Users\paulw\Desktop\numeric-PDDL_generator\pddl_gen\src\main\resources\output\pddl"

                pddl_files = os.listdir(output_path)

                pddl_files = [pf for pf in pddl_files if pf[-4:] == "pddl" and "problem" in pf]

                [os.rename(os.path.join(output_path, pf), os.path.join(new_folder_path, pf)) for pf in pddl_files]
                
           


            
            print("-"*100)

        