maven-publish-multi-module
==========================

A multi-project build with two Kotlin based projects:

 1. [core](./core) implements the main algorithm to compute the answer to the ultimate question of Life, the Universe and Everything
 2. [cli](./cli) implements the command line interface

The publications for the sub-modules need to be declared explicitly.

Try publication:

    ./gradlew publishToMavenLocal
