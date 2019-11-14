#!/bin/sh

mvn clean package -am -pl filter-devkit-mavenizer

JAR_NAME=`find filter-devkit-mavenizer -name "filter-devkit-mavenizer-*.jar" -print`
CLASS_PATH_FILE=`find filter-devkit-mavenizer/target -name "classpath.txt" -print`
CLASS_PATH=`cat "$CLASS_PATH_FILE"`

java -cp "$JAR_NAME:$CLASS_PATH" com.vordel.mavenizer.GatewayFullMavenizer $*