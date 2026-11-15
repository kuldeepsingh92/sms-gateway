# Android SMS Gateway (Multi-Provider Emulator)

A lightweight, high-performance Android application that turns your Android device into a private, local SMS Gateway. It emulates the **Top 5 Global SMS Providers (Twilio, Vonage, Plivo, Infobip, MSG91)** with simultaneous endpoint routing, allowing developers to build, test, and debug SMS workflows in applications without paying subscription fees or per-SMS charges to cloud providers.

---

## Table of Contents

1. [About the App & Architecture](#about-the-app--architecture)
2. [Supported Providers & Endpoint Matrix](#supported-providers--endpoint-matrix)
3. [Installation Guidelines (Overcoming Play Protect)](#installation-guidelines)
4. [App Setup & Configuration](#app-setup--configuration)
5. [Developer Commands & Terminal Cheatsheet](#developer-commands--terminal-cheatsheet)
6. [Sending SMS: Provider Code Examples & cURL](#sending-sms-provider-code-examples--curl)
7. [Testing Over USB Without Wi-Fi (`adb reverse`)](#testing-over-usb-without-wi-fi-adb-reverse)
8. [Switching to Production](#switching-to-production)

---

## About the App & Architecture

When developing apps with phone verification, OTPs, or transactional alerts, testing with cloud providers (like Twilio or Vonage) can be costly and requires setting up business accounts, submitting regulatory filings (like A2P 10DLC or DLT), and paying for test credits.

This app solves that by running a full HTTP REST server directly on your phone, dispatching real SMS messages through your existing phone carrier SIM plan.

### Technical Stack & Guarantees

* **Kotlin 2.0+**: Null-safe, modern Android programming standard.
* **Jetpack Compose (Material 3)**: Declarative, modern UI with dark/light themes and reactive state updates.
* **Ktor CIO Embedded Server**: Pure-Kotlin asynchronous coroutine I/O server engine running locally on port `8080` (<15MB RAM footprint).
* **Native Android `SmsManager`**: Direct communication with your phone's cellular baseband chip, supporting single-part and multi-part messages (>160 characters / Unicode emojis).
* **Coroutine FIFO Queue & Throttler**: Smoothly queues rapid incoming API requests and dispatches them with a configurable throttle delay (default: 1000ms) to prevent carrier spam detection and Android OS throttling.
* **Android Foreground Service + WakeLock & WifiLock**: Keeps the HTTP server alive and responsive even when the phone display is locked and enters Doze Mode.
* **Auto-Adaptive Keyboard Layout**: Bottom navigation dynamically hides when typing to prevent keyboard overlap or unwanted gaps.

---

## Supported Providers & Endpoint Matrix

All provider endpoints are **active simultaneously**. You can point different applications to different provider routes without restarting the server:

| Provider | Endpoint | Method | Authentication | Response Schema |
| :--- | :--- | :---: | :--- | :--- |
| **Twilio** | `/2010-04-01/Accounts/{SID}/Messages.json` | `POST` | Basic Auth (`AccountSid:AuthToken`) | Official Twilio `MessageInstance` JSON (`sid`, `status: "queued"`, RFC 2822 dates) |
| **Twilio Status** | `/2010-04-01/Accounts/{SID}/Messages/{MessageSid}.json` | `GET` | Basic Auth (`AccountSid:AuthToken`) | Updated status (`sent`, `delivered`, `failed`) |
| **Vonage (Nexmo)** | `/v1/messages` & `/sms/json` | `POST` | Bearer Token, Basic Auth, or `api_key`/`api_secret` | Vonage JSON (`message_uuid`, `messages: [{"status": "0"}]`) |
| **Plivo** | `/v1/Account/{authId}/Message/` | `POST` | Basic Auth (`authId:authToken`) | Plivo JSON (`message: "message(s) queued"`, `message_uuid`) |
| **Infobip** | `/sms/2/text/advanced` & `/sms/1/text/single` | `POST` | Header `Authorization: App {KEY}` | Infobip JSON (`bulkId`, `status: {"name": "PENDING_ENROUTE"}`) |
| **MSG91** | `/api/v5/flow/` & `/api/v2/sendsms` | `POST` | Header `authkey: {KEY}` | MSG91 JSON (`type: "success"`, `message: "..."`) |
| **Direct REST** | `/api/v1/messages` | `POST` | Bearer Token or Basic Auth | Clean JSON (`{"id": "...", "status": "queued"}`) |
| **Health Check** | `/` & `/health` | `GET` | None | Service status, IP, port, and version |

---

## Installation Guidelines

The compiled debug APK is located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Method 1: 1-Click ADB Install (Recommended & Bypasses Warnings)

If your phone is plugged into your Mac via USB with **USB Debugging** enabled:

```bash
~/Library/Android/sdk/platform-tools/adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

* `-r`: Replaces and updates any existing installation.
* `-g`: Automatically grants all runtime permissions (`SEND_SMS`, `READ_PHONE_STATE`, `POST_NOTIFICATIONS`) at install time.

### Method 2: Sideloading Manually (APK Transfer)

If transferring the `.apk` via Google Drive, WhatsApp, Telegram, or AirDrop:

#### 1. Google Play Protect Warning ("Blocked to protect your data")
Because this app is a private developer build requesting SMS access outside the Google Play Store, Android will show a security dialog:
1. Tap **"More details"** (or dropdown arrow) in the dialog.
2. Tap **"Install anyway"**.

#### 2. Android 13/14/15 Restricted Settings
On newer Android versions, sensitive permissions on sideloaded apps are temporarily restricted:
1. Go to phone **Settings** -> **Apps** -> **SMS Gateway**.
2. Tap the **3 vertical dots (⋮)** in the top right corner.
3. Tap **"Allow restricted settings"** and authenticate with your fingerprint or PIN.
4. Now open **Permissions** and enable **SMS**.

---

## App Setup & Configuration

1. **Exempt from Battery Optimizations**:
   * Open the app -> switch to the **Settings** tab -> tap **"Exempt From Battery Optimizations"**.
   * This is critical: it prevents Android from terminating the local server when the screen locks.
2. **Dual SIM Configuration**:
   * In the **Settings** tab, the app automatically detects active SIM cards. You can choose to use the **System Default SIM** or pin dispatching to a specific SIM slot (e.g. SIM 1 or SIM 2).
3. **Throttling Delay**:
   * Default is **1000ms** (1 second). Keep this between 1000ms–2000ms to avoid cellular carrier spam filters or Android OS rate limits.
4. **Start the Server**:
   * On the **Dashboard**, tap **Start**.
   * The status indicator turns green, displaying your phone's local network URL (e.g. `http://192.168.1.50:8080`).
5. **Quick In-App SMS Test**:
   * Before writing any backend code, enter your destination phone number in the **"Quick In-App SMS Test"** box on the Dashboard and tap **"Send Test SMS"** to verify that your cellular SIM card is sending messages correctly.

---

## Developer Commands & Terminal Cheatsheet

### Build the Project
```bash
# Compile debug APK
./gradlew assembleDebug

# Clean and rebuild
./gradlew clean assembleDebug
```

### ADB Operations
```bash
# Check connected Android devices
~/Library/Android/sdk/platform-tools/adb devices

# Install APK directly
~/Library/Android/sdk/platform-tools/adb install -r -g app/build/outputs/apk/debug/app-debug.apk

# Forward local port 8080 over USB cable (no Wi-Fi needed!)
~/Library/Android/sdk/platform-tools/adb reverse tcp:8080 tcp:8080

# View real-time application logs in terminal
~/Library/Android/sdk/platform-tools/adb logcat -s GatewayService Ktor
```

---

## Sending SMS: Provider Code Examples & cURL

### 1. Twilio Emulation

#### cURL
```bash
curl -X POST http://<PHONE_IP>:8080/2010-04-01/Accounts/<ACCOUNT_SID>/Messages.json \
  -u "<ACCOUNT_SID>:<AUTH_TOKEN>" \
  -d "To=+1234567890" \
  -d "Body=Hello from local Android SMS Gateway!"
```

#### Node.js / JavaScript (Fetch)
```javascript
const accountSid = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"; // From Dashboard
const authToken = "your_auth_token_here";               // From Dashboard
const phoneIp = "192.168.1.50";                         // Wi-Fi IP from Dashboard

async function sendSms(to, message) {
  const auth = Buffer.from(`${accountSid}:${authToken}`).toString("base64");
  const response = await fetch(`http://${phoneIp}:8080/2010-04-01/Accounts/${accountSid}/Messages.json`, {
    method: "POST",
    headers: {
      "Authorization": `Basic ${auth}`,
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: new URLSearchParams({
      To: to,
      Body: message
    }).toString()
  });

  const data = await response.json();
  console.log("Message SID:", data.sid);
  console.log("Status:", data.status);
  return data;
}

sendSms("+1234567890", "Your verification code is 482910");
```

#### Python (Twilio Official SDK)
```python
from twilio.rest import Client

ACCOUNT_SID = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
AUTH_TOKEN = "your_auth_token_here"
PHONE_IP = "192.168.1.50"

client = Client(ACCOUNT_SID, AUTH_TOKEN)
# Route SDK requests to your Android device
client.http_client.base_url = f"http://{PHONE_IP}:8080"

message = client.messages.create(
    to="+1234567890",
    from_="+0987654321",
    body="Your verification code is 482910"
)

print(f"Message Queued! SID: {message.sid}, Status: {message.status}")
```

---

### 2. Vonage (Nexmo) Emulation

#### cURL
```bash
curl -X POST http://<PHONE_IP>:8080/v1/messages \
  -H "Authorization: Bearer <AUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"to": "+1234567890", "text": "Your verification code is 482910"}'
```

#### Expected JSON Response
```json
{
  "message_count": "1",
  "messages": [
    {
      "to": "+1234567890",
      "message_id": "9a8b7c6d-5e4f-3a2b-1c0d-e9f8a7b6c5d4",
      "status": "0",
      "remaining_balance": "10.00",
      "message_price": "0.0000",
      "network": "12345"
    }
  ],
  "message_uuid": "9a8b7c6d-5e4f-3a2b-1c0d-e9f8a7b6c5d4"
}
```

---

### 3. Plivo Emulation

#### cURL
```bash
curl -X POST http://<PHONE_IP>:8080/v1/Account/<AUTH_ID>/Message/ \
  -u "<AUTH_ID>:<AUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"dst": "+1234567890", "text": "Your verification code is 482910"}'
```

#### Expected JSON Response
```json
{
  "message": "message(s) queued",
  "message_uuid": ["9a8b7c6d-5e4f-3a2b-1c0d-e9f8a7b6c5d4"],
  "api_id": "4a3b2c1d-0e9f-8a7b-6c5d-4e3f2a1b0c9d"
}
```

---

### 4. Infobip Emulation

#### cURL
```bash
curl -X POST http://<PHONE_IP>:8080/sms/2/text/advanced \
  -H "Authorization: App <AUTH_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"messages": [{"destinations": [{"to": "+1234567890"}], "text": "Your verification code is 482910"}]}'
```

#### Expected JSON Response
```json
{
  "bulkId": "1a2b3c4d-5e6f-7a8b-9c0d-e1f2a3b4c5d6",
  "messages": [
    {
      "to": "+1234567890",
      "status": {
        "groupId": 1,
        "groupName": "PENDING",
        "id": 7,
        "name": "PENDING_ENROUTE",
        "description": "Message sent to next instance"
      },
      "messageId": "9a8b7c6d-5e4f-3a2b-1c0d-e9f8a7b6c5d4"
    }
  ]
}
```

---

### 5. MSG91 Emulation

#### cURL
```bash
curl -X POST http://<PHONE_IP>:8080/api/v5/flow/ \
  -H "authkey: <MSG91_AUTHKEY>" \
  -H "Content-Type: application/json" \
  -d '{"mobiles": "+1234567890", "message": "Your verification code is 482910"}'
```

#### Expected JSON Response
```json
{
  "message": "346166447867373934333632",
  "type": "success"
}
```

---

## Testing Over USB Without Wi-Fi (`adb reverse`)

If your phone and development machine are on different subnets or you are on a restricted network (like public coffee shop Wi-Fi) where device-to-device traffic is blocked:

1. Connect your phone via USB cable.
2. Run this command on your Mac:
   ```bash
   ~/Library/Android/sdk/platform-tools/adb reverse tcp:8080 tcp:8080
   ```
3. You can now send requests directly to `http://localhost:8080` from your Mac, and ADB forwards the network packets over USB directly into the phone's gateway server.

---

## Switching to Production

Because the gateway responses and status codes are byte-for-byte identical to the real providers:

1. **Twilio**: Change `base_url` back to `https://api.twilio.com` and swap in your live Twilio Account SID and Auth Token.
2. **Vonage**: Change `base_url` to `https://api.nexmo.com` and use your live API Key and Secret.
3. **Plivo**: Change `base_url` to `https://api.plivo.com` and use your live Auth ID and Auth Token.
4. **Infobip**: Change base URL to your Infobip base URL (e.g. `https://xxxxxx.api.infobip.com`) and use your live API key.
5. **MSG91**: Change base URL to `https://control.msg91.com` and use your live AuthKey.

**Zero code refactoring or response-parsing changes are required.**
