#!/bin/sh
rm -rf ../../bin
mkdir ../../bin
javac -classpath "../../lib/cloudsim-4.0.jar:../../lib/commons-math3-3.6.1.jar:../../lib/colt.jar:../../lib/weka.jar:../../lib/mtj-1.0.4.jar" -sourcepath ../../src ../../src/edu/boun/edgecloudsim/applications/vec_ai_app/VehicularMainApp.java -d ../../bin
