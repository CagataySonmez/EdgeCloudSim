#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/.." && pwd)
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/edgecloudsim-build.XXXXXX")
doc_dir=$(mktemp -d "${TMPDIR:-/tmp}/edgecloudsim-doc.XXXXXX")
source_list=$(mktemp "${TMPDIR:-/tmp}/edgecloudsim-sources.XXXXXX")
test_source_list=$(mktemp "${TMPDIR:-/tmp}/edgecloudsim-tests.XXXXXX")

cleanup() {
	rm -rf -- "$build_dir" "$doc_dir"
	rm -f -- "$source_list" "$test_source_list"
}
trap cleanup EXIT INT TERM

if command -v sha256sum >/dev/null 2>&1; then
	(cd "$project_dir" && sha256sum --check lib/SHA256SUMS)
elif command -v shasum >/dev/null 2>&1; then
	(cd "$project_dir" && shasum -a 256 --check lib/SHA256SUMS)
else
	echo "A SHA-256 checksum utility (sha256sum or shasum) is required." >&2
	exit 1
fi

find "$project_dir/src" -name '*.java' -print | sort > "$source_list"
find "$project_dir/tests" -name '*.java' -print | sort > "$test_source_list"

javac -Xlint:all -Werror \
	-cp "$project_dir/lib/*" \
	-d "$build_dir" \
	@"$source_list" @"$test_source_list"

java -ea -cp "$build_dir:$project_dir/lib/*" \
	edu.boun.edgecloudsim.applications.sample_app5.CoreRegressionTest

java -ea -cp "$build_dir:$project_dir/lib/*" \
	edu.boun.edgecloudsim.core.ConfigurationSmokeTest "$project_dir"

javadoc -quiet -Xdoclint:all \
	-classpath "$project_dir/lib/*" \
	-d "$doc_dir" \
	@"$source_list" @"$test_source_list"

echo "Compilation, regression tests, and Javadoc validation passed."
