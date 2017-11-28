package samples

import com.google.auto.value.AutoValue

fun main(args: Array<String>) {
    val user = AutoValue_User("Douglas", 42)

    println("Hello $user")
}

@AutoValue
abstract class User {
    abstract val name: String
    abstract val age: Int
}