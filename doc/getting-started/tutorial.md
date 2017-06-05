#Tutorial

In this tutorial all the basics of using the GSK (Gradle Script Kotlin) DSL for Gradle will be covered, which includes the following:

- Specifying the basic project information
- Including additional project meta data to use in the entire build script
- Adding dependencies
- Using and configuring Gradle plugins
- Adding repositories

Every Gradle project that uses GSK must include a build file called **build.gradle.kts** in the root directory of the project, and [Gradle wrapper](https://docs.gradle.org/3.5/userguide/gradle_wrapper.html) version **4.0-rc-1**.


##Project Information

All information about the project must be specified at the top of the build file. Only **group** and **version** properties need to be set, eg:
```kotlin
group = "org.example"
version = "0.1-SNAPSHOT"
```


##Project Meta Data

Very useful to specify extra project meta data (in the **extra** Map) that will be be used in the entire build script in multiple places, especially when defining versions used in project dependencies. Any specified meta data must be defined in a **buildscript** block, eg:
```kotlin
buildscript {
    extra["kotlin-ver"] = "1.1.2-4"
    extra["junit-ver"] = "4.12"
    //...
}
```


##Adding Repositories

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


##Using Gradle Plugins

In order to use a Gradle plugin a **apply** block needs to be defined after the **buildscript** block. Every plugin that is used requires a call to the **plugin** function (in the **apply** block) with the name of the plugin passed in as a String, eg:
```kotlin
apply {
    plugin("kotlin")
    plugin("application")
}
```


##Configuring A Gradle Plugin

Once a Gradle plugin is *applied* it is configured after the **apply** block. A call is made to the **configure** function with the plugin passed through as the generic type, eg:
```kotlin
configure<ApplicationPluginConvention> {
    mainClassName = "org.example.codekata.MainKt"
}
```

Note that some plugins need to be imported first before they can be configured.


##Adding Dependencies

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