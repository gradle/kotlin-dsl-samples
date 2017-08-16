# Tutorial

In this tutorial all the basics of using the Gradle Kotlin DSL (GKD) will be covered, which includes the following:

- Specifying the basic project information
- Including additional project meta data to use in the entire build script
- Adding dependencies
- Using and configuring Gradle plugins
- Adding repositories

Every Gradle project that uses GKD must include a build file called **build.gradle.kts** in the root directory of the project, and [Gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) version **4.1**. Some *Kotlinic* (idiomatic Kotlin - aka **"The Kotlin way"**) examples are shown in the tutorial.


## Project Information

All information about the project must be specified at the top of the build file. Only **group** and **version** properties need to be set, eg:
```kotlin
group = "org.example"
version = "0.1-SNAPSHOT"
```


## Project Meta Data

Very useful to specify extra project meta data (in the **extra** Map) that will be be used in the entire build script in multiple places, especially when defining versions used in project dependencies. Any specified meta data must be defined in a **buildscript** block, eg:
```kotlin
buildscript {
    extra["kotlin-ver"] = "1.1.2-4"
    extra["junit-ver"] = "4.12"
    //...
}
```


## Adding Repositories

Repositories need be specified in the **buildscript** block and at the build script level, eg:
```kotlin
buildscript {
    //...
    repositories {
        jcenter()
    }
}

//...
repositories {
    jcenter()
}
```


## Using Gradle Plugins

In order to use a Gradle plugin a **apply** block needs to be defined after the **buildscript** block. Every plugin that is used requires a call to the **plugin** function (in the **apply** block) with the name of the plugin passed in as a String, eg:
```kotlin
apply {
    plugin("kotlin")
    plugin("application")
}
```


## Configuring A Gradle Plugin

Once a Gradle plugin is *applied* it is configured after the **apply** block. A call is made to the **configure** function with the plugin passed through as the generic type, eg:
```kotlin
configure<ApplicationPluginConvention> {
    mainClassName = "org.example.codekata.MainKt"
}
```

Note that some plugins need to be imported first before they can be configured.


## Adding Dependencies

Plugin dependencies are added in the **dependencies** block directly inside the **buildscript** block, eg:
```kotlin
//...
buildscript {
  //...
    repositories {
        jcenter()
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlin-ver"]}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${extra["dokka-ver"]}")
    }
}
```

Project dependencies are added in the **dependencies** block, eg:
```kotlin
dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jre8:${extra["kotlin-ver"]}")
    testCompile("junit:junit:${extra["junit-ver"]}")
}
```

## Gradle Tasks

Gradle can run tasks which can be thought of as build actions (eg **compileKotlin** - Compile Kotlin source files). Usually when you run Gradle a task is specified that is to be executed when running a build. It is good practice when customising/creating multiple Gradle tasks to do it within the **tasks** block, which is positioned after the **dependencies** block.


### Customising A Task

Often you will find it useful to customise some Gradle tasks (ones supplied by Gradle and/or a third party Gradle plugin). In order to customise a task it needs to be fetched first (via a lookup), eg:

```kotlin
tasks {
    // Fetching the "compileKotlin" task and customising it.
    "compileKotlin"(KotlinCompile::class) {
        doLast { println("Finished compiling.") }
    }
}
```


Alternatively you can just fetch the task if it is going to be used in multiple places in a build script, eg:

```kotlin
tasks {
    val compileKotlin = get("compileKotlin") as KotlinCompile
    
    compileKotlin.doFirst { println("Warming up the Kotlin compiler...") }
    compileKotlin.doLast { println("Finished compiling.") }
}
```


### Creating A Custom Task

When creating a custom Gradle task it needs to be given a unique name, and if its based on a task type (eg Jar) the class object (a **KClass**) representing the type, eg:

```kotlin
// ...

tasks {
    // Create a custom task called "createDokkaJar" that is based on the Jar type, and depends on 
    // the "dokka" task to run first before executing.
    "createDokkaJar"(Jar::class)" {
        dependsOn("dokka")
        classifier = "javadoc"
        from(dokka.outputDirectory)
    }
}
```

Alternatively a custom Gradle task can be created explicitly (more readble, yet slightly more verbose) which is *Kotlinic*, eg:

```kotlin
val createDokkaJar = task<Jar>("createDokkaJar") {
    dependsOn("dokka")
    classifier = "javadoc"
    from(dokka.outputDirectory)
}
```