#!/bin/sh

mkdir $APPLICATION_DIR 

cat << EOF >> /init
java \
-classpath $APPLICATION_DIR \
-Dcom.sun.management.jmxremote.port=9999 \
-Dcom.sun.management.jmxremote.authenticate=false \
-Dcom.sun.management.jmxremote.ssl=false \
-jar \
$APPLICATION_DIR/${project.artifactId}-${project.version}.jar
EOF

chmod +x /init
