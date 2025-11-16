@file:Suppress("NoWildcardImports")

package websockets

import org.junit.jupiter.api.Assertions.assertEquals
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
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@ActiveProfiles("stomp")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ElizaTopicTest {
    @LocalServerPort
    var port: Int = 0

    private val queue: BlockingQueue<String> = LinkedBlockingDeque()

    @Test
    fun `test eliza response`() {
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
                    queue.add(payload as String)
                }
            },
        )

        // Enviar mensaje a Eliza
        val msg = "I am always tired"
        session.send("/app/eliza-chat", msg)

        // Esperar hasta recibir la respuesta que no sea un mensaje inicial
        var response: String?
        do {
            response = queue.poll(2, TimeUnit.SECONDS)
        } while (response in listOf("The doctor is in.", "What's on your mind?", "---"))

        println("Received response: $response")
        assert(response!!.isNotEmpty())
        assertEquals("Can you think of a specific example?", response)
    }
}
