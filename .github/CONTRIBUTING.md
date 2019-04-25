# Contributing to Gradle Kotlin DSL Samples

Thank you for considering making a contribution to Gradle Kotlin DSL Samples! This guide explains how to setup your environment for development and where to get help if you encounter trouble.

## Where is the code?

This repository (`gradle/kotlin-dsl`) contains extra samples for the Gradle Kotlin DSL only. If you intend to contribute an extra sample keep reading.

The Gradle Kotlin DSL code can be found in the [gradle/gradle](https://github.com/gradle/gradle) git repository in the `:kotlinDsl*` sub-projects.
Please direct your contributions to that repository and see [how to contribute to Gradle](https://github.com/gradle/gradle/blob/master/CONTRIBUTING.md).

## Accept Developer Certificate of Origin

In order for your contributions to be accepted, you must [sign off](https://git-scm.com/docs/git-commit#git-commit---signoff) your Git commits to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).

## Follow the Code of Conduct

In order to foster a more inclusive community, Gradle has adopted the [Contributor Covenant](https://www.contributor-covenant.org/version/1/4/code-of-conduct/).

Contributors must follow the Code of Conduct outlined at [https://gradle.org/conduct/](https://gradle.org/conduct/).

## Making Changes

### Development Setup

In order to make changes to the Gradle Kotlin DSL Samples, you'll need:

* A text editor or IDE. We use and recommend the very latest [IntelliJ IDEA CE](http://www.jetbrains.com/idea/).
* A [Java Development Kit](http://www.oracle.com/technetwork/java/javase/downloads/index.html) (JDK) version 1.8 or higher
* [git](https://git-scm.com/) and a [GitHub account](https://github.com/join)

Gradle Kotlin DSL uses a pull request model for contributions. Fork [gradle/kotlin-dsl](https://github.com/gradle/kotlin-dsl) and clone your fork.

Configure your Git username and email with
```
git config user.name 'First Last'
git config user.email user@example.com
```

Before importing the project into IntelliJ IDEA make sure to run `./gradlew check` at least once so all required files are generated.

### Development Workflow

Samples are located in the `samples/` directory of this repository.

To add a new sample, simply create a new directory under `samples/`. That directory must include a [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html), please copy it from another sample directory.

A test runs `tasks` on each sample, so your new sample will automatically be smoke-tested. Please consider adding a more precise test.

Samples are tested using [gradle/exemplar](https://github.com/gradle/exemplar). If your test can be expressed using [exemplar external samples](https://github.com/gradle/exemplar#configuring-external-samples), add the configuration files to `subprojects/samples-tests/src/exemplar/samples/$yourSampleDir`. Otherwise please add a JUnit based test in `subprojects/samples-tests/src/test/kotlin` extending from `AbstractSampleTest`.

After making changes, you can test them by running `./gradlew check`.

You can debug Gradle by adding `-Dorg.gradle.debug=true` when executing. Gradle will wait for you to attach a debugger at `localhost:5005` by default.

### Creating Commits And Writing Commit Messages

The commit messages that accompany your code changes are an important piece of documentation, and help make your contribution easier to review.
Please consider reading [How to Write a Git Commit Message](http://chris.beams.io/posts/git-commit/). Minimally, follow these guidelines when writing commit messages.

* Keep commits discrete: avoid including multiple unrelated changes in a single commit
* Keep commits self-contained: avoid spreading a single change across multiple commits. A single commit should make sense in isolation
* If your commit pertains to a GitHub issue, include (`See #123`) in the commit message on a separate line
* [Sign off](https://git-scm.com/docs/git-commit#git-commit---signoff) your Git commits to indicate that you agree to the terms of [Developer Certificate of Origin](https://developercertificate.org/).

## Submitting Your Change

All code contributions should be submitted via a [pull request](https://help.github.com/articles/using-pull-requests) from a [forked GitHub repository](https://help.github.com/articles/fork-a-repo).

Once received, the pull request will be reviewed by a Gradle Kotlin DSL developer.

## Getting Help

If you run into any trouble, please reach out to us in the #kotlin-dsl channel of the [Gradle Community Slack](https://join.slack.com/t/gradle-community/shared_invite/enQtNDE3MzAwNjkxMzY0LTYwMTk0MWUwN2FiMzIzOWM3MzBjYjMxNWYzMDE1NGIwOTJkMTQ2NDEzOGM2OWIzNmU1ZTk5MjVhYjFhMTI3MmE).

## Resources

* The [Gradle Kotlin DSL Primer](https://docs.gradle.org/current/userguide/kotlin_dsl.html) is a must read.
* The Gradle [user manual](https://docs.gradle.org/current/userguide/userguide.html) and [guides](https://gradle.org/guides/) contain Kotlin DSL build script samples that demonstrate how to use all Gradle features.
* Some [diagrams](../doc/c4) provide a good overview of how the Kotlin DSL is structured and interacts with Gradle, Gradle plugins, IDEs.

## Our Thanks
We deeply appreciate your effort toward improving Gradle. If you enjoyed this process, perhaps you should consider getting [paid to develop Gradle](https://gradle.com/careers)?
