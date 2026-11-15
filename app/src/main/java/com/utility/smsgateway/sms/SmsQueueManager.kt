package com.utility.smsgateway.sms

import android.content.Context
import com.utility.smsgateway.data.model.MessageStatus
import com.utility.smsgateway.data.model.SmsLogItem
import com.utility.smsgateway.data.preferences.GatewayPreferences
import com.utility.smsgateway.data.repository.GatewayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SmsQueueManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val smsSender = SmsSender(context)
    private val prefs = GatewayPreferences(context)
    private val queue = Channel<SmsLogItem>(Channel.UNLIMITED)
    private var workerJob: Job? = null

    fun start() {
        if (workerJob != null && workerJob?.isActive == true) return

        workerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val item = queue.receive()
                    processMessage(item)
                    // Throttle between SMS dispatches to avoid carrier rate limits
                    val delayMs = prefs.throttleDelayMs.coerceAtLeast(200L)
                    delay(delayMs)
                } catch (_: Exception) {
                    // Queue closed or cancelled
                }
            }
        }
    }

    fun stop() {
        workerJob?.cancel()
        workerJob = null
    }

    suspend fun enqueue(item: SmsLogItem) {
        GatewayRepository.addLog(item)
        queue.send(item)
    }

    private fun processMessage(item: SmsLogItem) {
        GatewayRepository.updateMessageStatus(item.id, MessageStatus.SENDING)
        val simSlot = if (item.simSlot != null && item.simSlot >= 0) {
            item.simSlot
        } else {
            val prefSlot = prefs.selectedSimSlot
            if (prefSlot >= 0) prefSlot else null
        }

        val result = smsSender.sendSms(
            messageId = item.id,
            to = item.to,
            body = item.body,
            simSlot = simSlot
        )

        result.onFailure { exception ->
            GatewayRepository.updateMessageStatus(
                id = item.id,
                status = MessageStatus.FAILED,
                errorMessage = exception.message ?: "Unknown error"
            )
        }
    }
}

