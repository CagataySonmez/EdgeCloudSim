#!/bin/bash
if [ "$#" -ne 2 ]; then
	echo "Missing arguments! Please provide number of parallel processes and number of iterations."
	echo "Usage: '$0 4 10'"
	exit 1
fi

re='^[0-9]+$'
if ! [[ $1 =~ $re ]] ; then
	echo "$1 is not an integer! Please provide number of parallel processes."
	echo "Usage: '$0 4 10'"
	exit 1
fi

if ! [[ $2 =~ $re ]] ; then
	echo "$1 is not an integer! Please provide number of iterations."
	echo "Usage: '$0 4 10'"
	exit 1
fi

script_root_path="$(dirname "$(readlink -f "$0")")"
root_out_folder=${script_root_path}/output
num_of_processes=$1
iterationNumber=$2
process_counter=0

date=$(date '+%d-%m-%Y_%H-%M')
simulation_out_folder=${root_out_folder}/${date}
mkdir -p $simulation_out_folder

simulations=$(cat ${script_root_path}/simulation.list)

rm -rf ${script_root_path}/tmp_runner*
		
for sim_args in $simulations
do
	scenario_name=$(echo $sim_args | cut -d ';' -f1)
	edge_devices_file=$(echo $sim_args | cut -d ';' -f2)
	applications_file=$(echo $sim_args | cut -d ';' -f3)
	for (( i=1; i<=$iterationNumber; i++ ))
	do
		process_id=$(($process_counter % $num_of_processes))
		process_counter=$(($process_counter + 1))
		
		echo "${script_root_path}/runner.sh $simulation_out_folder $scenario_name $edge_devices_file $applications_file ${i}" >> "${simulation_out_folder}/tmp_runner${process_id}.sh"
	done
done

#num_of_cores=$(grep -c ^processor /proc/cpuinfo)

for (( i=0; i<$num_of_processes; i++ ))
do
	chmod +x ${simulation_out_folder}/tmp_runner${i}.sh
	${simulation_out_folder}/tmp_runner${i}.sh &

# 	pid=$!
#	cpu=$(($i % $num_of_cores))
# 	taskset -cp $cpu,$cpu $pid
done
