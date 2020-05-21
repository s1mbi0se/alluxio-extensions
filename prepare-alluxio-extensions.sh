#!/bin/bash


cd /simbiose/alluxio-extensions
git checkout develop
cd underfs/b2
mvn clean install
B2_VERSION=$(mvn --quiet help:evaluate -Dexpression=project.version -DforceStdout)
cp target/alluxio-underfs-b2-${B2_VERSION}.jar /tmp
