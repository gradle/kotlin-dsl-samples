package greeting

import org.junit.Test
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers.equalTo

class GreeterTest {

    @Test
    fun canTestBuildHelper() {
        assertThat(sayHelloTo("Gradle"), equalTo("Hello Gradle"))
    }

}
