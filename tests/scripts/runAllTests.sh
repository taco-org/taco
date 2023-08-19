#!/bin/bash

# Get the absolute path of the script and its directory
ABS_PATH="$(readlink -f "${BASH_SOURCE}")"
TEST_HOME="$(dirname "$ABS_PATH")"

# Check if the script is invoked with the correct number of parameters
if [ "$#" -ne 2 ]; then
    echo "Need two parameters: [dataset folder] [output folder]"
    exit 1
fi

DATASET="$1"
OUTPUT_FOLDER="$2"
THRESHOLD=10

# Count the total number of files in the dataset folder
total_files=$(find "$DATASET" -type f | wc -l)

# Clear the output folder before running the test
rm -rf "$OUTPUT_FOLDER"/*

# Initialize a variable to count the number of files visited
visited_files=0

# Iterate through each file in the dataset folder
for file in "$DATASET"/*; do
    if [ -f "$file" ]; then
        # Increment the visited_files count
        ((visited_files++))
        # Print the progress
        echo "[$visited_files/$total_files]: $file"
        # Execute the test script with the current file
        "$TEST_HOME/runOneTest.sh" "$file" "$OUTPUT_FOLDER" "$THRESHOLD"
    fi
done
