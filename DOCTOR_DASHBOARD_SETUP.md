# SmartHealthcare Doctor Dashboard

## Added
- Doctor Login inside the Android app
- Doctor Dashboard
- Pending appointment requests
- Upcoming appointments
- Recent appointment history
- Patient name, email, phone, age, gender and blood group
- Accept / Reject appointment requests
- Doctor notes
- Mark accepted appointments as Completed
- Doctor logout and refresh
- Android Back button returns from dashboard screens without closing the app

## Backend
The Flask backend needs the matching doctor mobile API routes included in the updated full project ZIP:
- `POST /api/doctor/login`
- `GET /api/doctor/profile`
- `GET /api/mobile/doctor/appointments`
- `GET /api/mobile/doctor/notifications`
- `POST /api/mobile/doctor/appointments/<id>/accept`
- `POST /api/mobile/doctor/appointments/<id>/reject`
- `POST /api/mobile/doctor/appointments/<id>/complete`

## Create a doctor account
The existing Flask web portal already has a doctor registration page:
`http://YOUR-PC-IP:5000/doctor/register`

After creating a doctor account, use the same email/password in the Android app's **Doctor Login**.

## Run
1. Start Flask with `python app.py`.
2. Confirm `ApiConfig.kt` points to the PC IP and port 5000.
3. Open the `android` folder in Android Studio.
4. Sync Gradle and run the app.
