import org.gradle.api.JavaVersion
import org.gradle.api.plugins.JavaPluginExtension

fun JavaPluginExtension.defaultCompatibility() {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}