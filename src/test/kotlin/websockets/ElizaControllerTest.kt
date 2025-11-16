@file:Suppress("NoWildcardImports")

package websockets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

@ActiveProfiles("stomp")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ElizaControllerTest {
    @LocalServerPort
    var port: Int = 0

    private val queue: BlockingQueue<String> = LinkedBlockingDeque()

    @Test
    fun `test eliza response`() {
        val stompClient = WebSocketStompClient(SockJsClient(listOf(WebSocketTransport(StandardWebSocketClient()))))
        stompClient.messageConverter =
            org.springframework.messaging.converter
                .StringMessageConverter()

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

        // Enviar un mensaje a Eliza
        val msg = "I am always tired"
        session.send("/app/eliza-chat", msg)

        // Esperar la respuesta
        val response = queue.poll(2, TimeUnit.SECONDS)
        println("Received response: $response")

        // Verificar que contiene algo de la respuesta de Eliza
        assert(response!!.isNotEmpty())
        assertEquals(response, "Can you think of a specific example?")
    }
}
