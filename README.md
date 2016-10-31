# kwoory

## What is it ?
kwoory is a command line tool, written in Groovy, designed to perform simple queries on database. The main idea is too make simple queries easier to type and make them a little less verbose.

## How to build ?

With Gradle installed :

    gradle makeJar

## How to run ?

The best way is to create .sh / .bat file and add it in your path or in ```/usr/local/bin```

    #!/bin/bash
    $JAVA_HOME/bin/java -cp "/path/to/kwoory-{version}.jar:/path/containing/kwoory.config" KwooryMain $@

## How to use ?

    kwoory {from|group} TABLE/ALIAS [parameters] [with [columns]]

You will find here a full documentation (in progress, will come soon :)
