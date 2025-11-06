@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class AnalyticsIntegrationTest {
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
    fun testAnalyticsInitialMetrics() {
        // 1. Creamos un cliente /analytics y comprobamos:
        //      - no hay ningún cliente eliza conectado
        //      - sólo hay 1 cliente analytics conectado
        val latchAnalytics = CountDownLatch(1)
        val listAnalytics = mutableListOf<String>()

        val clientAnalytics = TestClient(listAnalytics, latchAnalytics)
        clientAnalytics.connect("ws://localhost:$port/analytics")

        latchAnalytics.await()

        assertTrue(listAnalytics.isNotEmpty(), "Debe recibir al menos un mensaje inicial")
        val json = Json.parseToJsonElement(listAnalytics.first()).jsonObject

        val activeElizaClients = json["activeElizaClients"]?.toString()?.toInt()
        assertEquals(0, activeElizaClients)

        val analyticsConnectionsEver = json["analyticsConnectionsEver"]?.toString()?.toInt()
        assertEquals(1, analyticsConnectionsEver)

        // 2. Creamos un cliente /eliza y comprobamos:
        //      - hay 1 cliente eliza conectado
        //      - se han recibido al menos 6 mensajes
        //      - en /analytics se han actualizado los campos
        val latchEliza = CountDownLatch(7)
        val listEliza = mutableListOf<String>()

        val clientEliza = ComplexClient(listEliza, latchEliza)
        clientEliza.connect("ws://localhost:$port/eliza")

        latchEliza.await()

        val activeElizaClientsAfter = ElizaEndpoint.activeSessions.size
        assertEquals(1, activeElizaClientsAfter)

        assertTrue(listEliza.size >= 6)

        logger.info { "List de analytics: $listAnalytics" }
        val lastJson = Json.parseToJsonElement(listAnalytics.last()).jsonObject

        val lastActiveElizaClients = lastJson["activeElizaClients"]?.toString()?.toInt() ?: 0
        assertEquals(1, lastActiveElizaClients)

        val lastAnalyticsConnectionsEver = lastJson["analyticsConnectionsEver"]?.toString()?.toInt() ?: 0
        assertEquals(1, lastAnalyticsConnectionsEver)

        val lastMessagesReceived = lastJson["messagesReceived"]?.toString()?.toInt() ?: 0
        assertTrue(lastMessagesReceived >= 1)

        val lastMessagesSent = lastJson["messagesSent"]?.toString()?.toInt() ?: 0
        assertTrue(lastMessagesSent >= 3)

        val lastMessage = lastJson["lastMessage"]?.jsonPrimitive?.content
        assertEquals("I am always tired", lastMessage)
    }
}

/**
 * Cliente de prueba para recibir mensajes del endpoint /analytics
 */
@ClientEndpoint
class TestClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @OnMessage
    fun onMessage(
        message: String,
        session: Session,
    ) {
        list.add(message)
        latch.countDown()
    }
}
