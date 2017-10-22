source-control
==============

A project that depend on external sources:

 1. [external/compute.git](./external/compute.git) implements the main algorithm to compute the answer to the ultimate question of Life, the Universe and Everything
 2. [./](./) implements the command line interface

See [settings.gradle.kts](./settings.gradle.kts).

Run with:

    ./gradlew run

Check compilation dependencies with:

    ./gradlew dependencies --configuration compileClasspath
