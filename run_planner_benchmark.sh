
set -e # Exit on error
trap "echo 'Terminating...'; kill 0; exit 1" SIGINT

# gen_path=multi-perspective_aligner # if you want to test Axel Mezini's implementation. URL: https://github.com/axelmezini/multi-perspective_aligner
num_of_traces=5
gen_path=pddl_gen


# Find plan for all problems
for folder in pddl_gen/src/main/resources/output/pddl/*/; do
	echo "Working on {folder}"
	clean_folder="${folder%/}"        # Remove trailing slash
	last_part="${clean_folder##*/}"   # Extract last component
	echo "Folder name: $last_part"
	results_file=results/${last_part}_dump.txt
    stats_file=results/${last_part}_stats.txt
	for val in $(seq 1 $num_of_traces); do
	


      #(wsl -e time -v java -Xmx8g -jar "${gen_path}/src/main/resources/output/enhsp3.jar" -o ${gen_path}/src/main/resources/output/domain_with_failureSmall.pddl -f ${gen_path}/src/main/resources/output/pddl/${folder}/problem${val}.pddl -planner opt-blind) 2>&1 | tee -a $results_file
	  #(wsl -e time -v java -Xmx8g -jar "${gen_path}/src/main/resources/output/enhsp3.jar" -o ${gen_path}/src/main/resources/output/domain_framed_autonomy_process_reset.pddl -f ${folder}/problem${val}.pddl -planner opt-blind) 2>&1 | tee -a $results_file
	  (wsl -e time -v java -Xmx10g -jar "${gen_path}/src/main/resources/output/enhsp3.jar" -o ${gen_path}/src/main/resources/output/domain_framed_autonomy_process_reset_time.pddl -f ${folder}/problem1.pddl -planner opt-blind) 2>&1 | tee -a $results_file

      # timeout 45 bash -c "/usr/bin/time -v ./ff -o ${gen_path}/src/main/resources/output/pddl/domain.pddl -f ${gen_path}/src/main/resources/output/pddl/problem${val}.pddl -s 3+H" 2>&1 | tee -a $results_file

      # (/usr/bin/time -v ./fast-downward-24.06.1/fast-downward.py ${gen_path}/src/main/resources/output/pddl/domain.pddl ${gen_path}/src/main/resources/output/pddl/problem${val}.pddl --search "astar(hmax())" ) 2>&1 | tee -a $results_file

      # timeout has precedence over /usr/bin/time, so nothing will be shown in that case
    done
    java -jar java_helpers/StatsExtractor.jar $results_file $stats_file $num_of_traces
  done

wait

