plugins {
    `kotlin-dsl`

    // see buildSrc/src/main/kotlin/allopen-compiler-plugin.kt
    id("org.jetbrains.kotlin.plugin.allopen") version "1.3.0"
}

// see buildSrc/src/main/kotlin/allopen-compiler-plugin.kt
allOpen {
    annotation("my.AllOpen")
}


repositories {
    jcenter()
}
