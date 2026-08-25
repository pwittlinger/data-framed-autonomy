
set -e # Exit on error
trap "echo 'Terminating...'; kill 0; exit 1" SIGINT

num_of_traces=5


# Find plan for all problems
for folder in output/pddl/*/; do
	echo "Working on {folder}"
	clean_folder="${folder%/}"        # Remove trailing slash
	last_part="${clean_folder##*/}"   # Extract last component
	echo "Folder name: $last_part"
	results_file=results/${last_part}_dump.txt
    stats_file=results/${last_part}_stats.txt
	for val in $(seq 1 $num_of_traces); do
	
	(wsl -e time -v java -Xmx10g -jar "enhsp.jar" -o domains/domain_framed_autonomy_process_reset_time.pddl -f ${folder}/problem1.pddl -planner opt-blind) 2>&1 | tee -a $results_file

    done
    java -jar java_helpers/StatsExtractor.jar $results_file $stats_file $num_of_traces
  done

wait

