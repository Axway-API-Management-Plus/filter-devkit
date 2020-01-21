#!/bin/sh -x

mvn clean package -am -pl filter-devkit-mavenizer

CLASS_FOLDER='filter-devkit-mavenizer/target/classes'
CLASS_PATH_FILE=`find filter-devkit-mavenizer/target -name "classpath.txt" -print`
CLASS_PATH=`cat "$CLASS_PATH_FILE"`

java -cp "$CLASS_FOLDER:$CLASS_PATH" com.vordel.mavenizer.GatewayFullMavenizer $*
