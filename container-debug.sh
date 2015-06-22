#!/usr/bin/env bash

echo "Working dir $PWD"
echo "Listing files $(ls)"

# $(whereis gradle)

# Build
./gradlew assemble

if [ ! "$?" -eq "0" ]
then
    echo "Build failed"
    exit 1
fi

$(cp build/libs/*.jar build/idp-latest.jar)

# If no jar found, look for a war
if [ ! "$?" -eq "0" ]
then
    echo "Copying war file"
    $(cp build/libs/*.war build/idp-latest.war)
fi

# Run tests
#if [ ! "$?" -eq "0" ]
#then
#    echo "Run tests"
#    exit 1
#fi
