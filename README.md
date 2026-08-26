# SmartHealthcare — ML + Android + Health Chatbot

This version keeps the original Flask web application and adds a mobile-ready REST API plus a complete Android Studio project.

## Architecture

Android (Kotlin + Jetpack Compose)
→ REST API (Flask)
→ ML model (`models/best_model.pkl`) + SQLite

## Backend setup

Open this folder in VS Code:

```powershell
python -m pip install -r requirements.txt
python app.py
```

The API will run at:

- `http://127.0.0.1:5000`
- Android emulator: `http://10.0.2.2:5000`

Test:

```text
http://127.0.0.1:5000/api/health
```

### ML model version

The included model was serialized with scikit-learn 1.8.0. Keep the environment on `scikit-learn==1.8.0` to avoid model compatibility warnings/errors.

## Android setup

1. Open the `android` folder in Android Studio.
2. Let Gradle sync and download dependencies.
3. Start an Android Emulator.
4. Make sure the Flask server is running on the PC.
5. Run the app.

The default API URL is:

```text
http://10.0.2.2:5000/
```

This is correct for the Android Studio emulator.

### Real Android phone

Connect the phone and PC to the same Wi-Fi. Find the PC IPv4 address with:

```powershell
ipconfig
```

Then edit:

`android/app/src/main/java/com/smarthealthcare/mobile/network/ApiConfig.kt`

Example:

```kotlin
const val BASE_URL = "http://192.168.1.10:5000/"
```

Also allow Python/Flask through Windows Firewall when Windows asks.

## Included mobile features

- Login and registration
- Professional dashboard
- ML symptom-based disease prediction
- Top-3 predictions and confidence
- Prediction history
- Doctor list and appointment booking
- Appointment status / cancellation
- Patient notifications
- Health chatbot (offline rule-based assistant; no API key required)
- Profile
- Logout

## REST endpoints

- `GET /api/health`
- `POST /api/register`
- `POST /api/login`
- `GET /api/profile`
- `GET /api/symptoms`
- `POST /api/predict`
- `GET /api/history`
- `GET /api/stats`
- `GET /api/doctors`
- `GET /api/appointments`
- `POST /api/appointments`
- `POST /api/appointments/<id>/cancel`
- `GET /api/notifications`
- `POST /api/notifications/<id>/read`
- `POST /api/chat`

Mobile login/register returns a signed Bearer token valid for 7 days.

## Important

The ML predictor and chatbot provide general/educational information only. They are not a substitute for professional medical diagnosis or emergency care.
