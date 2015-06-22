#!/usr/bin/env bash

echo "Working dir $PWD"
echo "Listing files $(ls)"

# $(whereis gradle)

# Build
./gradlew build

if [ ! "$?" -eq "0" ]
then
    echo "Build failed"
    exit 1
fi

$(cp build/libs/*.jar build/search-latest.jar)

# Run tests
#if [ ! "$?" -eq "0" ]
#then
#    echo "Run tests"
#    exit 1
#fi
