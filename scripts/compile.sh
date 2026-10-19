#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
source_list=$(mktemp "${TMPDIR:-/tmp}/edgecloudsim-sources.XXXXXX")

cleanup() {
	rm -f -- "$source_list"
}
trap cleanup EXIT INT TERM

find "$project_dir/src" -name '*.java' -print | sort > "$source_list"
rm -rf -- "$project_dir/bin"
mkdir -p -- "$project_dir/bin"

javac -Xlint:all -Werror \
	-classpath "$project_dir/lib/*" \
	-d "$project_dir/bin" \
	@"$source_list"

echo "All EdgeCloudSim sources compiled successfully."
