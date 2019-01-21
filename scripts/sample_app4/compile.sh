#!/bin/sh
rm -rf ../../bin
mkdir ../../bin
javac -classpath "../../lib/cloudsim-4.0.jar:../../lib/commons-math3-3.6.1.jar:../../lib/jFuzzyLogic_v3.0.jar:../../lib/colt.jar" -sourcepath ../../src ../../src/edu/boun/edgecloudsim/applications/sample_app4/FuzzyMainApp.java -d ../../bin
