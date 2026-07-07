# Secure URL Receiver (Android & Chrome Extension)

This repository contains the client-side components of the secure URL sharing system: a native Android application that receives URLs in real-time, and a Google Chrome Extension to send browser URLs with a single click.

Communication is securely bridged via a public WebSocket cloud broker.

---

## 🔗 Architecture Components

1. **Android App (Client)**: A native Android application containing a Foreground Service that connects to the cloud relay.
2. **Chrome Extension (Sender)**: A Manifest V3 Chrome Extension that pings the relay server via HTTP POST to transmit active tab URLs.
3. **Cloud Broker**: The server codebase running on the cloud that relays messages between the clients.
   - **Repository Link**: [blaynewatt/web-relay](https://github.com/blaynewatt/web-relay)

---

## 📱 Android App Setup

The Android app supports two connection modes:
- **Local Server**: Starts a local WebSocket server listening on a custom port on your phone (requires direct ADB port forwarding/reversing).
- **Cloud Relay (Recommended)**: Establishes a persistent, auto-reconnecting WebSocket connection to the cloud broker over cellular networks or Wi-Fi.

### Setup Instructions:
1. Compile and install the APK to your phone.
2. Open the app and navigate to **Configuration**.
3. Select the **Cloud Relay** tab.
4. Input your relay server address:
   - Example: `wss://my-address.onrender.com`
5. Choose a private, unique **Secret Session Token** (e.g., `my-secure-token`).
6. Enable your desired automations:
   - **Auto-open URLs in browser** (launches default browser when URL arrives).
   - **Auto-copy URLs to clipboard**.
7. Tap the **Start** button at the top. The status will change to **Client: ACTIVE**.

---

## 🌐 Chrome Extension Setup

The Chrome Extension allows you to transmit your active browser tab to your phone with a single click.

### Installation Instructions:
1. Open Google Chrome and go to **`chrome://extensions/`**.
2. Enable **Developer mode** (toggle switch in the top-right corner).
3. Click the **Load unpacked** button (top-left corner).
4. Choose the `extension` folder inside this repository:
   - Path: `url-receiver-android/extension`
5. Pin the **URL Sender** extension icon in your Chrome toolbar.
6. Click the extension icon, expand the **Connection Settings**, and configure:
   - **Relay Server URL**: Point to your web service (e.g., `https://my-address.onrender.com`).
   - **Secret Token**: Set the **exact matching token** configured on your Android app.

---

## 🔒 Security

To prevent unauthorized devices from intercepting or sending URLs:
- The server groups clients into session rooms based on their **Secret Session Token**.
- Messages are only forwarded if the sender's HTTP POST payload token matches the WebSocket query parameter token.
- Keep your session token private!
