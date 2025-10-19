#!/bin/bash
# stop_simulation.sh
# Usage: ./stop_simulation.sh <simulation_id>

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <simulation_id>"
    exit 1
fi

simulation_id=$1
script_root_path="$(dirname "$(readlink -f "$0")")"
simulation_out_folder="${script_root_path}/output/${simulation_id}"
pid_file="${simulation_out_folder}/simulation_pids.txt"

if [ ! -f "$pid_file" ]; then
    echo "No PID file found for simulation ID '${simulation_id}'."
    exit 1
fi

echo "Stopping simulation ${simulation_id}..."

while read pid; do
    if kill -0 $pid 2>/dev/null; then
        echo "Killing runner PID $pid and its child processes..."
        # Kill entire process group (runner + child processes)
        kill -- -$pid
        sleep 1
        # Force kill if still running
        if kill -0 $pid 2>/dev/null; then
            echo "Force killing PID $pid and its children"
            kill -9 -- -$pid
        fi
    fi
done < "$pid_file"

rm -f "$pid_file"
echo "Simulation ${simulation_id} stopped."
