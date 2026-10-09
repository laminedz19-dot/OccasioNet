package dz.ocasionet.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dz.ocasionet.core.BuildConfig
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.ui.theme.AppColorPalette
import dz.ocasionet.core.ui.theme.OccasioNetPaletteStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.net.URLDecoder
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal fun authorizationHeaderForApiKey(apiKey: String, accessToken: String?): String? {
    accessToken?.takeIf { it.isNotBlank() }?.let { return "Bearer $it" }
    return if (apiKey.startsWith("sb_publishable_")) null else "Bearer $apiKey"
}

@JsonClass(generateAdapter = true)
data class PersistedAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String = "",
    val expiresAtEpochSeconds: Long
)

sealed class AuthCallbackResult {
    data class EmailConfirmed(val session: PersistedAuthSession) : AuthCallbackResult()
    data class PasswordRecovery(val session: PersistedAuthSession) : AuthCallbackResult()
    data class Error(val messageAr: String) : AuthCallbackResult()
    data object Ignored : AuthCallbackResult()
}

/**
 * واجهة التخزين الآمن لرموز الجلسة (تمنع استخدام SharedPreferences العادي غير المشفر).
 */
interface SecureSessionStore {
    fun save(session: PersistedAuthSession)
    fun load(): PersistedAuthSession?
    fun clear()
}

/**
 * تخزين مشفر في الذاكرة لبيئة الاختبارات مع تشفير AES/GCM حقيقي لبيانات الجلسة.
 */
class InMemoryEncryptedSessionStore : SecureSessionStore {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Volatile
    private var encryptedPayload: ByteArray? = null

    @Volatile
    private var ivBytes: ByteArray? = null

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(PersistedAuthSession::class.java)

    @Synchronized
    override fun save(session: PersistedAuthSession) {
        val json = adapter.toJson(session).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        ivBytes = cipher.iv
        encryptedPayload = cipher.doFinal(json)
    }

    @Synchronized
    override fun load(): PersistedAuthSession? {
        val payload = encryptedPayload ?: return null
        val iv = ivBytes ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(payload).toString(Charsets.UTF_8)
            adapter.fromJson(plain)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    override fun clear() {
        encryptedPayload = null
        ivBytes = null
    }
}

/**
 * تخزين الجلسة المشفر عبر Android Keystore (AES/GCM/NoPadding) داخل ملف خاص بالتطبيق.
 * لا يستخدم SharedPreferences عادي مطلقاً ولا يطبع أي رمز في Logcat.
 */
class AndroidKeystoreSessionStore(context: Context) : SecureSessionStore {
    private val appContext = context.applicationContext
    private val sessionFile = File(appContext.noBackupFilesDir, "occasionet_session.enc")
    private val keyAlias = "OccasioNetSessionMasterKey_v1"
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(PersistedAuthSession::class.java)

    private fun getOrCreateSecretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    override fun save(session: PersistedAuthSession) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val cipherBytes = cipher.doFinal(adapter.toJson(session).toByteArray(Charsets.UTF_8))
            val cipherBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
            sessionFile.writeText("$ivBase64:$cipherBase64", Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    @Synchronized
    override fun load(): PersistedAuthSession? {
        if (!sessionFile.exists()) return null
        return try {
            val content = sessionFile.readText(Charsets.UTF_8)
            val parts = content.split(":", limit = 2)
            if (parts.size != 2) {
                clear()
                return null
            }
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            val json = cipher.doFinal(cipherBytes).toString(Charsets.UTF_8)
            adapter.fromJson(json)
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    override fun clear() {
        runCatching {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
        }
    }
}

/**
 * تهيئة عميل Supabase المشترك في وحدة `:core` باستخدام متغيرات البيئة أو الإعداد المحلي في DataStore:
 * - `SUPABASE_URL` أو `URL`
 * - `SUPABASE_ANON_KEY` أو `ANON_KEY` (يدعم مفاتيح `sb_publishable_` دون إرسالها كـ Bearer JWT)
 *
 * يدير الجلسة المشفرة وتجديد `refresh_token` التلقائي ومعالجة روابط Deep Links.
 */
object SupabaseClient {

    const val DEEP_LINK_CALLBACK_URI = "occasionet://auth-callback"
    const val DEFAULT_VERIFIED_PROJECT_URL = "https://oxdsyhvsntmvzeouqvrr.supabase.co"

    @Volatile
    private var sessionStore: SecureSessionStore = InMemoryEncryptedSessionStore()

    @Volatile
    var dataStoreRepository: dz.ocasionet.core.repository.DataStoreRepository? = null
        private set

    private val refreshMutex = Mutex()

    @Volatile
    var currentAccessToken: String? = null

    @Volatile
    var currentRefreshToken: String? = null

    @Volatile
    var currentTokenExpiresAtEpochSeconds: Long = 0L

    @Volatile
    private var runtimeSupabaseUrlOverride: String? = null

    @Volatile
    private var runtimeSupabaseAnonKeyOverride: String? = null

    @Volatile
    private var cachedService: SupabaseRestService? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedAnonKey: String? = null

    /**
     * ربط التخزين المشفر ومستودع DataStore بـ Android Keystore عند انطلاق التطبيق.
     */
    fun initializeSecureStore(context: Context) {
        val repo = dz.ocasionet.core.repository.DataStoreRepository.getInstance(context)
        dataStoreRepository = repo
        sessionStore = repo
        runCatching {
            val prefs = runBlocking { repo.getUserPreferences() }
            if (!prefs.customSupabaseUrl.isNullOrBlank()) {
                runtimeSupabaseUrlOverride = prefs.customSupabaseUrl
            }
            if (!prefs.customSupabaseAnonKey.isNullOrBlank()) {
                runtimeSupabaseAnonKeyOverride = prefs.customSupabaseAnonKey
            }
            if (prefs.darkModeEnabled) {
                OccasioNetPaletteStore.selectPalette(AppColorPalette.OCCASIONET_LOGO_DARK)
            }
        }
        restorePersistedSession()
    }

    /**
     * تحديث رابط ومفتاح Supabase العام أثناء التشغيل وحفظهما محلياً في DataStore.
     */
    suspend fun updateRuntimeSupabaseConfig(url: String?, anonKey: String?): SupabaseConfigStatus {
        val cleanUrl = url?.trim()?.takeIf { it.isNotBlank() }
        val cleanKey = anonKey?.trim()?.takeIf { it.isNotBlank() }
        runtimeSupabaseUrlOverride = cleanUrl
        runtimeSupabaseAnonKeyOverride = cleanKey
        cachedService = null
        dataStoreRepository?.setCustomSupabaseConfig(cleanUrl, cleanKey)
        return inspectConfig()
    }

    /**
     * تبديل مخزن الجلسة (للاختبارات الآلية).
     */
    fun setSessionStoreForTesting(store: SecureSessionStore) {
        sessionStore = store
        runtimeSupabaseUrlOverride = null
        runtimeSupabaseAnonKeyOverride = null
        if (store is dz.ocasionet.core.repository.DataStoreRepository) {
            dataStoreRepository = store
        }
        restorePersistedSession()
    }

    /**
     * حفظ الجلسة بشكل آمن ومشفر بعد تسجيل الدخول أو التجديد.
     */
    @Synchronized
    fun persistSession(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String = "",
        expiresInSeconds: Long = 3600L,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L
    ): PersistedAuthSession {
        val expiresAt = nowEpochSeconds + expiresInSeconds
        val persisted = PersistedAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = email,
            expiresAtEpochSeconds = expiresAt
        )
        currentAccessToken = accessToken
        currentRefreshToken = refreshToken
        currentTokenExpiresAtEpochSeconds = expiresAt
        sessionStore.save(persisted)
        return persisted
    }

    /**
     * استعادة الجلسة المحفوظة بعد إغلاق التطبيق أو إعادة تشغيل الهاتف.
     */
    @Synchronized
    fun restorePersistedSession(): PersistedAuthSession? {
        val loaded = sessionStore.load()
        if (loaded != null) {
            currentAccessToken = loaded.accessToken
            currentRefreshToken = loaded.refreshToken
            currentTokenExpiresAtEpochSeconds = loaded.expiresAtEpochSeconds
        }
        return loaded
    }

    /**
     * التحقق من انتهاء صلاحية رمز الوصول (مع هامش أمان 30 ثانية).
     */
    fun isAccessTokenExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean {
        val exp = currentTokenExpiresAtEpochSeconds
        if (exp <= 0L) return false
        return (nowEpochSeconds + 30L) >= exp
    }

    /**
     * إبطال الجلسة محلياً وحذف الرموز المشفرة عند تسجيل الخروج أو فشل التجديد.
     */
    @Synchronized
    fun clearSession() {
        currentAccessToken = null
        currentRefreshToken = null
        currentTokenExpiresAtEpochSeconds = 0L
        sessionStore.clear()
    }

    /**
     * تجديد الجلسة بأمان مع قفل تزامن (Mutex) لمنع تكرار طلبات refresh_token في اللحظة نفسها.
     */
    suspend fun refreshSessionIfNeeded(
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
        customRefresher: (suspend (String) -> SupabaseSessionDto?)? = null
    ): Result<PersistedAuthSession> = refreshMutex.withLock {
        val loaded = sessionStore.load()
        val refreshTok = currentRefreshToken ?: loaded?.refreshToken
        if (refreshTok.isNullOrBlank()) {
            clearSession()
            return@withLock Result.failure(IllegalStateException("لا يوجد رمز تجديد صالح؛ يرجى تسجيل الدخول مجدداً."))
        }

        // إذا تم تجديد الرمز بالفعل بواسطة طلب متزامن سبقنا، نكتفي بالرمز الجديد
        if (!isAccessTokenExpired(nowEpochSeconds) && !currentAccessToken.isNullOrBlank()) {
            val active = sessionStore.load()
            if (active != null) return@withLock Result.success(active)
        }

        return@withLock try {
            val dto = if (customRefresher != null) {
                customRefresher(refreshTok)
            } else {
                val svc = getOrCreateService()
                val resp = svc?.refreshSession(RefreshTokenRequest(refreshTok))
                if (resp?.isSuccessful == true) resp.body() else null
            }

            val newAccess = dto?.accessToken
            val newRefresh = dto?.refreshToken ?: refreshTok
            val uid = dto?.user?.id ?: loaded?.userId.orEmpty()
            val email = dto?.user?.email ?: loaded?.email.orEmpty()

            if (newAccess.isNullOrBlank() || uid.isBlank()) {
                clearSession()
                Result.failure(IllegalStateException("انتهت صلاحية الجلسة وتعذر تجديدها؛ يرجى تسجيل الدخول مرة أخرى."))
            } else {
                val updated = persistSession(
                    accessToken = newAccess,
                    refreshToken = newRefresh,
                    userId = uid,
                    email = email,
                    expiresInSeconds = dto.expiresIn ?: 3600L,
                    nowEpochSeconds = nowEpochSeconds
                )
                Result.success(updated)
            }
        } catch (e: Exception) {
            clearSession()
            Result.failure(IllegalStateException("تعذر تجديد الجلسة المنتهية؛ تم تسجيل الخروج لحماية الحساب."))
        }
    }

    /**
     * تحليل رابط العودة (Deep Link) الخاص بتأكيد البريد الإلكتروني أو استعادة كلمة المرور:
     * `occasionet://auth-callback#access_token=...&refresh_token=...&expires_in=3600&type=signup|recovery`
     */
    fun handleAuthCallbackUri(rawUri: String): AuthCallbackResult {
        if (!rawUri.startsWith("occasionet://auth-callback", ignoreCase = true)) {
            return AuthCallbackResult.Ignored
        }
        val params = mutableMapOf<String, String>()
        val fragment = rawUri.substringAfter("#", "")
        val query = rawUri.substringAfter("?", "").substringBefore("#")
        listOf(fragment, query).filter { it.isNotBlank() }.forEach { part ->
            part.split("&").forEach { kv ->
                val idx = kv.indexOf('=')
                if (idx > 0) {
                    val k = URLDecoder.decode(kv.substring(0, idx), "UTF-8")
                    val v = URLDecoder.decode(kv.substring(idx + 1), "UTF-8")
                    params[k] = v
                }
            }
        }

        val errorDesc = params["error_description"] ?: params["error"]
        if (!errorDesc.isNullOrBlank()) {
            return AuthCallbackResult.Error("تعذر إتمام عملية التحقق من الرابط: $errorDesc")
        }

        val accessToken = params["access_token"]
        val refreshToken = params["refresh_token"].orEmpty()
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600L
        val type = params["type"].orEmpty().lowercase()
        val userId = params["user_id"].orEmpty().ifBlank { "callback-user" }

        if (accessToken.isNullOrBlank()) {
            return AuthCallbackResult.Error("الرابط لا يحتوي على رمز جلسة صالح أو انتهت صلاحيته.")
        }

        val session = persistSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            expiresInSeconds = expiresIn
        )
        return when (type) {
            "recovery" -> AuthCallbackResult.PasswordRecovery(session)
            else -> AuthCallbackResult.EmailConfirmed(session)
        }
    }

    /**
     * بناء الرابط العام لصورة إعلان داخل حاوية `listing-images`.
     */
    fun publicListingImageUrl(storagePath: String): String {
        val status = inspectConfig()
        val cleanPath = storagePath.trimStart('/')
        return if (status is SupabaseConfigStatus.Configured) {
            "${status.url}storage/v1/object/public/listing-images/$cleanPath"
        } else {
            "storage/v1/object/public/listing-images/$cleanPath"
        }
    }

    /**
     * قراءة رابط مشروع Supabase من الإعداد المحلي أو متغيرات البيئة (`SUPABASE_URL` أو `URL`).
     */
    val supabaseUrl: String
        get() {
            val runtime = runtimeSupabaseUrlOverride?.trim().orEmpty()
            if (!isPlaceholder(runtime)) return runtime
            val primary = BuildConfig.SUPABASE_URL.trim()
            return if (isPlaceholder(primary)) {
                BuildConfig.URL.trim()
            } else {
                primary
            }
        }

    /**
     * قراءة المفتاح العام `ANON_KEY` من الإعداد المحلي أو متغيرات البيئة (`SUPABASE_ANON_KEY` أو `ANON_KEY`).
     */
    val supabaseAnonKey: String
        get() {
            val runtime = runtimeSupabaseAnonKeyOverride?.trim().orEmpty()
            if (!isPlaceholder(runtime)) return runtime
            val primary = BuildConfig.SUPABASE_ANON_KEY.trim()
            return if (isPlaceholder(primary)) {
                BuildConfig.ANON_KEY.trim()
            } else {
                primary
            }
        }

    val isConfigured: Boolean
        get() = inspectConfig() is SupabaseConfigStatus.Configured

    val service: SupabaseRestService?
        get() = getOrCreateService()

    private fun isPlaceholder(value: String): Boolean {
        val clean = value.trim()
        return clean.isEmpty() ||
            clean.contains("UNCONFIGURED", ignoreCase = true) ||
            clean.contains("REPLACE_WITH", ignoreCase = true) ||
            clean.contains("CHANGEME", ignoreCase = true) ||
            clean.contains("your-project-ref", ignoreCase = true)
    }

    fun inspectConfig(
        rawUrl: String = supabaseUrl,
        rawAnonKey: String = supabaseAnonKey
    ): SupabaseConfigStatus {
        val url = rawUrl.trim().trimEnd('/')
        val key = rawAnonKey.trim()

        if (isPlaceholder(url) || isPlaceholder(key)) {
            return SupabaseConfigStatus.NotConfigured
        }

        if (key.contains("service_role", ignoreCase = true) || key.startsWith("sb_secret_")) {
            return SupabaseConfigStatus.ForbiddenServiceRoleKey(
                "خطأ أمني حرج: تم اكتشاف مفتاح service_role سري في إعدادات Android. يُسمح فقط بالمفتاح العام (anon/publishable) مع تفعيل RLS."
            )
        }

        val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
            "$url/"
        } else {
            "https://$url/"
        }
        val masked = if (key.length > 12) "${key.take(6)}...${key.takeLast(4)}" else "***"
        return SupabaseConfigStatus.Configured(url = normalizedUrl, anonKeyMasked = masked)
    }

    @Synchronized
    fun getOrCreateService(
        rawUrl: String = supabaseUrl,
        rawAnonKey: String = supabaseAnonKey
    ): SupabaseRestService? {
        val status = inspectConfig(rawUrl, rawAnonKey)
        if (status !is SupabaseConfigStatus.Configured) return null

        val anonKey = rawAnonKey.trim()
        if (cachedService != null && cachedBaseUrl == status.url && cachedAnonKey == anonKey) {
            return cachedService
        }

        val authInterceptor = Interceptor { chain ->
            val reqBuilder = chain.request().newBuilder().header("apikey", anonKey)
            val authorization = authorizationHeaderForApiKey(anonKey, currentAccessToken)
            if (authorization == null) {
                reqBuilder.removeHeader("Authorization")
            } else {
                reqBuilder.header("Authorization", authorization)
            }
            val req = reqBuilder.build()
            chain.proceed(req)
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val created = Retrofit.Builder()
            .baseUrl(status.url)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseRestService::class.java)

        cachedBaseUrl = status.url
        cachedAnonKey = anonKey
        cachedService = created
        return created
    }
}

object SupabaseClientProvider {

    var currentAccessToken: String?
        get() = SupabaseClient.currentAccessToken
        set(value) {
            if (value == null) {
                SupabaseClient.clearSession()
            } else {
                SupabaseClient.currentAccessToken = value
            }
        }

    fun inspectConfig(
        rawUrl: String = SupabaseClient.supabaseUrl,
        rawAnonKey: String = SupabaseClient.supabaseAnonKey
    ): SupabaseConfigStatus = SupabaseClient.inspectConfig(rawUrl, rawAnonKey)

    fun createServiceOrNull(
        rawUrl: String = SupabaseClient.supabaseUrl,
        rawAnonKey: String = SupabaseClient.supabaseAnonKey
    ): SupabaseRestService? = SupabaseClient.getOrCreateService(rawUrl, rawAnonKey)
}
