#!/bin/bash
# stop_simulation.sh
# Usage: ./stop_simulation.sh <simulation_id>

if [[ $# -ne 1 ]]; then
	echo "Usage: $0 <simulation_id>"
	exit 1
fi

simulation_id=$1

# Accept both the old and new simulation ID formats:
# 17-10-2026_14-30
# 17-10-2026_14-30-45_12345
simulation_id_regex='^[0-9]{2}-[0-9]{2}-[0-9]{4}_[0-9]{2}-[0-9]{2}(-[0-9]{2})?(_[0-9]+)?$'

if ! [[ $simulation_id =~ $simulation_id_regex ]]; then
	echo "Invalid simulation ID: $simulation_id"
	exit 1
fi

script_root_path="$(cd "$(dirname "$0")" && pwd -P)"
simulation_out_folder="${script_root_path}/output/${simulation_id}"
pid_file="${simulation_out_folder}/simulation_pids.txt"

if [[ ! -d "$simulation_out_folder" ]]; then
	echo "Simulation folder not found:"
	echo "$simulation_out_folder"
	exit 1
fi

if [[ ! -f "$pid_file" ]]; then
	echo "No PID file found for simulation ID '${simulation_id}'."
	exit 1
fi

if ! command -v pgrep >/dev/null 2>&1; then
	echo "Required command not found: pgrep"
	exit 1
fi

# Store process IDs in child-to-parent order.
process_tree=""

collect_process_tree()
{
	local current_pid=$1
	local child_pid

	if ! kill -0 "$current_pid" 2>/dev/null; then
		return
	fi

	# Temporarily suspend the process to prevent it from spawning new children.
	kill -STOP "$current_pid" 2>/dev/null || return

	for child_pid in $(pgrep -P "$current_pid" 2>/dev/null)
	do
		collect_process_tree "$child_pid"
	done

	process_tree="${process_tree} ${current_pid}"
}

echo "Stopping simulation ${simulation_id}..."

found_process=0
failed_process=0

while IFS= read -r pid || [[ -n "$pid" ]]
do
	# Remove a possible carriage return from Windows-style line endings.
	pid="${pid%$'\r'}"

	if ! [[ $pid =~ ^[0-9]+$ ]] || (( pid < 2 )); then
		echo "Skipping invalid PID: $pid"
		failed_process=1
		continue
	fi

	if ! kill -0 "$pid" 2>/dev/null; then
		echo "Runner PID $pid is no longer running."
		continue
	fi

	# Verify that a reused PID does not belong to an unrelated process.
	process_command="$(ps -ww -p "$pid" -o command= 2>/dev/null)"

	case "$process_command" in
		*"${simulation_out_folder}"*)
			;;
		*)
			echo "Refusing to kill PID $pid because it does not belong to this simulation."
			echo "Command: $process_command"
			failed_process=1
			continue
			;;
	esac

	echo "Stopping runner PID $pid and its child processes..."

	found_process=1
	process_tree=""
	collect_process_tree "$pid"

	# First, request graceful termination.
	for tree_pid in $process_tree
	do
		kill -TERM "$tree_pid" 2>/dev/null || true
		kill -CONT "$tree_pid" 2>/dev/null || true
	done

	sleep 1

	# Forcefully terminate any remaining processes.
	for tree_pid in $process_tree
	do
		if kill -0 "$tree_pid" 2>/dev/null; then
			echo "Force killing PID $tree_pid"
			kill -KILL "$tree_pid" 2>/dev/null || true
		fi
	done
done < "$pid_file"

if [[ $failed_process -eq 0 ]]; then
	rm -f "$pid_file"
else
	echo "The PID file was preserved because one or more processes could not be verified."
fi

if [[ $found_process -eq 0 ]]; then
	echo "No active simulation processes were found."
else
	echo "Simulation ${simulation_id} stopped."
fi

exit "$failed_process"
