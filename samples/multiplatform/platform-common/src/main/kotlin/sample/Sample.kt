package sample

expect class Greeter(name: String) {
    fun greet(): String
}

class Constant<out T : Any>(
    private val origin: () -> T
) {

    constructor(origin: T) : this({ origin })

    fun value(): T = origin()
}
