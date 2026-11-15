package com.utility.smsgateway.data.model

import kotlinx.serialization.Serializable

enum class MessageStatus(val value: String) {
    QUEUED("queued"),
    SENDING("sending"),
    SENT("sent"),
    DELIVERED("delivered"),
    FAILED("failed")
}

data class SmsLogItem(
    val id: String,
    val to: String,
    val from: String?,
    val body: String,
    val status: MessageStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val simSlot: Int? = null
)

@Serializable
data class SubresourceUris(
    val media: String
)

@Serializable
data class TwilioMessageResponse(
    val sid: String,
    val date_created: String,
    val date_updated: String,
    val date_sent: String? = null,
    val account_sid: String,
    val to: String,
    val from: String? = null,
    val messaging_service_sid: String? = null,
    val body: String,
    val status: String,
    val num_segments: String = "1",
    val num_media: String = "0",
    val direction: String = "outbound-api",
    val api_version: String = "2010-04-01",
    val price: String? = null,
    val price_unit: String? = null,
    val error_code: Int? = null,
    val error_message: String? = null,
    val uri: String,
    val subresource_uris: SubresourceUris? = null
)

@Serializable
data class TwilioErrorResponse(
    val code: Int,
    val message: String,
    val more_info: String? = null,
    val status: Int
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
    val local_ip: String,
    val port: Int
)

