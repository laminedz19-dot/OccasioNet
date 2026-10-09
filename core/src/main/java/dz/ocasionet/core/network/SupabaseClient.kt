package dz.ocasionet.core.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dz.ocasionet.core.BuildConfig
import dz.ocasionet.core.model.SupabaseConfigStatus
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

internal fun authorizationHeaderForApiKey(apiKey: String, accessToken: String?): String? {
    accessToken?.takeIf { it.isNotBlank() }?.let { return "Bearer $it" }
    return if (apiKey.startsWith("sb_publishable_")) null else "Bearer $apiKey"
}

/**
 * تهيئة عميل Supabase المشترك في وحدة `:core` باستخدام متغيرات البيئة:
 * - `SUPABASE_URL` أو `URL`
 * - `SUPABASE_ANON_KEY` أو `ANON_KEY`
 *
 * يتيح استخدام عميل موحّد (Singleton) في جميع وحدات التطبيق (`:core`, `:userApp`, `:adminApp`, `:app`).
 */
object SupabaseClient {

    @Volatile
    var currentAccessToken: String? = null

    @Volatile
    private var cachedService: SupabaseRestService? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    @Volatile
    private var cachedAnonKey: String? = null

    /**
     * قراءة رابط مشروع Supabase من متغيرات البيئة (`SUPABASE_URL` أو `URL`).
     */
    val supabaseUrl: String
        get() {
            val primary = BuildConfig.SUPABASE_URL.trim()
            return if (isPlaceholder(primary)) {
                BuildConfig.URL.trim()
            } else {
                primary
            }
        }

    /**
     * قراءة المفتاح العام `ANON_KEY` من متغيرات البيئة (`SUPABASE_ANON_KEY` أو `ANON_KEY`).
     */
    val supabaseAnonKey: String
        get() {
            val primary = BuildConfig.SUPABASE_ANON_KEY.trim()
            return if (isPlaceholder(primary)) {
                BuildConfig.ANON_KEY.trim()
            } else {
                primary
            }
        }

    /**
     * التحقق مما إذا كانت إعدادات الاتصال مهيأة وصحيحة.
     */
    val isConfigured: Boolean
        get() = inspectConfig() is SupabaseConfigStatus.Configured

    /**
     * الحصول على واجهة خدمات Supabase المهيأة للاستخدام في جميع أنحاء التطبيق.
     */
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

    /**
     * فحص حالة متغيرات البيئة والتأكد من عدم استخدام مفتاح `service_role` السري داخل تطبيق Android.
     */
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

    /**
     * تهيئة أو استرجاع مثيل `SupabaseRestService` المشترك.
     */
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
            if (authorization == null) reqBuilder.removeHeader("Authorization")
            else reqBuilder.header("Authorization", authorization)
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

/**
 * مزود متوافق مع كافة المستودعات (`OccasioNetRepository` و `AdminRepository`) ويفوّض التنفيذ إلى `SupabaseClient`.
 */
object SupabaseClientProvider {

    var currentAccessToken: String?
        get() = SupabaseClient.currentAccessToken
        set(value) {
            SupabaseClient.currentAccessToken = value
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
