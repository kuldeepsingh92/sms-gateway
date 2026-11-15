package com.utility.smsgateway.server

import android.content.Context
import android.util.Base64
import com.utility.smsgateway.data.model.HealthResponse
import com.utility.smsgateway.data.model.InfobipMessageItem
import com.utility.smsgateway.data.model.InfobipResponse
import com.utility.smsgateway.data.model.InfobipStatus
import com.utility.smsgateway.data.model.MessageStatus
import com.utility.smsgateway.data.model.Msg91Response
import com.utility.smsgateway.data.model.PlivoMessageResponse
import com.utility.smsgateway.data.model.SmsLogItem
import com.utility.smsgateway.data.model.SubresourceUris
import com.utility.smsgateway.data.model.TwilioErrorResponse
import com.utility.smsgateway.data.model.TwilioMessageResponse
import com.utility.smsgateway.data.model.VonageMessageItem
import com.utility.smsgateway.data.model.VonageMessageResponse
import com.utility.smsgateway.data.preferences.GatewayPreferences
import com.utility.smsgateway.data.repository.GatewayRepository
import com.utility.smsgateway.sms.SmsQueueManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentType
import io.ktor.server.request.header
import io.ktor.server.request.receiveParameters
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@Serializable
private data class DirectMessageRequest(
    val to: String? = null,
    val from: String? = null,
    val body: String? = null,
    val message: String? = null,
    val sim_slot: Int? = null
)

class GatewayHttpServer(
    private val context: Context,
    private val queueManager: SmsQueueManager
) {
    private val prefs = GatewayPreferences(context)
    private var serverEngine: EmbeddedServer<*, *>? = null

    fun start(port: Int): Result<Unit> {
        return try {
            val engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                configurePlugins()
                configureRoutes()
            }
            engine.start(wait = false)
            serverEngine = engine
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stop() {
        try {
            serverEngine?.stop(1000, 2000)
            serverEngine = null
        } catch (_: Exception) {
        }
    }

    private fun Application.configurePlugins() {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = true
            })
        }
    }

    private fun Application.configureRoutes() {
        routing {
            // Health check
            get("/") {
                val ip = GatewayRepository.localIp.value
                val port = prefs.port
                call.respond(
                    HealthResponse(
                        status = "UP",
                        service = "Android SMS Gateway (Twilio Compatible)",
                        version = "1.0.0",
                        local_ip = ip,
                        port = port
                    )
                )
            }

            get("/health") {
                val ip = GatewayRepository.localIp.value
                val port = prefs.port
                call.respond(
                    HealthResponse(
                        status = "UP",
                        service = "Android SMS Gateway (Twilio Compatible)",
                        version = "1.0.0",
                        local_ip = ip,
                        port = port
                    )
                )
            }

            // Twilio Compatible POST /2010-04-01/Accounts/{accountSid}/Messages.json
            post("/2010-04-01/Accounts/{accountSid}/Messages.json") {
                val accountSidParam = call.parameters["accountSid"] ?: ""
                handleTwilioSend(accountSidParam)
            }

            // Twilio Compatible alternative URL without .json
            post("/2010-04-01/Accounts/{accountSid}/Messages") {
                val accountSidParam = call.parameters["accountSid"] ?: ""
                handleTwilioSend(accountSidParam)
            }

            // Twilio Compatible GET Message status
            get("/2010-04-01/Accounts/{accountSid}/Messages/{messageSid}.json") {
                val accountSidParam = call.parameters["accountSid"] ?: ""
                val messageSid = call.parameters["messageSid"] ?: ""

                if (!authenticate(accountSidParam)) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        TwilioErrorResponse(
                            code = 20003,
                            message = "Authentication Error - Invalid Account SID or Auth Token",
                            more_info = "https://www.twilio.com/docs/errors/20003",
                            status = 401
                        )
                    )
                    return@get
                }

                val item = GatewayRepository.getMessage(messageSid)
                if (item == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        TwilioErrorResponse(
                            code = 20404,
                            message = "The requested resource /2010-04-01/Accounts/$accountSidParam/Messages/$messageSid.json was not found",
                            more_info = "https://www.twilio.com/docs/errors/20404",
                            status = 404
                        )
                    )
                } else {
                    val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val formattedDate = sdf.format(Date(item.timestamp))
                    call.respond(
                        TwilioMessageResponse(
                            sid = item.id,
                            date_created = formattedDate,
                            date_updated = formattedDate,
                            date_sent = if (item.status == MessageStatus.SENT || item.status == MessageStatus.DELIVERED) formattedDate else null,
                            account_sid = accountSidParam,
                            to = item.to,
                            from = item.from,
                            body = item.body,
                            status = item.status.value,
                            error_message = item.errorMessage,
                            uri = "/2010-04-01/Accounts/$accountSidParam/Messages/${item.id}.json",
                            subresource_uris = SubresourceUris(
                                media = "/2010-04-01/Accounts/$accountSidParam/Messages/${item.id}/Media.json"
                            )
                        )
                    )
                }
            }

            // Direct Generic REST Endpoint
            post("/api/v1/messages") {
                handleDirectSend()
            }

            // ==========================================
            // VONAGE (NEXMO) ENDPOINTS
            // ==========================================
            post("/v1/messages") {
                handleVonageSend()
            }
            post("/sms/json") {
                handleVonageSend()
            }

            // ==========================================
            // PLIVO ENDPOINTS
            // ==========================================
            post("/v1/Account/{authId}/Message/") {
                val authId = call.parameters["authId"] ?: ""
                handlePlivoSend(authId)
            }
            post("/v1/Account/{authId}/Message") {
                val authId = call.parameters["authId"] ?: ""
                handlePlivoSend(authId)
            }

            // ==========================================
            // INFOBIP ENDPOINTS
            // ==========================================
            post("/sms/2/text/advanced") {
                handleInfobipSend()
            }
            post("/sms/1/text/single") {
                handleInfobipSend()
            }

            // ==========================================
            // MSG91 ENDPOINTS
            // ==========================================
            post("/api/v5/flow/") {
                handleMsg91Send()
            }
            post("/api/v5/flow") {
                handleMsg91Send()
            }
            post("/api/v2/sendsms") {
                handleMsg91Send()
            }
        }
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleTwilioSend(accountSidParam: String) {
        if (!authenticate(accountSidParam)) {
            call.respond(
                HttpStatusCode.Unauthorized,
                TwilioErrorResponse(
                    code = 20003,
                    message = "Authentication Error - Invalid Account SID or Auth Token",
                    more_info = "https://www.twilio.com/docs/errors/20003",
                    status = 401
                )
            )
            return
        }

        var to: String? = null
        var from: String? = null
        var body: String? = null

        val contentType = call.request.contentType()
        if (contentType.match(ContentType.Application.FormUrlEncoded)) {
            val params = call.receiveParameters()
            to = params["To"] ?: params["to"]
            from = params["From"] ?: params["from"]
            body = params["Body"] ?: params["body"]
        } else {
            // Try parsing as JSON or generic text
            try {
                val text = call.receiveText()
                val json = Json { ignoreUnknownKeys = true }
                val parsed = json.decodeFromString<DirectMessageRequest>(text)
                to = parsed.to
                from = parsed.from
                body = parsed.body ?: parsed.message
            } catch (_: Exception) {
                // fall through
            }
        }

        if (to.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                TwilioErrorResponse(
                    code = 21604,
                    message = "A 'To' phone number is required.",
                    more_info = "https://www.twilio.com/docs/errors/21604",
                    status = 400
                )
            )
            return
        }

        if (body.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                TwilioErrorResponse(
                    code = 21602,
                    message = "Message body is required.",
                    more_info = "https://www.twilio.com/docs/errors/21602",
                    status = 400
                )
            )
            return
        }

        val sid = "SM" + UUID.randomUUID().toString().replace("-", "")
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val formattedDate = sdf.format(Date())

        val logItem = SmsLogItem(
            id = sid,
            to = to,
            from = from,
            body = body,
            status = MessageStatus.QUEUED
        )
        queueManager.enqueue(logItem)

        call.respond(
            HttpStatusCode.Created,
            TwilioMessageResponse(
                sid = sid,
                date_created = formattedDate,
                date_updated = formattedDate,
                account_sid = accountSidParam,
                to = to,
                from = from,
                body = body,
                status = "queued",
                uri = "/2010-04-01/Accounts/$accountSidParam/Messages/$sid.json",
                subresource_uris = SubresourceUris(
                    media = "/2010-04-01/Accounts/$accountSidParam/Messages/$sid/Media.json"
                )
            )
        )
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleDirectSend() {
        val authHeader = call.request.header("Authorization")
        val validToken = prefs.authToken
        val isValid = when {
            authHeader == null -> false
            authHeader.startsWith("Bearer ", ignoreCase = true) -> {
                authHeader.removePrefix("Bearer ").trim() == validToken
            }
            authHeader.startsWith("Basic ", ignoreCase = true) -> {
                authenticate(prefs.accountSid)
            }
            else -> false
        }

        if (!isValid) {
            call.respondText(
                "{\"error\": \"Unauthorized\"}",
                ContentType.Application.Json,
                HttpStatusCode.Unauthorized
            )
            return
        }

        val bodyText = call.receiveText()
        val json = Json { ignoreUnknownKeys = true }
        val req = try {
            json.decodeFromString<DirectMessageRequest>(bodyText)
        } catch (e: Exception) {
            call.respondText(
                "{\"error\": \"Invalid JSON payload: ${e.message}\"}",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        val to = req.to
        val text = req.body ?: req.message
        if (to.isNullOrBlank() || text.isNullOrBlank()) {
            call.respondText(
                "{\"error\": \"Fields 'to' and 'body' are required\"}",
                ContentType.Application.Json,
                HttpStatusCode.BadRequest
            )
            return
        }

        val sid = "SM" + UUID.randomUUID().toString().replace("-", "")
        val logItem = SmsLogItem(
            id = sid,
            to = to,
            from = req.from,
            body = text,
            status = MessageStatus.QUEUED,
            simSlot = req.sim_slot
        )
        queueManager.enqueue(logItem)

        call.respondText(
            "{\"id\": \"$sid\", \"status\": \"queued\", \"to\": \"$to\"}",
            ContentType.Application.Json,
            HttpStatusCode.Created
        )
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleVonageSend() {
        val bodyText = call.receiveText()
        var to: String? = null
        var text: String? = null
        var from: String? = null

        val contentType = call.request.contentType()
        if (contentType.match(ContentType.Application.FormUrlEncoded)) {
            val params = io.ktor.http.parseQueryString(bodyText)
            to = params["to"]
            text = params["text"] ?: params["body"]
            from = params["from"]
        } else {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(bodyText)
                if (root is kotlinx.serialization.json.JsonObject) {
                    to = (root["to"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    text = (root["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (root["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    from = (root["from"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
            } catch (_: Exception) {}
        }

        if (to.isNullOrBlank() || text.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error-text" to "Missing required parameters 'to' and 'text'")
            )
            return
        }

        val uuid = UUID.randomUUID().toString()
        val sid = "VN_" + uuid.replace("-", "").take(16)
        queueManager.enqueue(
            SmsLogItem(
                id = sid,
                to = to,
                from = from,
                body = text,
                status = MessageStatus.QUEUED
            )
        )

        call.respond(
            HttpStatusCode.OK,
            VonageMessageResponse(
                message_count = "1",
                messages = listOf(
                    VonageMessageItem(
                        to = to,
                        message_id = uuid,
                        status = "0"
                    )
                ),
                message_uuid = uuid
            )
        )
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handlePlivoSend(authIdParam: String) {
        val bodyText = call.receiveText()
        var dst: String? = null
        var src: String? = null
        var text: String? = null

        val contentType = call.request.contentType()
        if (contentType.match(ContentType.Application.FormUrlEncoded)) {
            val params = io.ktor.http.parseQueryString(bodyText)
            dst = params["dst"] ?: params["to"]
            src = params["src"] ?: params["from"]
            text = params["text"] ?: params["body"]
        } else {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(bodyText)
                if (root is kotlinx.serialization.json.JsonObject) {
                    dst = (root["dst"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (root["to"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    src = (root["src"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (root["from"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    text = (root["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (root["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
            } catch (_: Exception) {}
        }

        if (dst.isNullOrBlank() || text.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Mandatory parameter 'dst' or 'text' is missing")
            )
            return
        }

        val uuid = UUID.randomUUID().toString()
        val sid = "PL_" + uuid.replace("-", "").take(16)
        queueManager.enqueue(
            SmsLogItem(
                id = sid,
                to = dst,
                from = src,
                body = text,
                status = MessageStatus.QUEUED
            )
        )

        call.respond(
            HttpStatusCode.Accepted,
            PlivoMessageResponse(
                message = "message(s) queued",
                message_uuid = listOf(uuid),
                api_id = UUID.randomUUID().toString()
            )
        )
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleInfobipSend() {
        val bodyText = call.receiveText()
        var to: String? = null
        var text: String? = null

        try {
            val json = Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(bodyText)
            if (root is kotlinx.serialization.json.JsonObject) {
                val messagesArray = root["messages"] as? kotlinx.serialization.json.JsonArray
                if (messagesArray != null && messagesArray.isNotEmpty()) {
                    val firstMsg = messagesArray[0] as? kotlinx.serialization.json.JsonObject
                    text = (firstMsg?.get("text") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val destArray = firstMsg?.get("destinations") as? kotlinx.serialization.json.JsonArray
                    if (destArray != null && destArray.isNotEmpty()) {
                        val firstDest = destArray[0] as? kotlinx.serialization.json.JsonObject
                        to = (firstDest?.get("to") as? kotlinx.serialization.json.JsonPrimitive)?.content
                    }
                }
                if (to == null) {
                    to = (root["to"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
                if (text == null) {
                    text = (root["text"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                }
            }
        } catch (_: Exception) {}

        if (to.isNullOrBlank() || text.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("requestError" to mapOf("serviceException" to mapOf("messageId" to "BAD_REQUEST", "text" to "Missing destination or text")))
            )
            return
        }

        val msgId = UUID.randomUUID().toString()
        val sid = "IB_" + msgId.replace("-", "").take(16)
        queueManager.enqueue(
            SmsLogItem(
                id = sid,
                to = to,
                from = null,
                body = text,
                status = MessageStatus.QUEUED
            )
        )

        call.respond(
            HttpStatusCode.OK,
            InfobipResponse(
                bulkId = UUID.randomUUID().toString(),
                messages = listOf(
                    InfobipMessageItem(
                        to = to,
                        status = InfobipStatus(),
                        messageId = msgId
                    )
                )
            )
        )
    }

    private suspend fun io.ktor.server.routing.RoutingContext.handleMsg91Send() {
        val bodyText = call.receiveText()
        var to: String? = null
        var message: String? = null

        val contentType = call.request.contentType()
        if (contentType.match(ContentType.Application.FormUrlEncoded)) {
            val params = io.ktor.http.parseQueryString(bodyText)
            to = params["mobiles"] ?: params["to"]
            message = params["message"] ?: params["body"]
        } else {
            try {
                val json = Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(bodyText)
                if (root is kotlinx.serialization.json.JsonObject) {
                    to = (root["mobiles"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (root["to"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    message = (root["message"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                        ?: (root["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val recipients = root["recipients"] as? kotlinx.serialization.json.JsonArray
                    if (recipients != null && recipients.isNotEmpty()) {
                        val first = recipients[0] as? kotlinx.serialization.json.JsonObject
                        if (to == null) {
                            to = (first?.get("mobiles") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                        if (message == null) {
                            message = (first?.get("message") as? kotlinx.serialization.json.JsonPrimitive)?.content
                                ?: (first?.get("var1") as? kotlinx.serialization.json.JsonPrimitive)?.content
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (to.isNullOrBlank() || message.isNullOrBlank()) {
            call.respond(
                HttpStatusCode.BadRequest,
                Msg91Response(message = "Missing 'mobiles' or 'message'", type = "error")
            )
            return
        }

        val msgId = UUID.randomUUID().toString().replace("-", "").take(24)
        val sid = "M91_" + msgId.take(16)
        queueManager.enqueue(
            SmsLogItem(
                id = sid,
                to = to,
                from = null,
                body = message,
                status = MessageStatus.QUEUED
            )
        )

        call.respond(
            HttpStatusCode.OK,
            Msg91Response(
                message = msgId,
                type = "success"
            )
        )
    }

    private fun io.ktor.server.routing.RoutingContext.authenticate(accountSidParam: String): Boolean {
        if (accountSidParam != prefs.accountSid) return false

        val authHeader = call.request.header("Authorization") ?: return false
        if (!authHeader.startsWith("Basic ", ignoreCase = true)) return false

        return try {
            val b64 = authHeader.removePrefix("Basic ").trim()
            val decoded = String(Base64.decode(b64, Base64.DEFAULT))
            val parts = decoded.split(":", limit = 2)
            if (parts.size != 2) return false

            val user = parts[0]
            val pass = parts[1]
            user == prefs.accountSid && pass == prefs.authToken
        } catch (_: Exception) {
            false
        }
    }
}

