package com.utility.smsgateway.data.model

import kotlinx.serialization.Serializable

enum class SmsProvider(val displayName: String) {
    TWILIO("Twilio"),
    VONAGE("Vonage (Nexmo)"),
    PLIVO("Plivo"),
    INFOBIP("Infobip"),
    MSG91("MSG91")
}

// ----------------- VONAGE (NEXMO) SCHEMAS -----------------
@Serializable
data class VonageMessageItem(
    val to: String,
    val message_id: String,
    val status: String = "0",
    val remaining_balance: String = "10.00",
    val message_price: String = "0.0000",
    val network: String = "12345"
)

@Serializable
data class VonageMessageResponse(
    val message_count: String = "1",
    val messages: List<VonageMessageItem>,
    val message_uuid: String? = null
)

// ----------------- PLIVO SCHEMAS -----------------
@Serializable
data class PlivoMessageResponse(
    val message: String = "message(s) queued",
    val message_uuid: List<String>,
    val api_id: String
)

// ----------------- INFOBIP SCHEMAS -----------------
@Serializable
data class InfobipStatus(
    val groupId: Int = 1,
    val groupName: String = "PENDING",
    val id: Int = 7,
    val name: String = "PENDING_ENROUTE",
    val description: String = "Message sent to next instance"
)

@Serializable
data class InfobipMessageItem(
    val to: String,
    val status: InfobipStatus,
    val messageId: String
)

@Serializable
data class InfobipResponse(
    val bulkId: String,
    val messages: List<InfobipMessageItem>
)

// ----------------- MSG91 SCHEMAS -----------------
@Serializable
data class Msg91Response(
    val message: String,
    val type: String = "success"
)

