package com.utility.smsgateway.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utility.smsgateway.data.model.MessageStatus
import com.utility.smsgateway.data.model.SmsProvider
import com.utility.smsgateway.data.preferences.GatewayPreferences
import com.utility.smsgateway.data.repository.GatewayRepository
import com.utility.smsgateway.data.repository.ServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Composable
fun DashboardScreen(
    onToggleServer: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { GatewayPreferences(context) }
    val serverState by GatewayRepository.serverState.collectAsState()
    val localIp by GatewayRepository.localIp.collectAsState()
    val logs by GatewayRepository.logs.collectAsState()

    var selectedProvider by remember { mutableStateOf(prefs.selectedProvider) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }

    var accountSid by remember { mutableStateOf(prefs.accountSid) }
    var authToken by remember { mutableStateOf(prefs.authToken) }
    var vonageKey by remember { mutableStateOf(prefs.vonageApiKey) }
    var vonageSecret by remember { mutableStateOf(prefs.vonageApiSecret) }
    var msg91Key by remember { mutableStateOf(prefs.msg91AuthKey) }
    val port = prefs.port

    val isRunning = serverState == ServerState.RUNNING
    val baseUrl = "http://$localIp:$port"

    val scope = rememberCoroutineScope()
    var testPhoneNumber by remember { mutableStateOf("") }
    var testMessageBody by remember { mutableStateOf("Test SMS from Gateway") }
    var isSendingTest by remember { mutableStateOf(false) }
    var testResultFeedback by remember { mutableStateOf<String?>(null) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Dynamic cURL generation depending on selected provider
    val (curlCommand, activeEndpoint) = when (selectedProvider) {
        SmsProvider.TWILIO -> {
            val endpoint = "$baseUrl/2010-04-01/Accounts/$accountSid/Messages.json"
            val curl = "curl -X POST $endpoint \\\n" +
                    "  -u \"$accountSid:$authToken\" \\\n" +
                    "  -d \"To=+1234567890\" \\\n" +
                    "  -d \"Body=Test SMS from local Gateway\""
            Pair(curl, endpoint)
        }
        SmsProvider.VONAGE -> {
            val endpoint = "$baseUrl/v1/messages"
            val curl = "curl -X POST $endpoint \\\n" +
                    "  -H \"Content-Type: application/json\" \\\n" +
                    "  -H \"Authorization: Bearer $authToken\" \\\n" +
                    "  -d '{\"to\": \"+1234567890\", \"text\": \"Test SMS from local Gateway\"}'"
            Pair(curl, endpoint)
        }
        SmsProvider.PLIVO -> {
            val endpoint = "$baseUrl/v1/Account/$accountSid/Message/"
            val curl = "curl -X POST $endpoint \\\n" +
                    "  -u \"$accountSid:$authToken\" \\\n" +
                    "  -H \"Content-Type: application/json\" \\\n" +
                    "  -d '{\"dst\": \"+1234567890\", \"text\": \"Test SMS from local Gateway\"}'"
            Pair(curl, endpoint)
        }
        SmsProvider.INFOBIP -> {
            val endpoint = "$baseUrl/sms/2/text/advanced"
            val curl = "curl -X POST $endpoint \\\n" +
                    "  -H \"Authorization: App $authToken\" \\\n" +
                    "  -H \"Content-Type: application/json\" \\\n" +
                    "  -d '{\"messages\": [{\"destinations\": [{\"to\": \"+1234567890\"}], \"text\": \"Test SMS from local Gateway\"}]}'"
            Pair(curl, endpoint)
        }
        SmsProvider.MSG91 -> {
            val endpoint = "$baseUrl/api/v5/flow/"
            val curl = "curl -X POST $endpoint \\\n" +
                    "  -H \"authkey: $msg91Key\" \\\n" +
                    "  -H \"Content-Type: application/json\" \\\n" +
                    "  -d '{\"mobiles\": \"+1234567890\", \"message\": \"Test SMS from local Gateway\"}'"
            Pair(curl, endpoint)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when (serverState) {
                                        ServerState.RUNNING -> Color(0xFF3FB950)
                                        ServerState.STARTING -> Color(0xFFD29922)
                                        ServerState.STOPPED -> Color(0xFF8B949E)
                                        ServerState.ERROR -> MaterialTheme.colorScheme.error
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (serverState) {
                                ServerState.RUNNING -> "GATEWAY ACTIVE"
                                ServerState.STARTING -> "STARTING..."
                                ServerState.STOPPED -> "GATEWAY STOPPED"
                                ServerState.ERROR -> "SERVER ERROR"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = when (serverState) {
                                ServerState.RUNNING -> Color(0xFF3FB950)
                                ServerState.ERROR -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Button(
                        onClick = { onToggleServer(!isRunning) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) MaterialTheme.colorScheme.error else Color(0xFF238636)
                        )
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isRunning) "Stop" else "Start")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Gateway Base URL (All 5 Providers Active)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = baseUrl,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = { copyToClipboard("Gateway URL", baseUrl) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                    }
                }
            }
        }

        // Stats Row
        val totalCount = logs.size
        val sentCount = logs.count { it.status == MessageStatus.SENT || it.status == MessageStatus.DELIVERED }
        val failedCount = logs.count { it.status == MessageStatus.FAILED }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(title = "Total", count = totalCount, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            StatCard(title = "Sent", count = sentCount, color = Color(0xFF3FB950), modifier = Modifier.weight(1f))
            StatCard(title = "Failed", count = failedCount, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        }

        // Provider Selector Card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Active Provider Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { providerDropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedProvider.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Provider")
                    }

                    DropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false }
                    ) {
                        SmsProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(provider.displayName, fontWeight = if (provider == selectedProvider) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    selectedProvider = provider
                                    prefs.selectedProvider = provider
                                    providerDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Endpoint: $activeEndpoint",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Dynamic Credentials Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedProvider.displayName} Credentials",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    OutlinedButton(
                        onClick = {
                            val (newSid, newToken) = prefs.regenerateCredentials()
                            accountSid = newSid
                            authToken = newToken
                            vonageKey = newToken.take(8)
                            vonageSecret = newToken.takeLast(16)
                            msg91Key = newToken.take(24)
                            Toast.makeText(context, "Credentials regenerated", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Regenerate")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedProvider) {
                    SmsProvider.TWILIO -> {
                        CredentialRow(label = "ACCOUNT SID", value = accountSid, onCopy = { copyToClipboard("Account SID", accountSid) })
                        Spacer(modifier = Modifier.height(8.dp))
                        CredentialRow(label = "AUTH TOKEN", value = authToken, onCopy = { copyToClipboard("Auth Token", authToken) })
                    }
                    SmsProvider.VONAGE -> {
                        CredentialRow(label = "API KEY", value = vonageKey, onCopy = { copyToClipboard("Vonage API Key", vonageKey) })
                        Spacer(modifier = Modifier.height(8.dp))
                        CredentialRow(label = "API SECRET", value = vonageSecret, onCopy = { copyToClipboard("Vonage API Secret", vonageSecret) })
                    }
                    SmsProvider.PLIVO -> {
                        CredentialRow(label = "AUTH ID", value = accountSid, onCopy = { copyToClipboard("Plivo Auth ID", accountSid) })
                        Spacer(modifier = Modifier.height(8.dp))
                        CredentialRow(label = "AUTH TOKEN", value = authToken, onCopy = { copyToClipboard("Plivo Auth Token", authToken) })
                    }
                    SmsProvider.INFOBIP -> {
                        CredentialRow(label = "APP API KEY", value = authToken, onCopy = { copyToClipboard("Infobip API Key", authToken) })
                    }
                    SmsProvider.MSG91 -> {
                        CredentialRow(label = "AUTHKEY", value = msg91Key, onCopy = { copyToClipboard("MSG91 AuthKey", msg91Key) })
                    }
                }
            }
        }

        // Quick cURL Card (Formatted per provider)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "cURL for ${selectedProvider.displayName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    IconButton(onClick = { copyToClipboard("cURL Command", curlCommand) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy cURL")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .padding(12.dp)
                ) {
                    Text(
                        text = curlCommand,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // In-App SMS Tester Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Quick In-App SMS Test (${selectedProvider.displayName} Mode)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Sends a real SMS through your SIM via the active ${selectedProvider.displayName} endpoint.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testPhoneNumber,
                    onValueChange = { testPhoneNumber = it },
                    label = { Text("Recipient Phone Number (e.g. +1234567890)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = testMessageBody,
                    onValueChange = { testMessageBody = it },
                    label = { Text("Message Body") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (testResultFeedback != null) {
                        Text(
                            text = testResultFeedback ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (testResultFeedback!!.startsWith("Success")) Color(0xFF3FB950) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Button(
                        enabled = !isSendingTest,
                        onClick = {
                            if (!isRunning) {
                                Toast.makeText(context, "Please start the gateway first!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (testPhoneNumber.isBlank()) {
                                Toast.makeText(context, "Please enter a phone number", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch(Dispatchers.IO) {
                                isSendingTest = true
                                testResultFeedback = null
                                try {
                                    val endpointUrl = URL("http://127.0.0.1:$port/2010-04-01/Accounts/$accountSid/Messages.json")
                                    val conn = (endpointUrl.openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"
                                        doOutput = true
                                        val creds = "$accountSid:$authToken"
                                        val basicAuth = "Basic " + Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
                                        setRequestProperty("Authorization", basicAuth)
                                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                                        connectTimeout = 5000
                                        readTimeout = 5000
                                    }
                                    val params = "To=" + URLEncoder.encode(testPhoneNumber.trim(), "UTF-8") +
                                            "&Body=" + URLEncoder.encode(testMessageBody.trim(), "UTF-8")
                                    conn.outputStream.use { os ->
                                        os.write(params.toByteArray())
                                        os.flush()
                                    }
                                    val code = conn.responseCode
                                    val responseText = if (code in 200..299) {
                                        conn.inputStream.bufferedReader().use { it.readText() }
                                    } else {
                                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                                    }

                                    withContext(Dispatchers.Main) {
                                        isSendingTest = false
                                        if (code in 200..299) {
                                            testResultFeedback = "Success (HTTP $code)"
                                            Toast.makeText(context, "Test SMS Queued! Check Logs tab.", Toast.LENGTH_LONG).show()
                                        } else {
                                            testResultFeedback = "Failed (HTTP $code)"
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isSendingTest = false
                                        testResultFeedback = "Error: ${e.message}"
                                    }
                                }
                            }
                        }
                    ) {
                        if (isSendingTest) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sending...")
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send Test SMS")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatCard(title: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = count.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = color
            )
        }
    }
}

@Composable
private fun CredentialRow(label: String, value: String, onCopy: () -> Unit) {
    Column {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label", modifier = Modifier.size(18.dp))
            }
        }
    }
}
