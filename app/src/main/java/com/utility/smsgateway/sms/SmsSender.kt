package com.utility.smsgateway.sms

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.utility.smsgateway.receiver.SmsDeliveryReceiver

class SmsSender(private val context: Context) {

    data class SimInfo(
        val subscriptionId: Int,
        val slotIndex: Int,
        val displayName: String,
        val carrierName: String
    )

    fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getAvailableSimCards(): List<SimInfo> {
        val result = mutableListOf<SimInfo>()
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return result
        }

        try {
            val subManager = context.getSystemService(SubscriptionManager::class.java)
            val subList: List<SubscriptionInfo>? = subManager?.activeSubscriptionInfoList
            subList?.forEach { info ->
                result.add(
                    SimInfo(
                        subscriptionId = info.subscriptionId,
                        slotIndex = info.simSlotIndex,
                        displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                        carrierName = info.carrierName?.toString() ?: "Unknown"
                    )
                )
            }
        } catch (_: Exception) {
            // Permission or device specific failure
        }
        return result
    }

    private fun getSmsManager(targetSimSlot: Int?): SmsManager {
        val defaultManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        if (targetSimSlot == null || targetSimSlot < 0) {
            return defaultManager
        }

        try {
            val sims = getAvailableSimCards()
            val match = sims.firstOrNull { it.slotIndex == targetSimSlot }
            if (match != null) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    defaultManager.createForSubscriptionId(match.subscriptionId)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(match.subscriptionId)
                }
            }
        } catch (_: Exception) {
            // fallback to default
        }
        return defaultManager
    }

    fun sendSms(
        messageId: String,
        to: String,
        body: String,
        simSlot: Int? = null
    ): Result<Unit> {
        if (!hasSmsPermission()) {
            return Result.failure(SecurityException("SEND_SMS permission not granted"))
        }

        return try {
            val smsManager = getSmsManager(simSlot)

            val sentIntent = Intent(SmsDeliveryReceiver.ACTION_SMS_SENT).apply {
                setPackage(context.packageName)
                putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
            }
            val sentPendingIntent = PendingIntent.getBroadcast(
                context,
                messageId.hashCode(),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveryIntent = Intent(SmsDeliveryReceiver.ACTION_SMS_DELIVERED).apply {
                setPackage(context.packageName)
                putExtra(SmsDeliveryReceiver.EXTRA_MESSAGE_ID, messageId)
            }
            val deliveryPendingIntent = PendingIntent.getBroadcast(
                context,
                messageId.hashCode() + 1,
                deliveryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>()
                val deliveryIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentIntents.add(sentPendingIntent)
                    deliveryIntents.add(deliveryPendingIntent)
                }
                smsManager.sendMultipartTextMessage(
                    to,
                    null,
                    parts,
                    sentIntents,
                    deliveryIntents
                )
            } else {
                smsManager.sendTextMessage(
                    to,
                    null,
                    body,
                    sentPendingIntent,
                    deliveryPendingIntent
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

