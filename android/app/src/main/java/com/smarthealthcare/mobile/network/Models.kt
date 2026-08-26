package com.smarthealthcare.mobile.network

data class AuthRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val age: Int? = null,
    val gender: String = "",
    val phone: String = "",
    val blood_group: String = ""
)

data class UserDto(
    val id: Int = 0,
    val name: String = "",
    val email: String = "",
    val age: Int? = null,
    val gender: String? = null,
    val phone: String? = null,
    val blood_group: String? = null
)

data class AuthResponse(
    val success: Boolean = false,
    val message: String = "",
    val token: String? = null,
    val user: UserDto? = null
)

data class HealthResponse(
    val success: Boolean = false,
    val service: String? = null,
    val status: String? = null,
    val version: String? = null
)

data class SymptomsResponse(
    val success: Boolean = false,
    val symptoms: List<String> = emptyList()
)

data class PredictRequest(val symptoms: List<String>)

data class TopPrediction(
    val disease: String = "",
    val confidence: Double = 0.0
)

data class PredictionDto(
    val id: Int = 0,
    val disease: String = "",
    val confidence: Double = 0.0,
    val symptoms: List<String> = emptyList(),
    val severity: String = "",
    val specialist: String = "",
    val created_at: String? = null
)

data class PredictionResult(
    val disease: String = "",
    val confidence: Double = 0.0,
    val severity: String = "",
    val specialist: String = "",
    val precautions: String = "",
    val top3: List<TopPrediction> = emptyList(),
    val symptoms_selected: List<String> = emptyList(),
    val disclaimer: String = ""
)

data class PredictResponse(
    val success: Boolean = false,
    val message: String? = null,
    val prediction: PredictionResult? = null
)

data class HistoryResponse(
    val success: Boolean = false,
    val predictions: List<PredictionDto> = emptyList()
)

data class StatsResponse(
    val success: Boolean = false,
    val total_predictions: Int = 0,
    val total_appointments: Int = 0,
    val disease_counts: Map<String, Int> = emptyMap()
)

data class DoctorDto(
    val id: Int = 0,
    val name: String = "",
    val email: String = "",
    val department: String? = null,
    val phone: String? = null
)

data class DoctorsResponse(
    val success: Boolean = false,
    val doctors: List<DoctorDto> = emptyList()
)

data class DoctorAuthResponse(
    val success: Boolean = false,
    val message: String = "",
    val token: String? = null,
    val doctor: DoctorDto? = null
)

data class DoctorProfileResponse(
    val success: Boolean = false,
    val doctor: DoctorDto? = null
)

data class DoctorAppointmentDto(
    val id: Int = 0,
    val patient_id: Int = 0,
    val patient_name: String = "",
    val patient_email: String = "",
    val patient_phone: String? = null,
    val patient_age: Int? = null,
    val patient_gender: String? = null,
    val patient_blood_group: String? = null,
    val date: String = "",
    val time: String = "",
    val reason: String = "",
    val status: String = "",
    val doctor_note: String? = null,
    val created_at: String? = null
)

data class DoctorAppointmentsData(
    val pending: List<DoctorAppointmentDto> = emptyList(),
    val upcoming: List<DoctorAppointmentDto> = emptyList(),
    val history: List<DoctorAppointmentDto> = emptyList()
)

data class DoctorAppointmentsResponse(
    val success: Boolean = false,
    val data: DoctorAppointmentsData? = null
)

data class DoctorNotificationDto(
    val id: Int = 0,
    val message: String = "",
    val appointment_id: Int? = null,
    val created_at: String? = null
)

data class DoctorNotificationsResponse(
    val success: Boolean = false,
    val notifications: List<DoctorNotificationDto> = emptyList(),
    val count: Int = 0
)

data class DoctorActionRequest(
    val note: String = ""
)

data class AppointmentRequest(
    val doctor_id: Int,
    val date: String,
    val time: String,
    val reason: String
)

data class AppointmentDto(
    val id: Int = 0,
    val doctor_id: Int? = null,
    val doctor_name: String = "",
    val department: String? = null,
    val date: String = "",
    val time: String = "",
    val reason: String = "",
    val status: String = "",
    val doctor_note: String? = null,
    val created_at: String? = null
)

data class AppointmentsResponse(
    val success: Boolean = false,
    val appointments: List<AppointmentDto> = emptyList()
)

data class AppointmentResponse(
    val success: Boolean = false,
    val message: String = "",
    val appointment: AppointmentDto? = null
)

data class NotificationDto(
    val id: Int = 0,
    val message: String = "",
    val appointment_id: Int? = null,
    val created_at: String? = null
)

data class NotificationsResponse(
    val success: Boolean = false,
    val notifications: List<NotificationDto> = emptyList()
)

data class ChatRequest(val message: String)

data class ChatResponse(
    val success: Boolean = false,
    val reply: String = "",
    val disclaimer: String? = null
)

data class SimpleResponse(
    val success: Boolean = false,
    val message: String? = null
)
