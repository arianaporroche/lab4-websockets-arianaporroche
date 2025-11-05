@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class SessionsTest {
    @LocalServerPort
    private var port: Int = 0

    @BeforeEach
    fun setUp() {
        // Limpia el estado por si quedó algo de otro test
        ElizaEndpoint.activeSessions.clear()
    }

    @AfterEach
    fun tearDown() {
        // Cierra conexiones abiertas y limpia
        ElizaEndpoint.activeSessions.forEach { it.close() }
        ElizaEndpoint.activeSessions.clear()
    }

    @Test
    fun onOpen() {
        logger.info { "Test with 2 clients" }

        val latch = CountDownLatch(6)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()

        val client1 = SimpleClient(list1, latch)
        val client2 = SimpleClient(list2, latch)

        client1.connect("ws://localhost:$port/eliza")
        client2.connect("ws://localhost:$port/eliza")

        latch.await()

        // Verificamos que haya dos sesiones activas en el servidor
        val activeCount = ElizaEndpoint.activeSessions.size
        logger.info { "Sesiones activas en el servidor: $activeCount" }

        assertEquals(2, activeCount)
    }
}
