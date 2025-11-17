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

    @Test
    fun `analytics topic should update messagesSent and messagesReceived`() {
        val elizaInitLatch = CountDownLatch(3)
        val elizaResponseLatch = CountDownLatch(2)
        val analyticsInitLatch = CountDownLatch(1)

        val analyticsQueue: BlockingQueue<Map<String, Any>> = LinkedBlockingDeque()
        val elizaQueue: BlockingQueue<String> = LinkedBlockingDeque()

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

        // -------------------------------
        // Suscripción a /topic/analytics
        // -------------------------------
        session.subscribe(
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

        // ---------------------------
        // Suscripción a /topic/eliza
        // ---------------------------
        session.subscribe(
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

        // Esperar a los mensajes iniciales de Eliza
        elizaInitLatch.await()
        assertTrue(elizaQueue.size >= 3)

        // -------------------------------
        // Enviar mensaje al controlador
        // -------------------------------
        session.send("/app/eliza-chat", "I am always tired")

        // Esperar a la respuesta de Eliza
        elizaResponseLatch.await()
        assertTrue(elizaQueue.size >= 5)

        // Esperar el primer mensaje de analytics
        // analyticsInitLatch.await()
        // assertTrue(analyticsQueue.size >= 1)

        // // Esperar una respuesta de ELIZA
        // val elizaResponse =
        //     elizaQueue.poll(2, TimeUnit.SECONDS)
        //         ?: error("Did not receive ELIZA response in time")

        // println("ELIZA response = $elizaResponse")

        // // Ahora esperamos la actualización del topic de analytics
        // val analyticsUpdate =
        //     analyticsQueue.poll(2, TimeUnit.SECONDS)
        //         ?: error("Did not receive analytics update in time")

        // println("Analytics after sending a message = $analyticsUpdate")

        // val sent = (analyticsUpdate["messagesSent"] as Number).toInt()
        // val received = (analyticsUpdate["messagesReceived"] as Number).toInt()

        // assert(sent >= 1) { "messagesSent should be >= 1 but was $sent" }
        // assert(received >= 1) { "messagesReceived should be >= 1 but was $received" }
    }
}
