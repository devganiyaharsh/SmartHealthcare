# SmartHealthcare Android App

Open this `android` folder directly in Android Studio.

## Run order

1. Start Flask from the parent project:
   `python app.py`
2. Wait for:
   `Database initialized!`
3. Open this Android folder in Android Studio.
4. Let Gradle sync.
5. Start an emulator.
6. Run the `app` configuration.

## API address

Emulator:
`http://10.0.2.2:5000/`

Real phone:
replace the value in:
`app/src/main/java/com/smarthealthcare/mobile/network/ApiConfig.kt`

with your PC IPv4, for example:
`http://192.168.1.10:5000/`

The Flask server is configured to listen on `0.0.0.0:5000`, which allows a phone on the same LAN to reach it.

## Test backend first

Open in the PC browser:

`http://127.0.0.1:5000/api/health`

Expected response contains:
`"status": "online"`

## Main screens

Home → ML Predict → History → Appointments → AI Chat

The chatbot works without an API key and provides general health guidance. It is deliberately not a diagnostic system.
