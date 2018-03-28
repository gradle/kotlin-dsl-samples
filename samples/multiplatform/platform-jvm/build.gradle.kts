import org.gradle.internal.impldep.bsh.commands.dir

plugins {
    id("kotlin-platform-jvm")
}

dependencies {
    "expectedBy"(project(":platform-common"))
    "implementation"(kotlin("stdlib-jdk8"))
    "testCompile"(kotlin("test-testng"))
    "testCompile"("org.testng:testng:6.14.2")
}

tasks {
    "test"(Test::class) {
        useTestNG()
    }
}
