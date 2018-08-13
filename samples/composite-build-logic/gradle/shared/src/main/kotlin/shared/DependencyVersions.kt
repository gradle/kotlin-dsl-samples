package shared

object DependencyVersions {

    object javax {
        object measure {
            val api = DependencyVersion("javax.measure", "unit-api", "1.0")
            val ri = DependencyVersion("tec.units", "unit-ri", "1.0.3")
        }
    }
}

data class DependencyVersion(val group: String, val name: String, val version: String) {
    val notation: String
        get() = "$group:$name:$version"
}
