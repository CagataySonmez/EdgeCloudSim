#!/bin/sh
# Compiles and runs the Java test suite (test/) against the main source
# (src/), using the same lib/ jars as scripts/*/compile.sh plus JUnit's
# standalone console launcher (lib/junit-platform-console-standalone-*.jar).
#
# Usage: ./test.sh   (from the EdgeCloudSim/ repo root)
set -e

root="$(dirname "$(readlink -f "$0")")"
cd "$root"

# Windows' javac/java want ';' between classpath entries even under Git
# Bash/MSYS, where ':' silently produces "package does not exist" for every
# jar. Without this the suite could not be run at all on Windows.
case "$(uname -s)" in
	CYGWIN*|MINGW*|MSYS*) SEP=";" ;;
	*) SEP=":" ;;
esac

junit_jar=$(ls lib/junit-platform-console-standalone-*.jar | head -n 1)

rm -rf bin test-bin
mkdir -p bin test-bin

libs="lib/cloudsim-4.0.jar${SEP}lib/commons-math3-3.6.1.jar${SEP}lib/colt.jar"

echo "Compiling main source..."
javac -classpath "$libs" \
	-sourcepath src \
	src/edu/boun/edgecloudsim/applications/resaco/ReSACOMainApp.java \
	-d bin

echo "Compiling tests..."
javac -classpath "bin${SEP}${libs}${SEP}${junit_jar}" \
	-sourcepath "src${SEP}test" \
	$(find test -name "*.java") \
	-d test-bin

echo "Running tests..."
java -jar "${junit_jar}" execute \
	--classpath "bin${SEP}test-bin${SEP}${libs}" \
	--scan-classpath test-bin
