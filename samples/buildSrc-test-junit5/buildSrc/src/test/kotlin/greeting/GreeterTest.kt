package greeting

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions.assertEquals

class GreeterTest {
    @Test
    fun canSayHelloTo() {
        assertEquals("Hello Gradle", sayHelloTo("Gradle"))
    }
}
