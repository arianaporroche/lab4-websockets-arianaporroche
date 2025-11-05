@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ElizaServerTest {
    @LocalServerPort
    private var port: Int = 0

    @AfterEach
    fun tearDown() {
        // Cierra sesiones del servidor
        ElizaEndpoint.activeSessions.forEach { session ->
            if (session.isOpen) session.close()
        }
        ElizaEndpoint.activeSessions.clear()
    }

    @Test
    fun onOpen() {
        /*
         * ***************************************************
         *  MESSAGE FLOW WITHOUT BROADCAST
         *  (the order of the messages may not be identical)
         * ***************************************************
         * (onOpen from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         *
         * ***************************************************
         *  MESSAGE FLOW WITH BROADCAST
         *  (the order of the messages may not be identical)
         * ***************************************************
         * (onOpen from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         *      -> A new client joined. Total: 1
         */
        logger.info { "This is the test worker" }

        // ****Code with broadcast****
        val latch = CountDownLatch(4)
        // ****Code without broadcast****
        // val latch = CountDownLatch(3)

        val list = mutableListOf<String>()

        val client = SimpleClient(list, latch)
        client.connect("ws://localhost:$port/eliza")
        latch.await()

        // ****Code with broadcast****
        assertEquals(4, list.size)
        // ****Code without broadcast****
        // assertEquals(3, list.size)

        assertEquals("The doctor is in.", list[0])
    }

    @Test
    fun onChat() {
        /*
         * ***************************************************
         *  MESSAGE FLOW WITHOUT BROADCAST
         *  (the order of the messages may not be identical)
         * ***************************************************
         * (onOpen from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         * (onMsg from ComplexClient)
         *      <- I am always tired
         * (onMsg from ElizaServer)
         *      -> Can you think of a specific example?
         *      -> ---
         *
         * ***************************************************
         *  MESSAGE FLOW WITH BROADCAST
         *  (the order of the messages may not be identical)
         * ***************************************************
         * (onOpen from ElizaServer)
         *      -> The doctor is in.
         *      -> What's on your mind?
         *      -> ---
         *      -> A new client joined. Total: 1
         * (onMsg from ComplexClient)
         *      <- I am always tired
         * (onMsg from ElizaServer)
         *      -> Can you think of a specific example?
         *      -> ---
         *      -> [Session 0]: I am always tired
         */
        logger.info { "Test thread" }

        // ****Code with broadcast****
        val latch = CountDownLatch(6)
        // ****Code without broadcast****
        // val latch = CountDownLatch(4)

        val list = mutableListOf<String>()

        val client = ComplexClient(list, latch)
        client.connect("ws://localhost:$port/eliza")
        latch.await()

        val size = list.size

        // ****Code with broadcast****
        assertTrue(size >= 6)
        assertTrue(
            list[4] == "Can you think of a specific example?" ||
                list[5] == "Can you think of a specific example?",
        )
        // ****Code without broadcast****
        // assertTrue(size >= 4)
        // assertTrue(
        //     list[3] == "Can you think of a specific example?" ||
        //     list[4] == "Can you think of a specific example?"
        // )

        // 1. EXPLAIN WHY size = list.size IS NECESSARY
        //      Because WebSocket communication is asynchronous, and messages
        //      can arrive during the other asserts.
        // 2. REPLACE BY assertXXX expression that checks an interval; assertEquals must not be used;
        //      assertTrue
        // 3. EXPLAIN WHY assertEquals CANNOT BE USED AND WHY WE SHOULD CHECK THE INTERVAL
        //      Because message timing and order are non-deterministic, so the
        //      number of messages received can vary at different time
        // 4. COMPLETE assertEquals(XXX, list[XXX])
    }
}

@ClientEndpoint
class SimpleClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @OnMessage
    fun onMessage(message: String) {
        logger.info { "Client received: $message" }
        list.add(message)
        latch.countDown()
    }
}

@ClientEndpoint
class ComplexClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @OnMessage
    fun onMessage(
        message: String,
        session: Session,
    ) {
        logger.info { "Client received: $message" }
        list.add(message)
        latch.countDown()

        if (list.size == 3 && list.contains("---")) {
            session.basicRemote.sendText("I am always tired")
        }
    }
}

fun Any.connect(uri: String) {
    ContainerProvider.getWebSocketContainer().connectToServer(this, URI(uri))
}
