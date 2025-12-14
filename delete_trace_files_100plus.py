import os
inpath = r"C:\Users\paulw\Desktop\data-framed-autonomy\numeric-PDDL_generator\pddl_gen\src\main\resources\output\pddl"

if __name__=="__main__":
        
    fpaths = [folder.replace("dparsed", "").replace(".decl", "") for folder in os.listdir(inpath) if os.path.isdir(os.path.join(inpath, folder))]
    all_folder = [folder for folder in os.listdir(inpath) if os.path.isdir(os.path.join(inpath, folder))]
    for fx in all_folder:
        [os.remove(os.path.join(inpath, fx, f"problem{count}.pddl")) for count in range(11, 1001) if os.path.exists(os.path.join(inpath, fx, f"problem{count}.pddl"))]