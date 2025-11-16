@file:Suppress("NoWildcardImports", "WildcardImport", "SpreadOperator")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.CloseReason
import jakarta.websocket.CloseReason.CloseCodes
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.RemoteEndpoint
import jakarta.websocket.Session
import jakarta.websocket.server.ServerEndpoint
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import org.springframework.web.socket.server.standard.ServerEndpointExporter
import java.util.Locale
import java.util.Scanner

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Profile("normal")
@Configuration(proxyBeanMethods = false)
class NormalWebSocketConfig {
    @Bean
    fun serverEndpoint() = ServerEndpointExporter()
}

@Profile("stomp")
@Configuration
@EnableWebSocketMessageBroker
class StompWebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic/")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()
    }
}

private val logger = KotlinLogging.logger {}

/**
 * If the websocket connection underlying this [RemoteEndpoint] is busy sending a message when a call is made to send
 * another one, for example if two threads attempt to call a send method concurrently, or if a developer attempts to
 * send a new message while in the middle of sending an existing one, the send method called while the connection
 * is already busy may throw an [IllegalStateException].
 *
 * This method wraps the call to [RemoteEndpoint.Basic.sendText] in a synchronized block to avoid this exception.
 */
fun RemoteEndpoint.Basic.sendTextSafe(message: String) {
    synchronized(this) {
        sendText(message)
    }
}

@Profile("normal")
@ServerEndpoint("/eliza")
@Component
class ElizaEndpoint {
    private val eliza = Eliza()

    /**
     * Successful connection
     *
     * @param session
     */
    @OnOpen
    fun onOpen(session: Session) {
        logger.info { "Server Connected ... Session ${session.id}" }
        with(session.basicRemote) {
            sendTextSafe("The doctor is in.")
            sendTextSafe("What's on your mind?")
            sendTextSafe("---")
        }
    }

    /**
     * Connection closure
     *
     * @param session
     */
    @OnClose
    fun onClose(
        session: Session,
        closeReason: CloseReason,
    ) {
        logger.info { "Session ${session.id} closed because of $closeReason" }
    }

    /**
     * Message received
     *
     * @param message
     */
    @OnMessage
    fun onMsg(
        message: String,
        session: Session,
    ) {
        logger.info { "Server Message ... Session ${session.id}" }
        val currentLine = Scanner(message.lowercase(Locale.getDefault()))
        if (currentLine.findInLine("bye") == null) {
            logger.info { "Server received \"${message}\"" }
            runCatching {
                if (session.isOpen) {
                    with(session.basicRemote) {
                        sendTextSafe(eliza.respond(currentLine))
                        sendTextSafe("---")
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while sending message" }
                session.close(CloseReason(CloseCodes.CLOSED_ABNORMALLY, "I'm sorry, I didn't understand that."))
            }
        } else {
            session.close(CloseReason(CloseCodes.NORMAL_CLOSURE, "Alright then, goodbye!"))
        }
    }

    @OnError
    fun onError(
        session: Session,
        errorReason: Throwable,
    ) {
        logger.error(errorReason) { "Session ${session.id} closed because of ${errorReason.javaClass.name}" }
    }
}

@Profile("stomp")
@Controller
class ElizaController(
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val eliza = Eliza()

    // Conjunto para llevar el conteo de sesiones activas
    companion object {
        val activeSessions: MutableSet<String> = mutableSetOf()
    }

    @MessageMapping("/eliza-chat") // Ruta de entrada
    fun receiveMessage(
        message: String,
        headers: org.springframework.messaging.MessageHeaders,
    ) {
        // Obtener sessionId
        val sessionId = headers["simpSessionId"] as? String ?: return
        // Registrar la sesión si no estaba
        activeSessions.add(sessionId)
        logger.info { "Active sessions: ${activeSessions.size}" }
        logger.info { "Received message: $message from $sessionId" }

        val scanner = Scanner(message.lowercase(Locale.getDefault()))
        if (scanner.findInLine("bye") != null) {
            // Opcional: enviar un mensaje de despedida
            messagingTemplate.convertAndSend("/topic/eliza", "Alright then, goodbye!")
            // Remover la sesión
            activeSessions.remove(sessionId)
            return
        }

        val response = eliza.respond(scanner)
        logger.info { "Sending response: $response" }

        // Enviar la respuesta a todos los suscritos al topic /topic/eliza
        messagingTemplate.convertAndSend("/topic/eliza", response)
    }
}

@Profile("stomp")
@Component
class ElizaSessionInitializer(
    private val messagingTemplate: SimpMessagingTemplate,
) {
    @EventListener
    fun handleSessionSubscribeEvent(event: SessionSubscribeEvent) {
        val destination = event.message.headers["simpDestination"] as? String
        if (destination == "/topic/eliza") {
            // Enviar 3 mensajes iniciales al topic general
            messagingTemplate.convertAndSend("/topic/eliza", "The doctor is in.")
            messagingTemplate.convertAndSend("/topic/eliza", "What's on your mind?")
            messagingTemplate.convertAndSend("/topic/eliza", "---")
        }
    }
}

// @ServerEndpoint("/analytics")
// @Component
// class AnalyticsEndpoint {
//     companion object {
//         val dashboardSessions: MutableSet<Session> = CopyOnWriteArraySet()
//         var analyticsConnectionsEver = 0
//         var clientsDisconnected = 0
//         var messagesReceived = 0
//         var messagesSent = 0
//         var lastMessage: String? = null

//         fun messageReceived(msg: String) {
//             messagesReceived++
//             lastMessage = msg
//             sendMetricsToAll()
//         }

//         fun messageSent() {
//             messagesSent++
//             sendMetricsToAll()
//         }

//         fun sendMetricsToAll() {
//             val metrics = getMetricsJson()
//             for (s in dashboardSessions) {
//                 if (s.isOpen) s.basicRemote.sendTextSafe(metrics)
//             }
//         }

//         private fun sendMetrics(session: Session) {
//             if (session.isOpen) {
//                 session.basicRemote.sendTextSafe(getMetricsJson())
//             }
//         }

//         private fun getMetricsJson(): String =
//             """
//             {
//                 "activeElizaClients": ${ElizaEndpoint.activeSessions.size},
//                 "analyticsConnectionsEver": $analyticsConnectionsEver,
//                 "clientsDisconnected": $clientsDisconnected,
//                 "messagesReceived": $messagesReceived,
//                 "messagesSent": $messagesSent,
//                 "lastMessage": "${lastMessage ?: ""}"
//             }
//             """.trimIndent()
//     }

//     @OnOpen
//     fun onOpen(session: Session) {
//         dashboardSessions.add(session)
//         analyticsConnectionsEver++
//         sendMetrics(session)
//     }

//     @OnClose
//     fun onClose(session: Session) {
//         dashboardSessions.remove(session)
//         clientsDisconnected++
//         sendMetricsToAll()
//     }
// }
