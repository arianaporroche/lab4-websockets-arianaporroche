@file:Suppress("NoWildcardImports")

package websockets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ActiveProfiles("stomp")
@SpringBootTest(webEnvironment = RANDOM_PORT)
class SessionsElizaControllerTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `test active sessions with initial messages`() {
        val clients = 2
        val messagesPerClient = 3 // "The doctor is in.", "What's on your mind?", "---"
        val latch = CountDownLatch(clients * messagesPerClient)
        val messages = mutableListOf<String>()

        fun createClient(): StompSession {
            val client = WebSocketStompClient(StandardWebSocketClient())
            client.messageConverter = StringMessageConverter()

            return client
                .connect(
                    "ws://localhost:$port/ws",
                    WebSocketHttpHeaders(),
                    object : StompSessionHandlerAdapter() {
                        override fun afterConnected(
                            session: StompSession,
                            headers: StompHeaders,
                        ) {
                            session.subscribe(
                                "/topic/eliza",
                                object : StompFrameHandler {
                                    override fun getPayloadType(headers: StompHeaders): Type = String::class.java

                                    override fun handleFrame(
                                        headers: StompHeaders,
                                        payload: Any?,
                                    ) {
                                        messages.add(payload as String)
                                        latch.countDown()
                                    }
                                },
                            )
                        }
                    },
                ).get(1, TimeUnit.SECONDS)
        }

        val session1 = createClient()
        val session2 = createClient()

        // Esperar a que ambos clientes reciban los 3 mensajes iniciales
        val completed = latch.await(3, TimeUnit.SECONDS)
        assertEquals(true, completed, "Both clients should receive 3 initial messages each")

        // Verificar que los mensajes iniciales estén presentes
        assertTrue(messages.contains("The doctor is in."))
        assertTrue(messages.contains("What's on your mind?"))
        assertTrue(messages.contains("---"))

        // Desconectar clientes
        session1.disconnect()
        session2.disconnect()
    }
}
