# Lab 4 WebSocket -- Project Report

**Author:** Ariana Porroche Llorén (874055)

**Date:** 18th November 2025

**Course:** Web Engineering


## Description of Changes

During this lab, I implemented and verified WebSocket-based communication for the ELIZA chatbot server.
Initially, I completed the required task by finalizing the `ElizaServerTest.kt` tests to ensure the server correctly handled `onOpen` and `onChat` events, verifying both the greeting and DOCTOR-style responses.

I tried to interact with the Eliza Server via Postman, connecting to `ws://localhost:8080/eliza`, as in the following image:

| Postman initial tests |
|-----------|
| ![Postman initial tests at ws://localhost:8080/eliza](./postman-initial-tests.png) |


Additionally, I extended the project with three optional enhancements:

### 1. STOMP!:
  - **Description:**
  Added full STOMP protocol support to the WebSocket server and implemented a STOMP-based ELIZA client to test publish/subscribe message handling.

  - **Implementation:**
  Created a new STOMP WebSocket configuration (`StompWebSocketConfig`) enabling `/ws` as the STOMP endpoint and activating a simple broker for `/topic/**`.
  Implemented ElizaController using `@MessageMapping("/eliza-chat")` to receive STOMP messages and broadcast ELIZA responses to `/topic/eliza`.
  Also added an event listener (`ElizaSessionInitializer`) that automatically sends the three initial greeting messages to any client subscribing to `/topic/eliza`.

  - **Functionality:**
  Multiple clients can now subscribe to the same STOMP topic and receive real-time broadcasted ELIZA responses.

  - **Testing:**
  Validated using the `StompElizaTopicTest.kt` integration test, which verifies subscription behavior, initial greeting broadcast, and correct delivery of ELIZA responses through the `/topic/eliza` STOMP channel.

### 5. Real-time Analytics Dashboard:
  - **Description:**
  The analytics system was rebuilt to work entirely over STOMP. Instead of a dedicated WebSocket endpoint, analytics are now published as JSON messages on the STOMP topic `/topic/analytics`. Metrics include: `messagesSent`, `messagesReceived` and `activeElizaClients`.

  - **Implementation:**
  Implemented `AnalyticsPublisher`, a Spring component that tracks analytics counters and publishes updates to `/topic/analytics` using SimpMessagingTemplate. Analytics are triggered automatically whenever a client sends a message or joins or leaves the Eliza topic.

  - **Functionality:**
  Any analytics dashboard or STOMP client subscribed to `/topic/analytics` receives real-time updates reflecting:
    - Active STOMP sessions using the ELIZA chat
    - Total messages sent and received
    - Broadcast events triggered by ELIZA interactions. This makes the analytics fully synchronized with the STOMP-based ELIZA workflow

  - **Testing:**
  Verified through `StompAnalyticsTopicTest.kt`, which simulates STOMP clients subscribing to `/topic/analytics` and sending messages to ELIZA. The tests confirm that:
    - `activeElizaClients` is updated when new sessions appear
    - `messagesSent` and `messagesReceived` increase appropriately
    - Analytics updates are delivered reliably through broadcast to the topic


### 8. Session Management and Broadcast:
  - **Description:**
  Migrated session tracking and message broadcasting to the STOMP-based architecture. Active sessions are now tracked using Spring session IDs, and ELIZA responses are broadcast through the `/topic/eliza` channel.

  - **Implementation:**
  `ElizaController` handles incoming messages and broadcasts responses. `ElizaSessionInitializer` sends the initial three ELIZA messages to any client subscribing to `/topic/eliza`.

  - **Functionality:**
  All connected clients now receive synchronized ELIZA responses and greeting messages. Session counts are updated automatically whenever a client joins, leaves, or sends a message.
  
  - **Testing:**
  Validated with `SessionsTest.kt` (initial greetings & active session tracking) and `BroadcastTest.kt` (ensuring that ELIZA responses are broadcast to all subscribers). These integration tests simulate multiple STOMP clients and confirm consistent real-time behavior.


## Technical Decisions

- **Dual Profiles for WebSocket Modes (normal & STOMP):**
To keep both implementations cleanly separated, I introduced two Spring Boot profiles using `@Profile` annotations on each class and endpoint, and `@ActiveProfiles` inside the STOMP tests:
  - `normal` → Activates the Jakarta WebSocket version (`@ServerEndpoint("/eliza")`) as originally required for the assignment.
  - `stomp` → Activates the Spring WebSocket + STOMP message-broker setup with `/ws` as the endpoint, `/app` as the application prefix, and `/topic/**` as broker destinations.

## Learning Outcomes

Through this lab, I learned:
- How to design and manage WebSocket endpoints in a Spring Boot environment.
- The WebSocket lifecycle (connect, message, close) and how to broadcast messages between clients.
- Techniques for maintaining real-time analytics and shared state across endpoints.
- The importance of synchronization and concurrency control when handling multiple simultaneous WebSocket sessions.
- How to write integration tests for asynchronous WebSocket communication.

## AI Disclosure
### AI Tools Used

OpenAI ChatGPT (GPT-5) — used as a coding assistant.

### AI-Assisted Work

- Guidance during debugging when STOMP messages were not being delivered correctly.
- Minor assistance revising the structure and clarity of this project report.
- Estimated AI-assisted work: ~20%.

### Original Work

- All code involving STOMP configuration, controllers, session initialization, analytics publishing, and integration testing (`StompElizaTopicTest`, `StompAnalyticsTopicTest`, `BroadcastTest`, `SessionsTest`) was implemented and debugged independently.
- Design decisions (profiles, message routing, greetings logic, analytics flow) were made manually after testing different alternatives.
- Manual Postman/STOMP client testing and troubleshooting were performed without AI assistance.
