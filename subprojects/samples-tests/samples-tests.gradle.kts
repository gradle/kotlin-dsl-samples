plugins {
    kotlin("jvm") version embeddedKotlinVersion
    id("org.gradle.kotlin-dsl.ktlint-convention") version "0.4.1"
}

repositories {
    maven(url = "https://repo.gradle.org/gradle/libs")
    jcenter()
}

dependencies {
    testImplementation(gradleApi())
    testImplementation(gradleKotlinDsl())
    testImplementation(gradleTestKit())
    testImplementation("org.gradle:sample-check:0.7.0")
    testImplementation("junit:junit:4.12")
    testImplementation(kotlin("stdlib"))
    testImplementation("org.xmlunit:xmlunit-matchers:2.5.1")
}

tasks {

    val samplesDir = file("../../samples")
    val samplesTestDir = buildDir.resolve("samples")

    val generatedResourcesDir = buildDir.resolve("generated-resources/test")

    val generateTestProperties by registering(WriteProperties::class) {
        property("samplesDir", samplesDir)
        outputFile = generatedResourcesDir.resolve("test.properties")
    }

    sourceSets.test {
        resources.srcDir(files(generatedResourcesDir).builtBy(generateTestProperties))
    }

    val syncSamples by registering(Sync::class) {
        from(samplesDir)
        from(file("src/exemplar/samples"))
        into(samplesTestDir)
    }

    test {
        dependsOn(syncSamples)
        inputs.dir(samplesTestDir).withPathSensitivity(PathSensitivity.RELATIVE)
    }
}
