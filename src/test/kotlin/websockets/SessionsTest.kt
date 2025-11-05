@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
        // Cierra cualquier sesión abierta antes de empezar
        ElizaEndpoint.activeSessions.forEach { session ->
            try {
                if (session.isOpen) {
                    session.close()
                }
            } catch (e: Exception) {
                logger.warn(e) { "Error closing session ${session.id}" }
            }
        }
        ElizaEndpoint.activeSessions.clear()
        Thread.sleep(50)
    }

    @AfterEach
    fun tearDown() {
        // Cierra todas las sesiones abiertas al terminar el test
        ElizaEndpoint.activeSessions.forEach { session ->
            try {
                if (session.isOpen) {
                    session.close()
                }
            } catch (e: Exception) {
                logger.warn(e) { "Error closing session ${session.id}" }
            }
        }
        ElizaEndpoint.activeSessions.clear()
        Thread.sleep(50)
    }

    @Test
    fun onOpen2SimpleClients() {
        /*
         * ***************************************************
         *  MESSAGE FLOW WITH BROADCAST
         *  (the order of the messages may not be identical)
         * ***************************************************
         * (onOpen Session 0 from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         *      -> A new client joined. Total: 1
         * (onOpen Session 1 from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         *      -> A new client joined. Total: 2
         * (onMsg Session 0 from ElizaServer)
         *      -> A new client joined. Total: 2
         */

        logger.info { "Test with 2 simple clients" }

        val latch = CountDownLatch(9)
        val list0 = mutableListOf<String>()
        val list1 = mutableListOf<String>()

        val client0 = SimpleClient(list0, latch)
        val client1 = SimpleClient(list1, latch)

        client0.connect("ws://localhost:$port/eliza")
        client1.connect("ws://localhost:$port/eliza")

        latch.await()

        // Verificamos que haya dos sesiones activas en el servidor
        val activeCount = ElizaEndpoint.activeSessions.size
        assertEquals(2, activeCount)

        assertTrue(list0.contains("A new client joined. Total: 1"))
        assertTrue(list0.contains("A new client joined. Total: 2"))
        assertTrue(list1.contains("A new client joined. Total: 2"))
    }

    @Test
    fun onOpen2ComplexClients() {
        /*
         * ***************************************************
         *  MESSAGE FLOW WITH BROADCAST
         *  (the order of the messages may not be identical)
         * ***************************************************
         * (onOpen Session 0 from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         *      -> A new client joined. Total: 1
         * (onOpen Session 1 from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         *      -> A new client joined. Total: 2
         * (onMsg Session 0 from ElizaServer)
         *      -> A new client joined. Total: 2
         * (onMsg Session 0 from ComplexClient 0)
         *      <- I am always tired
         * (onMsg Session 0 from ElizaServer)
         *      -> Can you think of a specific example?
         *      -> ---
         *      -> [Session 0]: I am always tired
         * (onMsg Session 1 from ElizaServer)
         *      -> [Session 0]: I am always tired
         * (onMsg Session 1 from ComplexClient 1)
         *      <- I am always tired
         * (onMsg Session 1 from ElizaServer)
         *      -> Can you think of a specific example?
         *      -> ---
         *      -> [Session 1]: I am always tired
         * (onMsg Session 0 from ElizaServer)
         *      -> [Session 1]: I am always tired
         */

        logger.info { "Test with 2 complex clients" }

        val latch = CountDownLatch(16)
        val list0 = mutableListOf<String>()
        val list1 = mutableListOf<String>()

        val client0 = ComplexClient(list0, latch)
        val client1 = ComplexClient(list1, latch)

        client0.connect("ws://localhost:$port/eliza")
        client1.connect("ws://localhost:$port/eliza")

        latch.await()

        // Verificamos que haya dos sesiones activas en el servidor
        val activeCount = ElizaEndpoint.activeSessions.size
        assertEquals(2, activeCount)

        assertTrue(list0.contains("Can you think of a specific example?"))
        // assertTrue(list0.any { it.contains("I am always tired") }, "List 0: $list0")
        assertTrue(list0.contains("[Session 1]: I am always tired"), "List 0: $list0")

        assertTrue(list1.contains("Can you think of a specific example?"))
        // assertTrue(list1.any { it.contains("I am always tired") }, "List 1: $list1")
        assertTrue(list1.contains("[Session 1]: I am always tired"), "List 1: $list1")
    }
}
