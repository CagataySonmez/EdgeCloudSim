#!/bin/sh

javac -classpath "./json-simple-1.1.1.jar:../../../lib/weka.jar:../../../lib/mtj-1.0.4.jar" WekaModelCreator.java 
java -classpath ".:./json-simple-1.1.1.jar:../../../lib/weka.jar:../../../lib/mtj-1.0.4.jar" WekaModelCreator config.json
