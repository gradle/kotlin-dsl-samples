import sample.Constant
import sample.Greeter
import kotlin.test.Test
import kotlin.test.assertEquals

class SampleTests {

    @Test
    fun returnsGreetingsMessage() {
        assertEquals(
            expected = "Hello, Kotlin!",
            actual = Greeter("Kotlin").greet(),
            message = "Expected value differ"
        )
    }

    @Test
    fun returnsGivenValue() {
        assertEquals(
            expected = "Hello, Kotlin!",
            actual = Constant("Hello, Kotlin!").value(),
            message = "Expected value differ"
        )
    }

}