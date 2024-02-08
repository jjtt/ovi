package fi.torma.ovi

import kotlinx.coroutines.test.runTest
import org.junit.Test

class ParseAuthHeaderTest {

    @Test
    fun testCoroutine() = runTest {
        println("Running testCoroutine")
        val result = requestInputStatus()
        println(result)
        val result2 = requestSwitchOn()
        println(result2)
    }
}