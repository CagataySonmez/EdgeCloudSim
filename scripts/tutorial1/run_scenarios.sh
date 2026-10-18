#!/bin/bash

if [[ $# -ne 2 ]]; then
	echo "Missing arguments!"
	echo "Please provide the number of parallel processes and iterations."
	echo "Usage: $0 4 10"
	exit 1
fi

integer_regex='^[0-9]+$'

if ! [[ $1 =~ $integer_regex ]] || (( $1 < 1 )); then
	echo "$1 is not a positive integer!"
	echo "Please provide a positive number of parallel processes."
	echo "Usage: $0 4 10"
	exit 1
fi

if ! [[ $2 =~ $integer_regex ]] || (( $2 < 1 )); then
	echo "$2 is not a positive integer!"
	echo "Please provide a positive number of iterations."
	echo "Usage: $0 4 10"
	exit 1
fi

script_root_path="$(cd "$(dirname "$0")" && pwd -P)"
simulation_list="${script_root_path}/simulation.list"
runner_script="${script_root_path}/runner.sh"
root_out_folder="${script_root_path}/output"

num_of_processes=$1
iteration_number=$2
process_counter=0
job_counter=0
first_scenario=""

if [[ ! -f "$simulation_list" ]]; then
	echo "Simulation list not found:"
	echo "$simulation_list"
	exit 1
fi

if [[ ! -f "$runner_script" ]]; then
	echo "Runner script not found:"
	echo "$runner_script"
	exit 1
fi

# Seconds and process ID prevent collisions when started multiple times.
simulation_id="$(date '+%d-%m-%Y_%H-%M-%S')_$$"
simulation_out_folder="${root_out_folder}/${simulation_id}"

mkdir -p "$simulation_out_folder"

while IFS=';' read -r scenario_name edge_devices_file applications_file extra_field ||
	[[ -n "$scenario_name$edge_devices_file$applications_file$extra_field" ]]
do
	# Remove Windows-style carriage return if simulation.list uses CRLF.
	extra_field="${extra_field%$'\r'}"
	applications_file="${applications_file%$'\r'}"

	# Ignore blank lines and comment lines.
	if [[ -z "$scenario_name" || "$scenario_name" == \#* ]]; then
		continue
	fi

	if [[ -z "$edge_devices_file" || -z "$applications_file" || -n "$extra_field" ]]; then
		echo "Invalid simulation.list entry:"
		echo "${scenario_name};${edge_devices_file};${applications_file}${extra_field:+;${extra_field}}"
		echo "Expected format:"
		echo "scenario_name;edge_devices_file;applications_file"
		exit 1
	fi

	if [[ -z "$first_scenario" ]]; then
		first_scenario="$scenario_name"
	fi

	scenario_out_folder="${simulation_out_folder}/${scenario_name}"
	mkdir -p "$scenario_out_folder"

	echo "$(date '+%Y-%m-%d %H:%M:%S') - STARTED" \
		> "${scenario_out_folder}/progress.log"

	for (( iteration=1; iteration<=iteration_number; iteration++ ))
	do
		process_id=$((process_counter % num_of_processes))
		worker_script="${simulation_out_folder}/tmp_runner${process_id}.sh"

		# %q safely escapes paths and arguments containing spaces.
		printf '%q ' \
			/bin/bash \
			"$runner_script" \
			"$simulation_out_folder" \
			"$scenario_name" \
			"$edge_devices_file" \
			"$applications_file" \
			"$iteration" \
			>> "$worker_script"

		printf '\n' >> "$worker_script"

		process_counter=$((process_counter + 1))
		job_counter=$((job_counter + 1))
	done
done < "$simulation_list"

if (( job_counter == 0 )); then
	echo "No valid simulations were found in:"
	echo "$simulation_list"
	exit 1
fi

pids_file="${simulation_out_folder}/simulation_pids.txt"
: > "$pids_file"

started_processes=0

for (( process_id=0; process_id<num_of_processes; process_id++ ))
do
	worker_script="${simulation_out_folder}/tmp_runner${process_id}.sh"
	worker_log="${simulation_out_folder}/tmp_runner${process_id}.log"

	# Skip workers that received no jobs.
	if [[ ! -s "$worker_script" ]]; then
		continue
	fi

	nohup /bin/bash "$worker_script" \
		</dev/null \
		>>"$worker_log" 2>&1 &

	pid=$!
	echo "$pid" >> "$pids_file"

	started_processes=$((started_processes + 1))
done

echo "###############################################################"
echo "                  SIMULATIONS ARE STARTED!"
echo "###############################################################"
echo "Simulation ID: ${simulation_id}"
echo "Output folder: ${simulation_out_folder}"
echo "Total jobs: ${job_counter}"
echo "Worker processes: ${started_processes}"
echo "###############################################################"
echo "You can follow the progress via:"
echo "tail -f \"output/${simulation_id}/${first_scenario}/progress.log\""
echo "###############################################################"
echo "You can inspect an iteration via:"
echo "tail -f \"output/${simulation_id}/${first_scenario}/ite1.log\""
echo "###############################################################"
echo "You can stop all simulations via:"
echo "./stop_simulation.sh \"${simulation_id}\""
echo "###############################################################"
