composite-build-logic
=====================

Demonstrates how to use [Composite Builds](https://docs.gradle.org/current/userguide/composite_builds.html)
for build logic.

The outer build includes two other builds:
- `gradle/shared`
- `gradle/plugins`

Both the outer build and `gradle/plugins` depend on `gradle/shared` in several ways.

Run with:

    ./gradlew sample

See [settings.gradle.kts](./settings.gradle.kts) to see how to include builds and do dependency substitution.
