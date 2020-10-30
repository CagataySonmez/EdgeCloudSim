#!/bin/sh

echo "This script kills all java processes including Eclipse etc."
read -p "Do you want to continue[y/n]? " REPLY

if [ "$REPLY" = "y" ]
then
    while pgrep java >/dev/null 2>&1
    do
        killall java
        sleep 1
    done
fi

echo "Operation completed. bye..."
