package greeting

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag

class GreeterTest {
    @Test
    @Tag("fooTag")
    fun canSayHelloTo() {
        assertEquals("Hello Gradle", sayHelloTo("Gradle"))
    }
}
