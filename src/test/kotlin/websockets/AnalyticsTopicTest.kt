package websockets

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
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
class AnalyticsTopicTest {
    @LocalServerPort
    var port: Int = 0

    private val queue: BlockingQueue<Map<String, Any>> = LinkedBlockingDeque()

    @Test
    fun `analytics topic should receive active session updates`() {
        val stompClient = WebSocketStompClient(StandardWebSocketClient())
        stompClient.messageConverter = MappingJackson2MessageConverter()

        val session: StompSession =
            stompClient
                .connect("ws://localhost:$port/ws", WebSocketHttpHeaders(), object : StompSessionHandlerAdapter() {})
                .get(1, TimeUnit.SECONDS)

        // Suscribirse al topic /topic/analytics
        session.subscribe(
            "/topic/analytics",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    queue.add(payload as Map<String, Any>)
                }
            },
        )

        // Enviar un mensaje a Eliza para crear una sesión
        val msg = "Hello"
        session.send("/app/eliza-chat", msg)

        // Esperar la actualización de analytics
        val analyticsUpdate = queue.poll(2, TimeUnit.SECONDS)
        println("Received analytics update: $analyticsUpdate")

        // Verificar que contiene la clave "activeElizaClients" con valor >= 1
        val activeClients = (analyticsUpdate?.get("activeElizaClients") as? Number)?.toInt() ?: 0
        assert(activeClients >= 1)
    }
}
