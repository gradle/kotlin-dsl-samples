package tools

import org.junit.Test
import org.junit.Assert.assertThat
import org.hamcrest.CoreMatchers.equalTo

class BuildHelperTest {

    @Test
    fun canTestBuildHelper() {
        assertThat(sayHelloTo("Gradle"), equalTo("Hello Gradle"))
    }
}
