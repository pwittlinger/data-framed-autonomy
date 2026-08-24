import os
import sys
root_path = os.path.dirname(os.path.abspath(sys.argv[0]))

inpath = os.path.join(root_path, "pddl_gen", "src", "main", "resources", "output", "pddl")

# Run the file from root directory of the project.
# Removes all instances greater than 100 from the problem folders.

if __name__=="__main__":
        
    fpaths = [folder.replace("dparsed", "").replace(".decl", "") for folder in os.listdir(inpath) if os.path.isdir(os.path.join(inpath, folder))]
    all_folder = [folder for folder in os.listdir(inpath) if os.path.isdir(os.path.join(inpath, folder))]
    for fx in all_folder:
        [os.remove(os.path.join(inpath, fx, f"problem{count}.pddl")) for count in range(11, 1001) if os.path.exists(os.path.join(inpath, fx, f"problem{count}.pddl"))]