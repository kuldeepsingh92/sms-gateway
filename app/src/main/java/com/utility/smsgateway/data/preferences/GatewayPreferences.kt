package com.utility.smsgateway.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.utility.smsgateway.data.model.SmsProvider
import java.util.UUID

class GatewayPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sms_gateway_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCOUNT_SID = "account_sid"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_PORT = "port"
        private const val KEY_SIM_SLOT = "sim_slot"
        private const val KEY_THROTTLE_MS = "throttle_ms"
        private const val KEY_SERVER_ACTIVE = "server_active"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_VONAGE_KEY = "vonage_key"
        private const val KEY_VONAGE_SECRET = "vonage_secret"
        private const val KEY_MSG91_AUTHKEY = "msg91_authkey"
    }

    var accountSid: String
        get() {
            var sid = prefs.getString(KEY_ACCOUNT_SID, null)
            if (sid.isNullOrBlank()) {
                sid = "AC" + UUID.randomUUID().toString().replace("-", "")
                prefs.edit().putString(KEY_ACCOUNT_SID, sid).apply()
            }
            return sid
        }
        set(value) {
            prefs.edit().putString(KEY_ACCOUNT_SID, value.trim()).apply()
        }

    var authToken: String
        get() {
            var token = prefs.getString(KEY_AUTH_TOKEN, null)
            if (token.isNullOrBlank()) {
                token = UUID.randomUUID().toString().replace("-", "")
                prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
            }
            return token
        }
        set(value) {
            prefs.edit().putString(KEY_AUTH_TOKEN, value.trim()).apply()
        }

    var port: Int
        get() = prefs.getInt(KEY_PORT, 8080)
        set(value) {
            prefs.edit().putInt(KEY_PORT, value).apply()
        }

    var selectedSimSlot: Int
        get() = prefs.getInt(KEY_SIM_SLOT, -1) // -1 means default SIM
        set(value) {
            prefs.edit().putInt(KEY_SIM_SLOT, value).apply()
        }

    var throttleDelayMs: Long
        get() = prefs.getLong(KEY_THROTTLE_MS, 1000L) // 1 second default throttle
        set(value) {
            prefs.edit().putLong(KEY_THROTTLE_MS, value).apply()
        }

    var isServerActive: Boolean
        get() = prefs.getBoolean(KEY_SERVER_ACTIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SERVER_ACTIVE, value).apply()
        }

    var selectedProvider: SmsProvider
        get() {
            val name = prefs.getString(KEY_SELECTED_PROVIDER, SmsProvider.TWILIO.name)
            return try {
                SmsProvider.valueOf(name ?: SmsProvider.TWILIO.name)
            } catch (_: Exception) {
                SmsProvider.TWILIO
            }
        }
        set(value) {
            prefs.edit().putString(KEY_SELECTED_PROVIDER, value.name).apply()
        }

    var vonageApiKey: String
        get() = prefs.getString(KEY_VONAGE_KEY, null) ?: authToken.take(8)
        set(value) {
            prefs.edit().putString(KEY_VONAGE_KEY, value.trim()).apply()
        }

    var vonageApiSecret: String
        get() = prefs.getString(KEY_VONAGE_SECRET, null) ?: authToken.takeLast(16)
        set(value) {
            prefs.edit().putString(KEY_VONAGE_SECRET, value.trim()).apply()
        }

    var msg91AuthKey: String
        get() = prefs.getString(KEY_MSG91_AUTHKEY, null) ?: authToken.take(24)
        set(value) {
            prefs.edit().putString(KEY_MSG91_AUTHKEY, value.trim()).apply()
        }

    fun regenerateCredentials(): Pair<String, String> {
        val newSid = "AC" + UUID.randomUUID().toString().replace("-", "")
        val newToken = UUID.randomUUID().toString().replace("-", "")
        prefs.edit()
            .putString(KEY_ACCOUNT_SID, newSid)
            .putString(KEY_AUTH_TOKEN, newToken)
            .putString(KEY_VONAGE_KEY, newToken.take(8))
            .putString(KEY_VONAGE_SECRET, newToken.takeLast(16))
            .putString(KEY_MSG91_AUTHKEY, newToken.take(24))
            .apply()
        return Pair(newSid, newToken)
    }
}

