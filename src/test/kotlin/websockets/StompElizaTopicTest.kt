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
class StompElizaTopicTest {
    @LocalServerPort
    var port: Int = 0

    @Test
    fun `test eliza response`() {
        val initLatch = CountDownLatch(3)
        val responseLatch = CountDownLatch(2)

        val queue: BlockingQueue<String> = LinkedBlockingDeque()
        var initialMessagesCount = 0

        val stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = StringMessageConverter()

        val session: StompSession =
            stompClient
                .connect(
                    "ws://localhost:$port/ws",
                    WebSocketHttpHeaders(),
                    object : StompSessionHandlerAdapter() {},
                ).get(1, TimeUnit.SECONDS)

        // Suscribirse al topic
        session.subscribe(
            "/topic/eliza",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = String::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    val message = payload as String
                    queue.add(message)

                    if (initialMessagesCount < 3) {
                        initLatch.countDown()
                        initialMessagesCount++
                    } else {
                        responseLatch.countDown()
                    }
                }
            },
        )

        // Esperar a los mensajes iniciales
        initLatch.await()

        assertTrue(queue.size >= 3)
        assertTrue(queue.contains("The doctor is in."))
        assertTrue(queue.contains("What's on your mind?"))
        assertTrue(queue.contains("---"))

        // Enviar mensaje a Eliza
        session.send("/app/eliza-chat", "I am always tired")

        // Esperar la respuesta
        responseLatch.await()

        assertTrue(queue.size >= 5)
        assertTrue(queue.contains("Can you think of a specific example?"), queue.joinToString())

        session.disconnect()
    }
}
