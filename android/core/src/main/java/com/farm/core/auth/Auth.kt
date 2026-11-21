package com.farm.core.auth

import android.content.Context
import android.content.SharedPreferences
import com.farm.core.api.ApiConfig
import com.farm.core.api.ApiException
import com.farm.core.api.Http
import com.farm.core.api.HttpResult
import com.farm.core.api.OtpRequestResponse
import com.farm.core.api.TokenResponse
import com.farm.core.api.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

/** Session kept in app-private storage (replaces expo-secure-store). */
object AuthStorage {
    private const val PREFS = "farm_auth"
    private const val ACCESS = "farm_access_token"
    private const val REFRESH = "farm_refresh_token"
    private const val USER = "farm_user_data"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var accessToken: String?
        get() = prefs?.getString(ACCESS, null)
        set(v) { prefs?.edit()?.putString(ACCESS, v)?.apply() }

    var refreshToken: String?
        get() = prefs?.getString(REFRESH, null)
        set(v) { prefs?.edit()?.putString(REFRESH, v)?.apply() }

    var user: User?
        get() = prefs?.getString(USER, null)?.let { runCatching { Http.json.decodeFromString<User>(it) }.getOrNull() }
        set(v) { prefs?.edit()?.putString(USER, v?.let { Http.encode(it) })?.apply() }

    fun save(data: TokenResponse) {
        accessToken = data.accessToken
        refreshToken = data.refreshToken
        user = data.user
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}

/** Indian mobile numbers: "98765 43210" or "098…" -> "+919876543210". */
fun normalizePhone(value: String): String {
    val digits = value.replace(Regex("[\\s\\-()]"), "")
    return if (Regex("^0?\\d{10}$").matches(digits)) "+91" + digits.takeLast(10) else digits
}

/** The identity service calls (POST /identity/...). */
object AuthService {
    @Serializable private data class PhoneIn(val phone: String)
    @Serializable private data class VerifyIn(val phone: String, val code: String)
    @Serializable private data class LoginIn(val phone: String, val password: String)
    @Serializable private data class RefreshIn(val refreshToken: String?)
    @Serializable
    private data class RegisterIn(
        val name: String, val phone: String, val password: String, val roles: List<String>, val email: String? = null,
    )

    private inline fun <reified T> handle(res: HttpResult): T {
        if (!res.ok) {
            val error = res.error()
            val message = error?.message ?: error?.detail ?: "Request failed with status ${res.status}"
            throw ApiException(message, error?.code ?: "REQUEST_FAILED", res.status)
        }
        return res.decode()
    }

    private fun store(data: TokenResponse): TokenResponse {
        AuthStorage.save(data)
        Http.authToken = data.accessToken
        return data
    }

    suspend fun requestOtp(phone: String): OtpRequestResponse =
        handle(Http.call("POST", "/identity/otp/request", Http.encode(PhoneIn(normalizePhone(phone))), token = null))

    suspend fun verifyOtp(phone: String, code: String): TokenResponse =
        store(handle(Http.call("POST", "/identity/otp/verify", Http.encode(VerifyIn(normalizePhone(phone), code)), token = null)))

    suspend fun loginWithPassword(phone: String, password: String): TokenResponse =
        store(handle(Http.call("POST", "/identity/login", Http.encode(LoginIn(normalizePhone(phone), password)), token = null)))

    suspend fun register(name: String, phone: String, password: String, roles: List<String>, email: String?): User =
        handle(
            Http.call(
                "POST", "/identity/register",
                Http.encode(RegisterIn(name, normalizePhone(phone), password, roles, email)), token = null,
            ),
        )

    suspend fun getMe(): User {
        val token = AuthStorage.accessToken ?: throw ApiException("No access token found", "UNAUTHORIZED", 401)
        return handle(Http.call("GET", "/identity/me", token = token))
    }

    suspend fun refresh(): TokenResponse {
        val refresh = AuthStorage.refreshToken ?: throw ApiException("No refresh token found", "UNAUTHORIZED", 401)
        return store(handle(Http.call("POST", "/identity/refresh", Http.encode(RefreshIn(refresh)), token = null)))
    }

    suspend fun logout() {
        val token = AuthStorage.accessToken
        val refresh = AuthStorage.refreshToken
        try {
            if (token != null || refresh != null) {
                Http.call("POST", "/identity/logout", Http.encode(RefreshIn(refresh)), token = token)
            }
        } catch (_: Exception) {
            // Continue to the local wipe even if the server logout fails
        }
        AuthStorage.clear()
        Http.authToken = null
    }
}

data class AuthState(
    val user: User? = null,
    val accessToken: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val isAuthenticated: Boolean get() = user != null && accessToken != null
    fun hasRole(role: String): Boolean = user?.roles?.contains(role) == true
}

/** App-wide auth state (the AuthProvider / useAuth of the React app). */
object AuthManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private var started = false

    /** Restore the stored session, then verify it against /me (refreshing the token if needed). */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                val token = AuthStorage.accessToken
                val user = AuthStorage.user
                if (token != null && user != null) {
                    Http.authToken = token
                    _state.update { it.copy(user = user, accessToken = token) }
                    if (!token.startsWith("mock-token-")) verifyStoredSession()
                }
            } finally {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun verifyStoredSession() {
        try {
            val fresh = AuthService.getMe()
            AuthStorage.user = fresh
            _state.update { it.copy(user = fresh) }
        } catch (e: ApiException) {
            try {
                val refreshed = AuthService.refresh()
                _state.update { it.copy(user = refreshed.user, accessToken = refreshed.accessToken) }
            } catch (_: Exception) {
                AuthStorage.clear()
                Http.authToken = null
                _state.update { it.copy(user = null, accessToken = null) }
            }
        } catch (_: Exception) {
            // Offline: keep the stored session, it is checked again on the next start.
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun setError(msg: String) = _state.update { it.copy(error = msg) }

    private suspend fun checkRole(user: User, requiredRole: String?) {
        if (requiredRole == null || user.roles.contains(requiredRole)) return
        AuthService.logout()
        _state.update { it.copy(user = null, accessToken = null) }
        val roles = user.roles.joinToString(", ").ifEmpty { "none" }
        throw ApiException(
            "Access denied: Your account role is [$roles], but this app requires [$requiredRole].",
            "FORBIDDEN_ROLE", 403,
        )
    }

    private fun friendly(e: Exception, fallback: String): String = when (e) {
        is ApiException -> e.message ?: fallback
        else -> fallback
    }

    suspend fun requestOtp(phone: String): OtpRequestResponse {
        clearError()
        try {
            return AuthService.requestOtp(phone)
        } catch (e: Exception) {
            setError(friendly(e, "Failed to send OTP. Please check your network (${ApiConfig.baseUrl})."))
            throw e
        }
    }

    suspend fun verifyOtp(phone: String, code: String, requiredRole: String?): User {
        clearError()
        try {
            val data = AuthService.verifyOtp(phone, code)
            checkRole(data.user, requiredRole)
            _state.update { it.copy(user = data.user, accessToken = data.accessToken) }
            return data.user
        } catch (e: Exception) {
            setError(friendly(e, "Invalid OTP code. Please try again."))
            throw e
        }
    }

    suspend fun loginWithPassword(phone: String, password: String, requiredRole: String?): User {
        clearError()
        try {
            val data = AuthService.loginWithPassword(phone, password)
            checkRole(data.user, requiredRole)
            _state.update { it.copy(user = data.user, accessToken = data.accessToken) }
            return data.user
        } catch (e: Exception) {
            setError(friendly(e, "Login failed. Check your network (${ApiConfig.baseUrl})."))
            throw e
        }
    }

    suspend fun register(name: String, phone: String, password: String, email: String?, requiredRole: String): User {
        clearError()
        try {
            AuthService.register(name, phone, password, listOf(requiredRole), email)
        } catch (e: Exception) {
            setError(friendly(e, "Sign up failed. Please check your network."))
            throw e
        }
        return loginWithPassword(phone, password, requiredRole)
    }

    fun logout() {
        scope.launch {
            try {
                AuthService.logout()
            } finally {
                _state.update { it.copy(user = null, accessToken = null, error = null) }
            }
        }
    }

    /** Dev helper: a local session for the given role, for demos without the backend. */
    fun devMockLogin(role: String): User {
        val user = User(
            id = "dev-user-$role-001",
            name = if (role == "delivery") "Dev Rider Suresh" else "Dev Customer Anita",
            phone = if (role == "delivery") "+919876543211" else "+919876543210",
            email = "$role@farm.local",
            roles = listOf(role),
            phoneVerified = true,
            createdAt = Instant.now().toString(),
        )
        val token = "mock-token-$role-${System.currentTimeMillis()}"
        AuthStorage.accessToken = token
        AuthStorage.refreshToken = "mock-refresh-$token"
        AuthStorage.user = user
        Http.authToken = token
        _state.update { it.copy(user = user, accessToken = token, error = null) }
        return user
    }
}

/** Call once from the app's onCreate. */
object FarmCore {
    fun init(context: Context) {
        ApiConfig.init(context)
        AuthStorage.init(context)
        AuthManager.start()
    }
}
