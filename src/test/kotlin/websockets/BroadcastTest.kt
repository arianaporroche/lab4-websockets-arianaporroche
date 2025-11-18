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

        // Enviar mensaje a Eliza
        session1.send("/app/eliza-chat", "I am always tired")

        // Esperar la respuesta en ambos clientes (BROADCAST)
        responseLatch1.await()
        responseLatch2.await()

        assertTrue(queue1.contains("Can you think of a specific example?"))
        assertTrue(queue2.contains("Can you think of a specific example?"))

        session1.disconnect()
        session2.disconnect()
    }
}
