package com.smarthealthcare.mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smarthealthcare.mobile.network.*
import kotlinx.coroutines.launch
import retrofit2.HttpException
import android.util.Log


// ---------- Brand palette ----------
private val BrandTeal = Color(0xFF0D9488)
private val BrandTealDark = Color(0xFF0F766E)
private val BrandTealLight = Color(0xFF14B8A6)
private val BrandIndigo = Color(0xFF6366F1)
private val BgSoft = Color(0xFFF3F7FA)
private val TextDark = Color(0xFF0F172A)
private val TextMuted = Color(0xFF64748B)
private val TextFaint = Color(0xFF94A3B8)
private val BorderSoft = Color(0xFFE2E8F0)
private val TealContainer = Color(0xFFCCFBF1)
private val SuccessGreen = Color(0xFF16A34A)
private val WarningAmber = Color(0xFFD97706)
private val DangerRed = Color(0xFFDC2626)
private val InfoBlue = Color(0xFF2563EB)

private val LightColors = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Color(0xFF042F2C),
    secondary = BrandIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E3FF),
    tertiary = BrandTealLight,
    background = BgSoft,
    onBackground = TextDark,
    surface = Color.White,
    onSurface = TextDark,
    surfaceVariant = Color(0xFFEDF2F7),
    onSurfaceVariant = TextMuted,
    outline = BorderSoft,
    error = DangerRed
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    secondary = Color(0xFFA5B4FC)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = LightColors) {
                SmartHealthcareApp()
            }
        }
    }
}

enum class Screen(val label: String) {
    HOME("Home"), PREDICT("Predict"), HISTORY("History"), APPOINTMENTS("Appointments"), CHAT("AI Chat")
}

class HealthcareViewModel : ViewModel() {
    private var prefs: android.content.SharedPreferences? = null

    var token by mutableStateOf<String?>(null)
        private set
    var user by mutableStateOf<UserDto?>(null)
        private set
    var symptoms by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedSymptoms by mutableStateOf<Set<String>>(emptySet())
        private set
    var prediction by mutableStateOf<PredictionResult?>(null)
        private set
    var history by mutableStateOf<List<PredictionDto>>(emptyList())
        private set
    var stats by mutableStateOf<StatsResponse?>(null)
        private set
    var doctors by mutableStateOf<List<DoctorDto>>(emptyList())
        private set
    var appointments by mutableStateOf<List<AppointmentDto>>(emptyList())
        private set
    var notifications by mutableStateOf<List<NotificationDto>>(emptyList())
        private set
    var doctorNotifications by mutableStateOf<List<DoctorNotificationDto>>(emptyList())
        private set
    var doctor by mutableStateOf<DoctorDto?>(null)
        private set
    var doctorMode by mutableStateOf(false)
        private set
    var doctorAppointments by mutableStateOf<DoctorAppointmentsData?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("smart_health", Context.MODE_PRIVATE)
        val savedDoctorToken = prefs?.getString("doctor_token", null)
        val savedPatientToken = prefs?.getString("token", null)

        if (!savedDoctorToken.isNullOrBlank()) {
            doctorMode = true
            token = savedDoctorToken
            ApiClient.setToken(token)
            viewModelScope.launch {
                runCatching {
                    val profile = ApiClient.service.doctorProfile()
                    doctor = profile.doctor
                    loadDoctorAppointments()
                }.onFailure {
                    logout()
                }
            }
        } else {
            token = savedPatientToken
            ApiClient.setToken(token)
            if (token != null) {
                viewModelScope.launch {
                    runCatching {
                        val profile = ApiClient.service.profile()
                        user = profile.user
                        refreshHome()
                    }.onFailure {
                        logout()
                    }
                }
            }
        }
    }

    fun clearMessage() { message = null }

    private fun errorText(t: Throwable): String {
        return when (t) {
            is HttpException -> "Server error (${t.code()}). Check that Flask is running."
            else -> t.message ?: "Something went wrong. Please try again."
        }
    }

    fun login(email: String, password: String, onDone: () -> Unit) {
        if (email.isBlank() || password.isBlank()) { message = "Enter email and password."; return }
        loading = true
        viewModelScope.launch {
            try {
                val result = ApiClient.service.login(AuthRequest(email.trim(), password))
                if (result.success && result.token != null) {
                    token = result.token
                    user = result.user
                    prefs?.edit()?.putString("token", token)?.apply()
                    ApiClient.setToken(token)
                    refreshHome()
                    onDone()
                } else message = result.message.ifBlank { "Login failed." }
            } catch (e: Exception) { message = errorText(e) }
            finally { loading = false }
        }
    }

    fun register(req: RegisterRequest, onDone: () -> Unit) {
        if (req.name.isBlank() || req.email.isBlank() || req.password.length < 6) {
            message = "Name, valid email and a 6+ character password are required."
            return
        }

        loading = true

        viewModelScope.launch {
            try {
                val result = ApiClient.service.register(req)

                if (result.success) {

                    // Registration ke baad AUTO LOGIN nahi hoga
                    token = null
                    user = null

                    // Koi purana patient token ho to remove karo
                    prefs?.edit()
                        ?.remove("token")
                        ?.apply()

                    ApiClient.setToken(null)

                    // Success message
                    message = "Account created successfully. Please login."

                    // AuthScreen ko Login mode me le jao
                    onDone()

                } else {
                    message = result.message.ifBlank {
                        "Registration failed."
                    }
                }

            } catch (e: Exception) {
                message = errorText(e)

            } finally {
                loading = false
            }
        }
    }

    fun doctorLogin(
        email: String,
        password: String,
        onDone: () -> Unit
    ) {
        if (email.isBlank() || password.isBlank()) {
            message = "Enter doctor email and password."
            return
        }

        loading = true

        viewModelScope.launch {
            try {

                val result = ApiClient.service.doctorLogin(
                    AuthRequest(
                        email.trim(),
                        password
                    )
                )

                Log.d("DOCTOR_LOGIN", "success = ${result.success}")
                Log.d("DOCTOR_LOGIN", "message = ${result.message}")
                Log.d("DOCTOR_LOGIN", "token = ${result.token}")
                Log.d("DOCTOR_LOGIN", "doctor = ${result.doctor}")

                if (
                    result.success &&
                    result.token != null &&
                    result.doctor != null
                ) {

                    doctorMode = true

                    token = result.token
                    doctor = result.doctor

                    prefs?.edit()
                        ?.remove("token")
                        ?.putString("doctor_token", token)
                        ?.apply()

                    ApiClient.setToken(token)

                    loadDoctorAppointments()

                    onDone()

                } else {

                    message = result.message.ifBlank {
                        "Doctor login failed."
                    }
                }

            } catch (e: Exception) {

                Log.e(
                    "DOCTOR_LOGIN",
                    "Login exception",
                    e
                )

                message = errorText(e)

            } finally {

                loading = false
            }
        }
    }

    fun loadDoctorAppointments() {
        viewModelScope.launch {
            try {
                doctorAppointments = ApiClient.service.doctorAppointments().data
                doctorNotifications = ApiClient.service.doctorNotifications().notifications
            } catch (e: Exception) {
                message = errorText(e)
            }
        }
    }

    fun doctorRespond(id: Int, action: String, note: String = "") {
        loading = true
        viewModelScope.launch {
            try {
                val result = when (action) {
                    "accept" -> ApiClient.service.acceptDoctorAppointment(id, DoctorActionRequest(note))
                    "reject" -> ApiClient.service.rejectDoctorAppointment(id, DoctorActionRequest(note))
                    else -> ApiClient.service.completeDoctorAppointment(id, DoctorActionRequest(note))
                }
                message = result.message ?: "Appointment updated."
                loadDoctorAppointments()
            } catch (e: Exception) {
                message = errorText(e)
            } finally {
                loading = false
            }
        }
    }

    fun logout() {
        token = null
        user = null
        doctor = null
        doctorMode = false
        doctorAppointments = null
        prefs?.edit()?.remove("token")?.remove("doctor_token")?.apply()
        ApiClient.setToken(null)
        selectedSymptoms = emptySet()
        prediction = null
    }

    fun refreshHome() {
        viewModelScope.launch {
            try {
                val s = ApiClient.service.stats()
                stats = s
                val n = ApiClient.service.notifications()
                notifications = n.notifications
            } catch (e: Exception) { message = errorText(e) }
        }
    }

    fun loadSymptoms() {
        if (symptoms.isNotEmpty()) return
        viewModelScope.launch {
            try { symptoms = ApiClient.service.symptoms().symptoms }
            catch (e: Exception) { message = errorText(e) }
        }
    }

    fun toggleSymptom(value: String) {
        selectedSymptoms = if (value in selectedSymptoms) selectedSymptoms - value else selectedSymptoms + value
    }

    fun predict() {
        if (selectedSymptoms.isEmpty()) { message = "Select at least one symptom."; return }
        loading = true
        viewModelScope.launch {
            try {
                val result = ApiClient.service.predict(PredictRequest(selectedSymptoms.toList()))
                if (result.success) {
                    prediction = result.prediction
                    loadHistory()
                    refreshHome()
                } else message = result.message ?: "Prediction failed."
            } catch (e: Exception) { message = errorText(e) }
            finally { loading = false }
        }
    }

    fun loadHistory() {
        viewModelScope.launch {
            try { history = ApiClient.service.history().predictions }
            catch (e: Exception) { message = errorText(e) }
        }
    }

    fun loadAppointments() {
        viewModelScope.launch {
            try {
                appointments = ApiClient.service.appointments().appointments
                doctors = ApiClient.service.doctors().doctors
            } catch (e: Exception) { message = errorText(e) }
        }
    }

    fun bookAppointment(req: AppointmentRequest) {
        loading = true
        viewModelScope.launch {
            try {
                val result = ApiClient.service.bookAppointment(req)
                if (result.success) {
                    message = "Appointment request sent successfully."
                    loadAppointments()
                    refreshHome()
                } else message = result.message
            } catch (e: Exception) { message = errorText(e) }
            finally { loading = false }
        }
    }

    fun cancelAppointment(id: Int) {
        viewModelScope.launch {
            try {
                val result = ApiClient.service.cancelAppointment(id)
                message = result.message ?: "Appointment cancelled."
                loadAppointments()
            } catch (e: Exception) { message = errorText(e) }
        }
    }

    fun loadNotifications() {
        viewModelScope.launch {
            try { notifications = ApiClient.service.notifications().notifications }
            catch (e: Exception) { message = errorText(e) }
        }
    }

    fun markNotificationRead(id: Int) {
        viewModelScope.launch {
            try {
                ApiClient.service.markNotificationRead(id)
                notifications = notifications.filterNot { it.id == id }
            } catch (_: Exception) {}
        }
    }

    fun sendChat(text: String, onReply: (String) -> Unit) {
        if (text.isBlank()) return
        viewModelScope.launch {
            try {
                val result = ApiClient.service.chat(ChatRequest(text))
                if (result.success) onReply(result.reply) else message = "Chatbot could not respond."
            } catch (e: Exception) { message = errorText(e) }
        }
    }
}

// ---------- Root ----------
@Composable
fun SmartHealthcareApp(vm: HealthcareViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.init(context) }

    var screen by remember { mutableStateOf(Screen.HOME) }

    Box(Modifier.fillMaxSize()) {
        if (vm.token == null) {
            AuthScreen(vm)
        } else if (vm.doctorMode) {
            BackHandler(enabled = screen != Screen.HOME) {
                screen = Screen.HOME
            }
            DoctorScaffold(
                vm = vm,
                screen = screen,
                onScreenChange = { screen = it }
            )
        } else {
            BackHandler(enabled = screen != Screen.HOME) {
                screen = Screen.HOME
            }
            MainScaffold(
                vm = vm,
                screen = screen,
                onScreenChange = { screen = it }
            )
        }

        AnimatedVisibility(
            visible = vm.message != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val msg = vm.message
            if (msg != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clickable { vm.clearMessage() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF5EEAD4))
                        Spacer(Modifier.width(12.dp))
                        Text(msg, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Icon(Icons.Default.Close, null, tint = Color.White.copy(alpha = .7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ---------- Auth ----------
@Composable
fun AuthScreen(vm: HealthcareViewModel) {
    var register by remember { mutableStateOf(false) }
    var doctorLogin by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var blood by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), BrandTealDark, BgSoft)
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(vertical = 40.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.HealthAndSafety, null, tint = BrandTeal, modifier = Modifier.size(38.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("SmartHealthcare", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Your intelligent health companion",
                        color = Color.White.copy(alpha = .85f),
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(22.dp)) {
                        if (!doctorLogin) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(4.dp)
                            ) {
                                SegmentButton("Sign in", !register, Modifier.weight(1f)) { register = false }
                                SegmentButton("Register", register, Modifier.weight(1f)) { register = true }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Text(
                            when {
                                doctorLogin -> "Doctor Sign in"
                                register -> "Create your health account"
                                else -> "Welcome back"
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            when {
                                doctorLogin -> "Manage appointments and patients from your doctor dashboard"
                                register -> "Fill in your details to get started"
                                else -> "Sign in to continue your health journey"
                            },
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                        Spacer(Modifier.height(16.dp))

                        if (register && !doctorLogin) {
                            Field("Full name", name, { name = it }, "e.g. Harsh Devganiya", icon = Icons.Default.Person)
                        }
                        Field("Email", email, { email = it }, "you@example.com", KeyboardType.Email, icon = Icons.Default.Email)
                        Field("Password", password, { password = it }, "Minimum 6 characters", password = true, icon = Icons.Default.Lock)

                        if (register && !doctorLogin) {
                            Row(Modifier.fillMaxWidth()) {
                                Box(Modifier.weight(1f)) { Field("Age", age, { age = it }, "Optional", KeyboardType.Number, icon = Icons.Default.Cake) }
                                Spacer(Modifier.width(10.dp))
                                Box(Modifier.weight(1f)) { Field("Gender", gender, { gender = it }, "Optional", icon = Icons.Default.Wc) }
                            }
                            Field("Phone", phone, { phone = it }, "Optional", KeyboardType.Phone, icon = Icons.Default.Phone)
                            Field("Blood group", blood, { blood = it }, "Optional", icon = Icons.Default.Bloodtype)
                        }

                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                when {
                                    doctorLogin -> vm.doctorLogin(email, password) {}
                                    register -> vm.register(
                                        RegisterRequest(
                                            name,
                                            email,
                                            password,
                                            age.toIntOrNull(),
                                            gender,
                                            phone,
                                            blood
                                        )
                                    ) {
                                        register = false
                                        password = ""
                                    }
                                    else -> vm.login(email, password) {}
                                }
                            },
                            enabled = !vm.loading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            if (vm.loading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    when {
                                        doctorLogin -> "Sign in as Doctor"
                                        register -> "Create account"
                                        else -> "Sign in"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (!doctorLogin) {
                            TextButton(onClick = { register = !register }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    if (register) "Already have an account? Sign in" else "New here? Create an account",
                                    color = BrandTeal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                doctorLogin = !doctorLogin
                                register = false
                                email = ""
                                password = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandIndigo)
                        ) {
                            Icon(if (doctorLogin) Icons.Default.Person else Icons.Default.MedicalServices, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (doctorLogin) "Patient Login" else "Doctor Login")
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Educational project - not a substitute for professional medical advice",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SegmentButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) BrandTeal else TextMuted
        )
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    icon: ImageVector? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = if (icon != null) {
            { Icon(icon, contentDescription = null, tint = BrandTeal) }
        } else null,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandTeal,
            focusedLabelColor = BrandTeal,
            cursorColor = BrandTeal
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next)
    )
}

// ---------- Doctor Dashboard ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorScaffold(
    vm: HealthcareViewModel,
    screen: Screen,
    onScreenChange: (Screen) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Doctor Dashboard", fontWeight = FontWeight.ExtraBold)
                        Text("Dr. ${vm.doctor?.name ?: "Doctor"}", fontSize = 12.sp, color = TextMuted)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadDoctorAppointments() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { vm.logout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = BgSoft
    ) { padding ->
        DoctorDashboardScreen(vm, padding)
    }
}

@Composable
fun DoctorDashboardScreen(vm: HealthcareViewModel, padding: PaddingValues) {
    LaunchedEffect(Unit) { vm.loadDoctorAppointments() }
    val data = vm.doctorAppointments
    val pending = data?.pending.orEmpty()
    val upcoming = data?.upcoming.orEmpty()
    val history = data?.history.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = BrandTeal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Manage your patients", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Review appointment requests, respond to patients and manage your schedule.",
                        color = Color.White.copy(alpha = .9f),
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DoctorStatCard("Pending", pending.size.toString(), Icons.Default.PendingActions, WarningAmber, Modifier.weight(1f))
                DoctorStatCard("Upcoming", upcoming.size.toString(), Icons.Default.Event, InfoBlue, Modifier.weight(1f))
                DoctorStatCard("History", history.size.toString(), Icons.Default.History, BrandIndigo, Modifier.weight(1f))
            }
        }

        item {
            Text("Appointment requests", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextDark)
        }

        if (pending.isEmpty()) {
            item { EmptyCard("No pending appointment requests.", Icons.Default.EventAvailable) }
        } else {
            items(pending, key = { it.id }) { appointment ->
                DoctorAppointmentCard(vm, appointment, showActions = true)
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Upcoming appointments", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextDark)
        }

        if (upcoming.isEmpty()) {
            item { EmptyCard("No upcoming appointments.", Icons.Default.Event) }
        } else {
            items(upcoming, key = { it.id }) { appointment ->
                DoctorAppointmentCard(vm, appointment, showActions = appointment.status == "Accepted")
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Recent patient history", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextDark)
        }

        if (history.isEmpty()) {
            item { EmptyCard("No appointment history yet.", Icons.Default.History) }
        } else {
            items(history, key = { it.id }) { appointment ->
                DoctorAppointmentCard(vm, appointment, showActions = false)
            }
        }
    }
}

@Composable
fun DoctorStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            Text(title, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun DoctorAppointmentCard(
    vm: HealthcareViewModel,
    appointment: DoctorAppointmentDto,
    showActions: Boolean
) {
    var note by remember(appointment.id) { mutableStateOf("") }
    var showNote by remember(appointment.id) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(TealContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = BrandTeal)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(appointment.patient_name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                    Text(appointment.patient_email, fontSize = 12.sp, color = TextMuted)
                }
                AssistChip(
                    onClick = {},
                    label = { Text(appointment.status) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = statusColor(appointment.status).copy(alpha = .12f),
                        labelColor = statusColor(appointment.status)
                    )
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, null, tint = BrandTeal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text("${appointment.date} • ${appointment.time}", fontSize = 13.sp, color = TextDark)
            }
            Spacer(Modifier.height(6.dp))
            Text("Reason: ${appointment.reason}", fontSize = 13.sp, color = TextMuted)

            if (showActions && appointment.status == "Pending") {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Doctor note (optional)") },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.doctorRespond(appointment.id, "accept", note) },
                        enabled = !vm.loading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Accept") }
                    OutlinedButton(
                        onClick = { vm.doctorRespond(appointment.id, "reject", note) },
                        enabled = !vm.loading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Reject") }
                }
            } else if (showActions && appointment.status == "Accepted") {
                Spacer(Modifier.height(10.dp))
                if (showNote) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Completion note") },
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        if (!showNote) showNote = true
                        else vm.doctorRespond(appointment.id, "complete", note)
                    },
                    enabled = !vm.loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (showNote) "Mark as completed" else "Complete appointment") }
            }

            if (!appointment.doctor_note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Your note: ${appointment.doctor_note}", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

// ---------- Scaffold ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(vm: HealthcareViewModel, screen: Screen, onScreenChange: (Screen) -> Unit) {
    val navItems = listOf(Screen.HOME, Screen.PREDICT, Screen.HISTORY, Screen.APPOINTMENTS, Screen.CHAT)

    Scaffold(
        containerColor = BgSoft,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SmartHealthcare", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDark)
                        Text(screen.label, fontSize = 12.sp, color = TextMuted)
                    }
                },
                navigationIcon = {
                    Box(
                        Modifier.padding(start = 14.dp).size(38.dp).clip(RoundedCornerShape(12.dp))
                            .background(BrandTeal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.HealthAndSafety, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.logout() }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout", tint = DangerRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { onScreenChange(item) },
                        icon = {
                            Icon(
                                when (item) {
                                    Screen.HOME -> Icons.Default.Home
                                    Screen.PREDICT -> Icons.Default.MedicalServices
                                    Screen.HISTORY -> Icons.Default.History
                                    Screen.APPOINTMENTS -> Icons.Default.CalendarMonth
                                    Screen.CHAT -> Icons.Default.Chat
                                }, item.label
                            )
                        },
                        label = { Text(item.label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandTeal,
                            selectedTextColor = BrandTeal,
                            indicatorColor = TealContainer,
                            unselectedIconColor = TextFaint,
                            unselectedTextColor = TextFaint
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (screen) {
            Screen.HOME -> HomeScreen(vm, padding, onScreenChange)
            Screen.PREDICT -> PredictScreen(vm, padding)
            Screen.HISTORY -> HistoryScreen(vm, padding)
            Screen.APPOINTMENTS -> AppointmentScreen(vm, padding)
            Screen.CHAT -> ChatScreen(vm, padding)
        }
    }
}

// ---------- Home ----------
@Composable
fun HomeScreen(vm: HealthcareViewModel, padding: PaddingValues, onScreenChange: (Screen) -> Unit) {
    LaunchedEffect(Unit) { vm.refreshHome() }
    val u = vm.user
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(BrandTealDark, BrandTeal, BrandTealLight)
                            )
                        )
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Good to see you,", color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                            Text(u?.name?.split(" ")?.firstOrNull() ?: "there", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = .2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Manage your health, predictions and appointments in one place.",
                        color = Color.White.copy(alpha = .9f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { onScreenChange(Screen.PREDICT) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandTeal),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.MedicalServices, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Check symptoms", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Predictions", "${vm.stats?.total_predictions ?: 0}", Icons.Default.TrendingUp, BrandTeal, Modifier.weight(1f))
                StatCard("Appointments", "${vm.stats?.total_appointments ?: 0}", Icons.Default.EventAvailable, BrandIndigo, Modifier.weight(1f))
            }
        }
        item {
            Text("Quick actions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                QuickCard("AI Assistant", "Chat about symptoms", Icons.Default.SmartToy, BrandIndigo, Modifier.weight(1f)) { onScreenChange(Screen.CHAT) }
                QuickCard("Book doctor", "Schedule a visit", Icons.Default.CalendarMonth, BrandTeal, Modifier.weight(1f)) { onScreenChange(Screen.APPOINTMENTS) }
            }
        }
        if (vm.notifications.isNotEmpty()) {
            item { Text("Notifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark) }
            items(vm.notifications.take(3)) { note ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(TealContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationsActive, null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(note.message, Modifier.weight(1f), fontSize = 13.sp, color = TextDark)
                        TextButton(onClick = { vm.markNotificationRead(note.id) }) { Text("Read", color = BrandTeal) }
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Warning, null, tint = WarningAmber)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Health predictions are for educational support only and should not replace professional medical advice.",
                        fontSize = 12.sp,
                        color = Color(0xFF7C5A0F)
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(x0: String, x1: String, x2: ImageVector, x3: Color, x4: Modifier) {
    TODO("Not yet implemented")
}

@Composable
fun QuickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            Text(subtitle, fontSize = 11.sp, color = TextMuted)
        }
    }
}

// ---------- Predict ----------
@Composable
fun PredictScreen(vm: HealthcareViewModel, padding: PaddingValues) {
    LaunchedEffect(Unit) { vm.loadSymptoms() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("ML Disease Prediction", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            Text("Select the symptoms you currently have.", color = TextMuted, fontSize = 13.sp)
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(430.dp).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vm.symptoms) { symptom ->
                        val selected = symptom in vm.selectedSymptoms
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) TealContainer else Color(0xFFF8FAFC))
                                .border(
                                    width = if (selected) 1.5.dp else 1.dp,
                                    color = if (selected) BrandTeal else BorderSoft,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { vm.toggleSymptom(symptom) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (selected) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                                null,
                                tint = if (selected) BrandTeal else TextFaint,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                symptom.replace("_", " ").replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) BrandTealDark else Color(0xFF334155)
                            )
                        }
                    }
                }
            }
        }
        item {
            Text("${vm.selectedSymptoms.size} symptoms selected", fontWeight = FontWeight.Medium, color = Color(0xFF334155))
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { vm.predict() },
                enabled = !vm.loading && vm.selectedSymptoms.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (vm.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Predict disease", fontWeight = FontWeight.SemiBold)
                }
            }
        }
        vm.prediction?.let { result ->
            item { PredictionCard(result) }
        }
    }
}

private fun severityColor(severity: String): Color = when (severity.lowercase()) {
    "high", "severe" -> DangerRed
    "medium", "moderate" -> WarningAmber
    "low", "mild" -> SuccessGreen
    else -> BrandTeal
}

@Composable
fun PredictionCard(result: PredictionResult) {
    val sevColor = severityColor(result.severity)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(sevColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MedicalServices, null, tint = sevColor)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Prediction result", fontSize = 12.sp, color = TextMuted)
                    Text(result.disease, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text("${result.confidence}% confidence") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = TealContainer, labelColor = BrandTealDark)
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(result.severity) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = sevColor.copy(alpha = 0.12f), labelColor = sevColor)
                )
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { (result.confidence / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = BrandTeal,
                trackColor = BorderSoft
            )
            Spacer(Modifier.height(16.dp))
            InfoRow(Icons.Default.Person, "Suggested specialist", result.specialist)
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Default.CheckCircle, "General precautions", result.precautions)
            Spacer(Modifier.height(16.dp))
            Text("Top predictions", fontWeight = FontWeight.Bold, color = TextDark)
            Spacer(Modifier.height(8.dp))
            result.top3.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(p.disease, color = Color(0xFF334155), fontSize = 13.sp)
                    Text("${p.confidence}%", fontWeight = FontWeight.SemiBold, color = BrandTeal, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Divider(color = BorderSoft)
            Spacer(Modifier.height(10.dp))
            Text(result.disclaimer, fontSize = 11.sp, color = TextFaint)
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = BrandTeal, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
        }
    }
}

// ---------- History ----------
@Composable
fun HistoryScreen(vm: HealthcareViewModel, padding: PaddingValues) {
    LaunchedEffect(Unit) { vm.loadHistory() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Prediction History", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            Text("Your previous ML prediction results.", color = TextMuted, fontSize = 13.sp)
        }
        if (vm.history.isEmpty()) {
            item { EmptyCard("No predictions yet. Try the symptom checker.", Icons.Default.History) }
        } else {
            items(vm.history) { p ->
                val sevColor = severityColor(p.severity)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        Box(Modifier.width(5.dp).fillMaxHeight().background(sevColor))
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(p.disease, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextDark)
                                AssistChip(
                                    onClick = {},
                                    label = { Text("${p.confidence}%") },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = TealContainer, labelColor = BrandTealDark)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${p.severity} \u2022 ${p.specialist}", fontSize = 13.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(p.symptoms.joinToString(", "), color = TextFaint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyCard(text: String, icon: ImageVector = Icons.Default.Info) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = BorderSoft, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(10.dp))
            Text(text, color = TextMuted, textAlign = TextAlign.Center)
        }
    }
}

// ---------- Appointments ----------
private fun statusColor(status: String): Color = when (status.lowercase()) {
    "accepted", "confirmed" -> SuccessGreen
    "pending" -> WarningAmber
    "cancelled", "rejected" -> DangerRed
    "completed" -> InfoBlue
    else -> TextMuted
}

@Composable
fun AppointmentScreen(vm: HealthcareViewModel, padding: PaddingValues) {
    LaunchedEffect(Unit) { vm.loadAppointments() }
    var selectedDoctor by remember { mutableStateOf<DoctorDto?>(null) }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Book an Appointment", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            Text("Choose a doctor and send a request.", color = TextMuted, fontSize = 13.sp)
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    DoctorSelector(vm.doctors, selectedDoctor) { selectedDoctor = it }
                    Field("Date", date, { date = it }, "DD-MM-YYYY", icon = Icons.Default.CalendarMonth)
                    Field("Time", time, { time = it }, "e.g. 10:30 AM", icon = Icons.Default.AccessTime)
                    Field("Reason", reason, { reason = it }, "Why do you need an appointment?", icon = Icons.Default.Info)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            selectedDoctor?.let {
                                vm.bookAppointment(AppointmentRequest(it.id, date, time, reason))
                            }
                        },
                        enabled = !vm.loading && selectedDoctor != null && date.isNotBlank() && time.isNotBlank() && reason.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandTeal),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (vm.loading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Send appointment request", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(4.dp))
            Text("My appointments", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
        }
        if (vm.appointments.isEmpty()) item { EmptyCard("No appointments found.", Icons.Default.CalendarMonth) }
        else items(vm.appointments) { a ->
            val sColor = statusColor(a.status)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(a.doctor_name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                        AssistChip(
                            onClick = {},
                            label = { Text(a.status) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = sColor.copy(alpha = 0.12f), labelColor = sColor)
                        )
                    }
                    Text(a.department ?: "", color = TextMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = BrandTeal, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${a.date} \u2022 ${a.time}", fontSize = 13.sp, color = Color(0xFF334155))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(a.reason, color = TextMuted, fontSize = 13.sp)
                    if (a.status == "Pending" || a.status == "Accepted") {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { vm.cancelAppointment(a.id) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)
                        ) { Text("Cancel") }
                    }
                    if (!a.doctor_note.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Doctor note: ${a.doctor_note}", fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
        }
    }
}

@Composable
fun DoctorSelector(
    doctors: List<DoctorDto>,
    selected: DoctorDto?,
    onSelect: (DoctorDto) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text("Doctor", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth().clickable { open = !open },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            border = BorderStroke(1.dp, BorderSoft)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(TealContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = BrandTeal)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(selected?.name ?: "Select a doctor", fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(selected?.department ?: "Choose from available doctors", fontSize = 12.sp, color = TextMuted)
                }
                Icon(
                    if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    null,
                    tint = TextFaint
                )
            }
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderSoft)
            ) {
                Column {
                    doctors.forEach { doctor ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(doctor); open = false }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MedicalServices, null, tint = BrandTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(doctor.name, fontWeight = FontWeight.SemiBold, color = TextDark)
                                Text(doctor.department ?: "", fontSize = 12.sp, color = TextMuted)
                            }
                        }
                        Divider(color = Color(0xFFF1F5F9))
                    }
                }
            }
        }
    }
}

// ---------- Chat ----------
data class ChatMessage(val fromUser: Boolean, val text: String)

@Composable
fun ChatScreen(vm: HealthcareViewModel, padding: PaddingValues) {
    var input by remember { mutableStateOf("") }
    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(false, "Hi! I'm your SmartHealthcare assistant. Tell me your main symptom and how long you've had it.")
            )
        )
    }

    Column(Modifier.fillMaxSize().padding(padding).background(BgSoft)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { m ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start
                ) {
                    if (!m.fromUser) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(BrandTeal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SmartToy, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Card(
                        modifier = Modifier.widthIn(max = 260.dp),
                        shape = RoundedCornerShape(
                            topStart = 18.dp, topEnd = 18.dp,
                            bottomStart = if (m.fromUser) 18.dp else 4.dp,
                            bottomEnd = if (m.fromUser) 4.dp else 18.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (m.fromUser) BrandTeal else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Text(
                            m.text,
                            Modifier.padding(14.dp),
                            color = if (m.fromUser) Color.White else TextDark,
                            fontSize = 14.sp
                        )
                    }
                    if (m.fromUser) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(BrandIndigo),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Divider(color = BorderSoft)
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask a health question...") },
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandTeal, cursorColor = BrandTeal),
                maxLines = 3
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        messages = messages + ChatMessage(true, text)
                        input = ""
                        vm.sendChat(text) { reply ->
                            messages = messages + ChatMessage(false, reply)
                        }
                    }
                },
                modifier = Modifier.size(46.dp).clip(CircleShape).background(BrandTeal)
            ) { Icon(Icons.Default.Send, "Send", tint = Color.White) }
        }
    }
}
