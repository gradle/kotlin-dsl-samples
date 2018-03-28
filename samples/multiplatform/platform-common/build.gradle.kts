plugins {
    id("kotlin-platform-common")
}

dependencies {
    implementation(kotlin("stdlib-common"))
    testCompile(kotlin("test-common"))
    testCompile(kotlin("test-annotations-common"))
}
