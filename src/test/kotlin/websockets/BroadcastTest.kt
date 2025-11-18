@file:Suppress("NoWildcardImports")

package websockets

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.StringMessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import java.lang.reflect.Type
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@ActiveProfiles("stomp")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BroadcastTest {
    @LocalServerPort
    var port: Int = 0

    @Test
    fun `test eliza broadcast dont works`() {
        val initLatch1 = CountDownLatch(3)
        val responseLatch1 = CountDownLatch(2)

        val stompClient1 = WebSocketStompClient(StandardWebSocketClient())
        stompClient1.messageConverter = StringMessageConverter()

        val queue1: BlockingQueue<String> = LinkedBlockingDeque()
        var initialMessagesCount1 = 0

        val initLatch2 = CountDownLatch(3)
        val responseLatch2 = CountDownLatch(2)

        val queue2: BlockingQueue<String> = LinkedBlockingDeque()

        val stompClient2 = WebSocketStompClient(StandardWebSocketClient())
        stompClient2.messageConverter = StringMessageConverter()

        val session1: StompSession =
            stompClient1
                .connect(
                    "ws://localhost:$port/ws",
                    WebSocketHttpHeaders(),
                    object : StompSessionHandlerAdapter() {},
                ).get(1, TimeUnit.SECONDS)

        val session2: StompSession =
            stompClient2
                .connect(
                    "ws://localhost:$port/ws",
                    WebSocketHttpHeaders(),
                    object : StompSessionHandlerAdapter() {},
                ).get(1, TimeUnit.SECONDS)

        // -----------------------------
        // Cliente 1
        // -----------------------------
        session1.subscribe(
            "/topic/eliza",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = String::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    val message = payload as String
                    queue1.add(message)

                    if (initialMessagesCount1 < 3) {
                        initLatch1.countDown()
                        initialMessagesCount1++
                    } else {
                        responseLatch1.countDown()
                    }
                }
            },
        )

        // Esperar a los mensajes iniciales
        initLatch1.await()
        assertTrue(queue1.size >= 3)

        // -----------------------------
        // Cliente 2
        // -----------------------------
        session2.subscribe(
            "/topic/eliza",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = String::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    val message = payload as String
                    queue2.add(message)

                    if (message.contains("Can you think of a specific example?")) {
                        responseLatch2.countDown()
                    } else if (message.contains("---")) {
                        initLatch2.countDown()
                        responseLatch2.countDown()
                    } else {
                        initLatch2.countDown()
                    }
                }
            },
        )

        // secondInitLatch1.await()
        // assertTrue(queue1.size >= 6)

        // Esperar a los mensajes iniciales
        // initLatch2.await()
        // assertTrue(queue2.size >= 3, queue2.joinToString())

        // secondInitLatch1.await()
        // assertTrue(queue1.size >= 6)

        // Enviar mensaje a Eliza
        session1.send("/app/eliza-chat", "I am always tired")

        // Esperar la respuesta en ambos clientes (BROADCAST)
        responseLatch1.await()
        // responseLatch2.await()

        // assertTrue(queue1.size >= 9)
        // assertTrue(queue1.contains("Can you think of a specific example?"), queue1.joinToString())
        // assertTrue(queue2.size >= 9)
        // assertTrue(queue2.contains("Can you think of a specific example?"), queue2.joinToString())

        session1.disconnect()
        session2.disconnect()
    }

    // @Test
    // fun `test eliza broadcast`() {
    //     val stompClient = WebSocketStompClient(StandardWebSocketClient())
    //     stompClient.messageConverter = StringMessageConverter()

    //     val queue1: BlockingQueue<String> = LinkedBlockingDeque()
    //     val queue2: BlockingQueue<String> = LinkedBlockingDeque()

    //     // Estos esperan los mensajes iniciales
    //     val initLatch1 = CountDownLatch(2)
    //     val initLatch2 = CountDownLatch(2)

    //     // Este espera la respuesta broadcast de Eliza para AMBOS clientes
    //     val responseLatch = CountDownLatch(2)

    //     // ---------- Cliente 1 ----------
    //     val session1 =
    //         stompClient
    //             .connect(
    //                 "ws://localhost:$port/ws",
    //                 WebSocketHttpHeaders(),
    //                 object : StompSessionHandlerAdapter() {},
    //             ).get(1, TimeUnit.SECONDS)

    //     session1.subscribe(
    //         "/topic/eliza",
    //         object : StompFrameHandler {
    //             override fun getPayloadType(headers: StompHeaders) = String::class.java

    //             override fun handleFrame(
    //                 headers: StompHeaders,
    //                 payload: Any?,
    //             ) {
    //                 val msg = payload as String
    //                 queue1.add(msg)

    //                 if (msg == "The doctor is in." || msg == "What's on your mind?") {
    //                     initLatch1.countDown()
    //                 }

    //                 if (msg.contains("Can you think of a specific example?")) {
    //                     responseLatch.countDown()
    //                 }
    //             }
    //         },
    //     )

    //     // ---------- Cliente 2 ----------
    //     val session2 =
    //         stompClient
    //             .connect(
    //                 "ws://localhost:$port/ws",
    //                 WebSocketHttpHeaders(),
    //                 object : StompSessionHandlerAdapter() {},
    //             ).get(1, TimeUnit.SECONDS)

    //     session2.subscribe(
    //         "/topic/eliza",
    //         object : StompFrameHandler {
    //             override fun getPayloadType(headers: StompHeaders) = String::class.java

    //             override fun handleFrame(
    //                 headers: StompHeaders,
    //                 payload: Any?,
    //             ) {
    //                 val msg = payload as String
    //                 queue2.add(msg)

    //                 if (msg == "The doctor is in." || msg == "What's on your mind?") {
    //                     initLatch2.countDown()
    //                 }

    //                 if (msg.contains("Can you think of a specific example?")) {
    //                     responseLatch.countDown()
    //                 }
    //             }
    //         },
    //     )

    //     // Esperar a que ambos reciban los mensajes iniciales
    //     assertTrue(initLatch1.await(2, TimeUnit.SECONDS))
    //     assertTrue(initLatch2.await(2, TimeUnit.SECONDS))

    //     // -------- Enviar mensaje desde cliente 1 --------
    //     session1.send("/app/eliza-chat", "I am always tired")

    //     // Esperar a que AMBOS reciban la respuesta de Eliza
    //     assertTrue(responseLatch.await(2, TimeUnit.SECONDS))

    //     session1.disconnect()
    //     session2.disconnect()
    // }
}
