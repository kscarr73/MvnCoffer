#!/bin/bash

set -o allexport
source ../mvncoffer.properties
set +o allexport

#JVM_ARGS=
JVM_ARGS="-agentlib:jdwp=transport=dt_socket,server=y,address=8000"

/usr/lib/jvm/java-20-temurin/bin/java --enable-preview $JVM_ARGS -jar MvnCoffer-2.0.1.jar > mvncoffer.log

