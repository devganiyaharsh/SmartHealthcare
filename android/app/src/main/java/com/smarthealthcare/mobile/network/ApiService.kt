package com.smarthealthcare.mobile.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("api/doctor/login")
    suspend fun doctorLogin(@Body request: AuthRequest): DoctorAuthResponse

    @GET("api/doctor/profile")
    suspend fun doctorProfile(): DoctorProfileResponse

    @GET("api/mobile/doctor/appointments")
    suspend fun doctorAppointments(): DoctorAppointmentsResponse

    @GET("api/mobile/doctor/notifications")
    suspend fun doctorNotifications(): DoctorNotificationsResponse

    @POST("api/mobile/doctor/appointments/{id}/accept")
    suspend fun acceptDoctorAppointment(@Path("id") id: Int, @Body request: DoctorActionRequest): SimpleResponse

    @POST("api/mobile/doctor/appointments/{id}/reject")
    suspend fun rejectDoctorAppointment(@Path("id") id: Int, @Body request: DoctorActionRequest): SimpleResponse

    @POST("api/mobile/doctor/appointments/{id}/complete")
    suspend fun completeDoctorAppointment(@Path("id") id: Int, @Body request: DoctorActionRequest): SimpleResponse

    @GET("api/profile")
    suspend fun profile(): ProfileResponse

    @GET("api/symptoms")
    suspend fun symptoms(): SymptomsResponse

    @POST("api/predict")
    suspend fun predict(@Body request: PredictRequest): PredictResponse

    @GET("api/history")
    suspend fun history(@Query("limit") limit: Int = 50): HistoryResponse

    @GET("api/stats")
    suspend fun stats(): StatsResponse

    @GET("api/doctors")
    suspend fun doctors(): DoctorsResponse

    @GET("api/appointments")
    suspend fun appointments(): AppointmentsResponse

    @POST("api/appointments")
    suspend fun bookAppointment(@Body request: AppointmentRequest): AppointmentResponse

    @POST("api/appointments/{id}/cancel")
    suspend fun cancelAppointment(@Path("id") id: Int): SimpleResponse

    @GET("api/notifications")
    suspend fun notifications(): NotificationsResponse

    @POST("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: Int): SimpleResponse

    @POST("api/chat")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

data class ProfileResponse(
    val success: Boolean = false,
    val user: UserDto? = null
)

class TokenInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val token = tokenProvider()
        val request = chain.request().newBuilder()
        if (!token.isNullOrBlank()) {
            request.addHeader("Authorization", "Bearer $token")
        }
        return chain.proceed(request.build())
    }
}

object ApiClient {
    private var token: String? = null

    fun setToken(value: String?) {
        token = value
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(TokenInterceptor { token })
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: ApiService = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
