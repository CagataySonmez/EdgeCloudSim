#!/bin/sh
# Windows' javac wants ';' between classpath entries even under Git
# Bash/MSYS, where ':' silently fails with "package does not exist".
case "$(uname -s)" in
	CYGWIN*|MINGW*|MSYS*) SEP=";" ;;
	*) SEP=":" ;;
esac

JARS="../../lib/cloudsim-4.0.jar${SEP}../../lib/commons-math3-3.6.1.jar${SEP}../../lib/colt.jar"

rm -rf ../../bin
mkdir ../../bin
javac -encoding UTF-8 -classpath "$JARS" -sourcepath ../../src ../../src/edu/boun/edgecloudsim/applications/motivation2/MainApp.java -d ../../bin
