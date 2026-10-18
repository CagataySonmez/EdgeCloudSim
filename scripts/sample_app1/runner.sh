#!/bin/sh

if [ "$#" -ne 5 ]; then
	echo "Usage: $0 <output_folder> <scenario_name> <edge_devices_file> <applications_file> <iteration_number>"
	exit 1
fi

# Linux and macOS support for readlink -f is different.
# This is a workaround to get the absolute path of the script.
script_root_path="$(cd "$(dirname "$0")" && pwd -P)"

simulation_out_folder=$1
scenario_name=$2
edge_devices_file=$3
applications_file=$4
iteration_number=$5

scenario_base_folder="${simulation_out_folder}/${scenario_name}"
scenario_out_folder="${scenario_base_folder}/ite${iteration_number}"
iteration_log="${scenario_out_folder}.log"
progress_log="${scenario_base_folder}/progress.log"

scenario_conf_file="${script_root_path}/config/${scenario_name}.properties"
scenario_edge_devices_file="${script_root_path}/config/${edge_devices_file}"
scenario_applications_file="${script_root_path}/config/${applications_file}"

# Orijinal ../../bin ve ../../lib yollarını script konumuna sabitler.
project_root="$(cd "${script_root_path}/../.." && pwd -P)"

classpath="${project_root}/bin"
classpath="${classpath}:${project_root}/lib/cloudsim-7.0.0-alpha.jar"
classpath="${classpath}:${project_root}/lib/commons-math3-3.6.1.jar"
classpath="${classpath}:${project_root}/lib/colt.jar"

if [ ! -f "$scenario_conf_file" ]; then
	echo "Scenario configuration not found: $scenario_conf_file"
	exit 1
fi

if [ ! -f "$scenario_edge_devices_file" ]; then
	echo "Edge devices configuration not found: $scenario_edge_devices_file"
	exit 1
fi

if [ ! -f "$scenario_applications_file" ]; then
	echo "Applications configuration not found: $scenario_applications_file"
	exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
	java_command="${JAVA_HOME}/bin/java"
else
	java_command="java"
fi

mkdir -p "$scenario_out_folder" || exit 1

"$java_command" \
	-classpath "$classpath" \
	edu.boun.edgecloudsim.applications.sample_app1.MainApp \
	"$scenario_conf_file" \
	"$scenario_edge_devices_file" \
	"$scenario_applications_file" \
	"$scenario_out_folder" \
	"$iteration_number" \
	> "$iteration_log" 2>&1

java_status=$?

if [ "$java_status" -eq 0 ]; then
	echo "$(date '+%Y-%m-%d %H:%M:%S') - ite${iteration_number} OK" \
		>> "$progress_log"
else
	echo "$(date '+%Y-%m-%d %H:%M:%S') - ite${iteration_number} FAIL (exit code: ${java_status})" \
		>> "$progress_log"
fi

if tar -czf "${scenario_out_folder}.tar.gz" \
	-C "$scenario_base_folder" \
	"ite${iteration_number}"
then
	rm -rf "$scenario_out_folder"
else
	echo "$(date '+%Y-%m-%d %H:%M:%S') - ite${iteration_number} ARCHIVE FAILED" \
		>> "$progress_log"

	# If archiving fails, we keep the uncompressed folder for debugging purposes.
	exit 1
fi

exit "$java_status"
