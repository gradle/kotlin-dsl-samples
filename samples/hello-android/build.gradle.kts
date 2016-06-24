import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.internal.dsl.BuildType
import com.android.build.gradle.internal.dsl.ProductFlavor
import com.android.builder.core.DefaultApiVersion
import com.android.builder.core.DefaultProductFlavor

import org.gradle.api.NamedDomainObjectContainer
import java.lang.System

buildscript {
    //Temporary hack until Android plugin has proper support
    System.setProperty("com.android.build.gradle.overrideVersionCheck",  "true")

    //Set Kotlin for use in runtime application
    val extra = project.extensions.extraProperties
    extra["kotlinVersion"] = "1.1.0-dev-998"
    extra["repo"] = "https://repo.gradle.org/gradle/repo"

    repositories {
        jcenter()
        maven { setUrl(extra["repo"]) }
    }
    dependencies {
        classpath("com.android.tools.build:gradle:2.2.0-alpha4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${extra["kotlinVersion"]}")
    }
}

repositories {
    jcenter()
    maven { setUrl(extra["repo"]) }
}


apply {
    plugin<AppPlugin>()
    plugin("kotlin-android")
}

configure<AppExtension> {
    buildToolsVersion("23.0.3")
    compileSdkVersion(23)

    defaultConfigExtension {
        setMinSdkVersion(15)
        setTargetSdkVersion(23)

        applicationId = "com.example.kotlingradle"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypesExtension {
        release {
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }
}

dependencies {
    compile("com.android.support:appcompat-v7:23.4.0")
    compile("com.android.support.constraint:constraint-layout:1.0.0-alpha3")
    compile("org.jetbrains.kotlin:kotlin-stdlib:${extra["kotlinVersion"]}")
}

//Extension functions to allow comfortable references
fun <T> NamedDomainObjectContainer<T>.release(func: T.() -> Unit) = findByName("release").apply(func)

fun AppExtension.defaultConfigExtension(func: DefaultProductFlavor.() -> Unit) = defaultConfig.apply(func)

fun AppExtension.buildTypesExtension(func: NamedDomainObjectContainer<BuildType>.() -> Unit) = buildTypes { func.invoke(it) }

fun DefaultProductFlavor.setMinSdkVersion(value: Int) = setMinSdkVersion(DefaultApiVersion.create(value))

fun DefaultProductFlavor.setTargetSdkVersion(value: Int) = setTargetSdkVersion(DefaultApiVersion.create(value))