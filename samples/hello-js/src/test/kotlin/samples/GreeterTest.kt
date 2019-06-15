import kotlin.test.Test
import kotlin.test.assertEquals

class GreeterTest {
    @Test
    fun greetTest() {
        val greeter = Greeter()
        assertEquals("Hello, world!", greeter.greet("world"))
    }
}
