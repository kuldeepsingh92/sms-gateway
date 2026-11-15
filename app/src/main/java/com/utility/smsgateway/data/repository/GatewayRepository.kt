package com.utility.smsgateway.data.repository

import com.utility.smsgateway.data.model.MessageStatus
import com.utility.smsgateway.data.model.SmsLogItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

object GatewayRepository {
    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _serverError = MutableStateFlow<String?>(null)
    val serverError: StateFlow<String?> = _serverError.asStateFlow()

    private val _localIp = MutableStateFlow("127.0.0.1")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _logs = MutableStateFlow<List<SmsLogItem>>(emptyList())
    val logs: StateFlow<List<SmsLogItem>> = _logs.asStateFlow()

    fun setServerState(state: ServerState, error: String? = null) {
        _serverState.value = state
        _serverError.value = error
    }

    fun updateLocalIp() {
        _localIp.value = resolveLocalIp()
    }

    fun addLog(item: SmsLogItem) {
        val current = _logs.value.toMutableList()
        current.add(0, item) // most recent first
        // Keep up to 200 items in memory to avoid unbounded memory growth
        if (current.size > 200) {
            _logs.value = current.take(200)
        } else {
            _logs.value = current
        }
    }

    fun updateMessageStatus(id: String, status: MessageStatus, errorMessage: String? = null) {
        val current = _logs.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = current[index]
            current[index] = item.copy(status = status, errorMessage = errorMessage ?: item.errorMessage)
            _logs.value = current
        }
    }

    fun getMessage(id: String): SmsLogItem? {
        return _logs.value.find { it.id == id }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    private fun resolveLocalIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && !hostAddress.startsWith("127.")) {
                            return hostAddress
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // fallback
        }
        return "127.0.0.1"
    }
}

