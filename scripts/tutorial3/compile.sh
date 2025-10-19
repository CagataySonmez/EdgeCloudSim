#!/bin/sh
rm -rf ../../bin
mkdir ../../bin
javac -classpath "../../lib/cloudsim-7.0.0-alpha.jar:../../lib/commons-math3-3.6.1.jar:../../lib/colt.jar" -sourcepath ../../src ../../src/edu/boun/edgecloudsim/applications/tutorial3/MainApp.java -d ../../bin
