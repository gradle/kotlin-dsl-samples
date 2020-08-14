package sample

actual class Greeter actual constructor(private val name: String) {
    actual fun greet() = "Hello, $name!"
}