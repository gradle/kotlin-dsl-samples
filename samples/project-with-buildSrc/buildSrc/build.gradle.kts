plugins {
    `kotlin-dsl`
    kotlin("plugin.allopen") version KotlinVersion.CURRENT.toString()
}

// see buildSrc/src/main/kotlin/my/plugin.allopen.kt
allOpen {
    annotation("my.AllOpen")
}


repositories {
    jcenter()
}
