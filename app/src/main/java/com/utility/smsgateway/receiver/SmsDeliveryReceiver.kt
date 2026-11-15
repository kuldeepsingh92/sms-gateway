package com.utility.smsgateway.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.utility.smsgateway.data.model.MessageStatus
import com.utility.smsgateway.data.repository.GatewayRepository

class SmsDeliveryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SMS_SENT = "com.utility.smsgateway.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.utility.smsgateway.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return

        when (intent.action) {
            ACTION_SMS_SENT -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        GatewayRepository.updateMessageStatus(messageId, MessageStatus.SENT)
                    }
                    SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                        GatewayRepository.updateMessageStatus(
                            messageId,
                            MessageStatus.FAILED,
                            "Generic failure"
                        )
                    }
                    SmsManager.RESULT_ERROR_NO_SERVICE -> {
                        GatewayRepository.updateMessageStatus(
                            messageId,
                            MessageStatus.FAILED,
                            "No cellular service"
                        )
                    }
                    SmsManager.RESULT_ERROR_NULL_PDU -> {
                        GatewayRepository.updateMessageStatus(
                            messageId,
                            MessageStatus.FAILED,
                            "Null PDU"
                        )
                    }
                    SmsManager.RESULT_ERROR_RADIO_OFF -> {
                        GatewayRepository.updateMessageStatus(
                            messageId,
                            MessageStatus.FAILED,
                            "Airplane mode / Radio off"
                        )
                    }
                    else -> {
                        GatewayRepository.updateMessageStatus(
                            messageId,
                            MessageStatus.FAILED,
                            "Failed with code: $resultCode"
                        )
                    }
                }
            }
            ACTION_SMS_DELIVERED -> {
                when (resultCode) {
                    Activity.RESULT_OK -> {
                        GatewayRepository.updateMessageStatus(messageId, MessageStatus.DELIVERED)
                    }
                    else -> {
                        GatewayRepository.updateMessageStatus(
                            messageId,
                            MessageStatus.FAILED,
                            "Delivery failed with code: $resultCode"
                        )
                    }
                }
            }
        }
    }
}

