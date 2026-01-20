buildSrc-test-junit5
====================

Sample project setup to show how you can test your _buildSrc_ custom build logic using
Junit5 Unit tests.

`./gradlew hello` this task uses a build helper function from _buildSrc_. The code inside _buildSrc_ will be compiled and tested prior to the execution of this task.
