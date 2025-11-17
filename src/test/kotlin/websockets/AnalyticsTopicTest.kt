package websockets

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
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
                .connect(
                    "ws://localhost:$port/ws",
                    WebSocketHttpHeaders(),
                    object : StompSessionHandlerAdapter() {},
                ).get(1, TimeUnit.SECONDS)

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

        session.disconnect()
    }

    @Test
    fun `analytics topic should update messagesSent and messagesReceived`() {
        val elizaInitLatch = CountDownLatch(3)
        val elizaResponseLatch = CountDownLatch(2)
        val analyticsInitLatch = CountDownLatch(1)

        val analyticsQueue: BlockingQueue<Map<String, Any>> = LinkedBlockingDeque()
        val elizaQueue: BlockingQueue<String> = LinkedBlockingDeque()

        var initialMessagesCount = 0

        val stompClientEliza = WebSocketStompClient(StandardWebSocketClient())
        stompClientEliza.messageConverter = StringMessageConverter()
        val stompClientAnalytics = WebSocketStompClient(StandardWebSocketClient())
        stompClientAnalytics.messageConverter = MappingJackson2MessageConverter()

        val sessionEliza: StompSession =
            stompClientEliza
                .connect(
                    "ws://localhost:$port/ws",
                    WebSocketHttpHeaders(),
                    object : StompSessionHandlerAdapter() {},
                ).get(1, TimeUnit.SECONDS)

        val sessionAnalytics: StompSession =
            stompClientAnalytics
                .connect(
                    "ws://localhost:$port/ws",
                    WebSocketHttpHeaders(),
                    object : StompSessionHandlerAdapter() {},
                ).get(1, TimeUnit.SECONDS)

        // ---------------------------
        // Suscripción a /topic/eliza
        // ---------------------------
        sessionEliza.subscribe(
            "/topic/eliza",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = String::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    val message = payload as String
                    elizaQueue.add(message)

                    if (initialMessagesCount < 3) {
                        elizaInitLatch.countDown()
                        initialMessagesCount++
                    } else {
                        elizaResponseLatch.countDown()
                    }
                }
            },
        )

        // -------------------------------
        // Suscripción a /topic/analytics
        // -------------------------------
        sessionAnalytics.subscribe(
            "/topic/analytics",
            object : StompFrameHandler {
                override fun getPayloadType(headers: StompHeaders): Type = Map::class.java

                override fun handleFrame(
                    headers: StompHeaders,
                    payload: Any?,
                ) {
                    analyticsQueue.add(payload as Map<String, Any>)
                    analyticsInitLatch.countDown()
                }
            },
        )

        // Esperar a los mensajes iniciales de Eliza
        elizaInitLatch.await()
        assertTrue(elizaQueue.size >= 3)

        // -------------------------------
        // Enviar mensaje al controlador
        // -------------------------------
        sessionEliza.send("/app/eliza-chat", "I am always tired")

        // Esperar a la respuesta de Eliza
        elizaResponseLatch.await()
        assertTrue(elizaQueue.size >= 5)

        // Esperar el primer mensaje de analytics
        analyticsInitLatch.await()
        assertTrue(analyticsQueue.size >= 1)

        val analyticsUpdate =
            analyticsQueue.poll(2, TimeUnit.SECONDS)
                ?: error("Did not receive analytics update in time")

        val messagesSent = (analyticsUpdate?.get("messagesSent") as? Number)?.toInt() ?: 0
        assert(messagesSent >= 1)

        val messagesReceived = (analyticsUpdate?.get("messagesReceived") as? Number)?.toInt() ?: 0
        assert(messagesReceived >= 1)

        sessionEliza.disconnect()
        sessionAnalytics.disconnect()
    }
}
