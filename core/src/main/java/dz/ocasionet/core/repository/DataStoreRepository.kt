package dz.ocasionet.core.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dz.ocasionet.core.network.PersistedAuthSession
import dz.ocasionet.core.network.SecureSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.occasionetPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "occasionet_user_preferences"
)

/**
 * نموذج تفضيلات المستخدم المحلية المخزنة في Jetpack DataStore.
 */
data class LocalUserPreferences(
    val darkModeEnabled: Boolean = false,
    val preferredWilayaCode: Int? = null,
    val preferredCommuneId: Int? = null,
    val preferredCategoryId: Int? = null,
    val preferredMinPriceDzd: Long? = null,
    val preferredMaxPriceDzd: Long? = null,
    val notificationsEnabled: Boolean = true,
    val acceptedTermsAndPrivacy: Boolean = false,
    val languageCode: String = "ar"
)

/**
 * واجهة تشفير رموز المصادقة قبل حفظها داخل DataStore<Preferences>.
 */
interface TokenCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String?
}

/**
 * مشفّر AES/GCM يعتمد على AndroidKeyStore على الأجهزة الحقيقية مع بديل AES-256 في بيئة اختبارات JVM.
 */
class AesGcmTokenCipher : TokenCipher {
    private val keyAlias = "OccasioNetDataStoreTokenKey_v1"

    @Volatile
    private var fallbackKey: SecretKey? = null

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
            if (existing != null) {
                existing.secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            fallbackKey ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
                fallbackKey = it
            }
        }
    }

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ivEncoded = java.util.Base64.getEncoder().encodeToString(cipher.iv)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payloadEncoded = java.util.Base64.getEncoder().encodeToString(encryptedBytes)
        return "$ivEncoded:$payloadEncoded"
    }

    override fun decrypt(cipherText: String): String? {
        val parts = cipherText.split(":", limit = 2)
        if (parts.size != 2) return null
        return try {
            val iv = try {
                java.util.Base64.getDecoder().decode(parts[0])
            } catch (_: Exception) {
                Base64.decode(parts[0], Base64.NO_WRAP)
            }
            val payload = try {
                java.util.Base64.getDecoder().decode(parts[1])
            } catch (_: Exception) {
                Base64.decode(parts[1], Base64.NO_WRAP)
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(payload).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * مستودع Jetpack DataStore في وحدة `:core` لإدارة تفضيلات المستخدم المحلية ورموز المصادقة المشفرة.
 */
class DataStoreRepository(
    private val dataStore: DataStore<Preferences>,
    private val tokenCipher: TokenCipher = AesGcmTokenCipher()
) : SecureSessionStore {

    constructor(context: Context) : this(
        dataStore = context.applicationContext.occasionetPreferencesDataStore,
        tokenCipher = AesGcmTokenCipher()
    )

    object Keys {
        // مفاتيح تفضيلات المستخدم المحلية
        val DARK_MODE_ENABLED = booleanPreferencesKey("pref_dark_mode_enabled")
        val PREFERRED_WILAYA_CODE = intPreferencesKey("pref_wilaya_code")
        val PREFERRED_COMMUNE_ID = intPreferencesKey("pref_commune_id")
        val PREFERRED_CATEGORY_ID = intPreferencesKey("pref_category_id")
        val PREFERRED_MIN_PRICE_DZD = longPreferencesKey("pref_min_price_dzd")
        val PREFERRED_MAX_PRICE_DZD = longPreferencesKey("pref_max_price_dzd")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("pref_notifications_enabled")
        val ACCEPTED_TERMS_AND_PRIVACY = booleanPreferencesKey("pref_accepted_terms_and_privacy")
        val LANGUAGE_CODE = stringPreferencesKey("pref_language_code")

        // مفاتيح رموز المصادقة والجلسة (يتم تشفير الرموز بـ AES/GCM قبل تخزينها)
        val ENCRYPTED_ACCESS_TOKEN = stringPreferencesKey("auth_encrypted_access_token")
        val ENCRYPTED_REFRESH_TOKEN = stringPreferencesKey("auth_encrypted_refresh_token")
        val AUTH_USER_ID = stringPreferencesKey("auth_user_id")
        val AUTH_USER_EMAIL = stringPreferencesKey("auth_user_email")
        val AUTH_EXPIRES_AT_EPOCH_SECONDS = longPreferencesKey("auth_expires_at_epoch_seconds")
    }

    private val safePreferencesFlow: Flow<Preferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    /**
     * تدفق تفضيلات المستخدم المحلية بشكل تفاعلي.
     */
    val userPreferencesFlow: Flow<LocalUserPreferences> = safePreferencesFlow.map { prefs ->
        LocalUserPreferences(
            darkModeEnabled = prefs[Keys.DARK_MODE_ENABLED] ?: false,
            preferredWilayaCode = prefs[Keys.PREFERRED_WILAYA_CODE],
            preferredCommuneId = prefs[Keys.PREFERRED_COMMUNE_ID],
            preferredCategoryId = prefs[Keys.PREFERRED_CATEGORY_ID],
            preferredMinPriceDzd = prefs[Keys.PREFERRED_MIN_PRICE_DZD],
            preferredMaxPriceDzd = prefs[Keys.PREFERRED_MAX_PRICE_DZD],
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            acceptedTermsAndPrivacy = prefs[Keys.ACCEPTED_TERMS_AND_PRIVACY] ?: false,
            languageCode = prefs[Keys.LANGUAGE_CODE] ?: "ar"
        )
    }

    /**
     * تدفق جلسة المصادقة المحفوظة مع فك تشفير رموز الوصول والتجديد تلقائياً.
     */
    val authSessionFlow: Flow<PersistedAuthSession?> = safePreferencesFlow.map { prefs ->
        decodeSessionFromPreferences(prefs)
    }

    /**
     * تدفق رمز الوصول الحالي (`access_token`).
     */
    val accessTokenFlow: Flow<String?> = authSessionFlow.map { it?.accessToken }

    /**
     * تدفق رمز التجديد الحالي (`refresh_token`).
     */
    val refreshTokenFlow: Flow<String?> = authSessionFlow.map { it?.refreshToken }

    private fun decodeSessionFromPreferences(prefs: Preferences): PersistedAuthSession? {
        val encAccess = prefs[Keys.ENCRYPTED_ACCESS_TOKEN] ?: return null
        val encRefresh = prefs[Keys.ENCRYPTED_REFRESH_TOKEN] ?: return null
        val userId = prefs[Keys.AUTH_USER_ID] ?: return null
        val email = prefs[Keys.AUTH_USER_EMAIL].orEmpty()
        val expiresAt = prefs[Keys.AUTH_EXPIRES_AT_EPOCH_SECONDS] ?: 0L

        val accessToken = tokenCipher.decrypt(encAccess) ?: return null
        val refreshToken = tokenCipher.decrypt(encRefresh) ?: return null
        if (accessToken.isBlank() || userId.isBlank()) return null

        return PersistedAuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            userId = userId,
            email = email,
            expiresAtEpochSeconds = expiresAt
        )
    }

    suspend fun getUserPreferences(): LocalUserPreferences = userPreferencesFlow.first()

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DARK_MODE_ENABLED] = enabled
        }
    }

    suspend fun setPreferredLocation(wilayaCode: Int?, communeId: Int? = null) {
        dataStore.edit { prefs ->
            if (wilayaCode == null) {
                prefs.remove(Keys.PREFERRED_WILAYA_CODE)
                prefs.remove(Keys.PREFERRED_COMMUNE_ID)
            } else {
                prefs[Keys.PREFERRED_WILAYA_CODE] = wilayaCode
                if (communeId == null) {
                    prefs.remove(Keys.PREFERRED_COMMUNE_ID)
                } else {
                    prefs[Keys.PREFERRED_COMMUNE_ID] = communeId
                }
            }
        }
    }

    suspend fun setPreferredCategory(categoryId: Int?) {
        dataStore.edit { prefs ->
            if (categoryId == null) {
                prefs.remove(Keys.PREFERRED_CATEGORY_ID)
            } else {
                prefs[Keys.PREFERRED_CATEGORY_ID] = categoryId
            }
        }
    }

    suspend fun setPreferredPriceRange(minPriceDzd: Long?, maxPriceDzd: Long?) {
        dataStore.edit { prefs ->
            if (minPriceDzd == null) {
                prefs.remove(Keys.PREFERRED_MIN_PRICE_DZD)
            } else {
                prefs[Keys.PREFERRED_MIN_PRICE_DZD] = minPriceDzd
            }
            if (maxPriceDzd == null) {
                prefs.remove(Keys.PREFERRED_MAX_PRICE_DZD)
            } else {
                prefs[Keys.PREFERRED_MAX_PRICE_DZD] = maxPriceDzd
            }
        }
    }

    suspend fun saveFilterPreferences(
        categoryId: Int?,
        wilayaCode: Int?,
        communeId: Int?,
        minPriceDzd: Long?,
        maxPriceDzd: Long?
    ) {
        dataStore.edit { prefs ->
            if (categoryId == null) prefs.remove(Keys.PREFERRED_CATEGORY_ID) else prefs[Keys.PREFERRED_CATEGORY_ID] = categoryId
            if (wilayaCode == null) {
                prefs.remove(Keys.PREFERRED_WILAYA_CODE)
                prefs.remove(Keys.PREFERRED_COMMUNE_ID)
            } else {
                prefs[Keys.PREFERRED_WILAYA_CODE] = wilayaCode
                if (communeId == null) prefs.remove(Keys.PREFERRED_COMMUNE_ID) else prefs[Keys.PREFERRED_COMMUNE_ID] = communeId
            }
            if (minPriceDzd == null) prefs.remove(Keys.PREFERRED_MIN_PRICE_DZD) else prefs[Keys.PREFERRED_MIN_PRICE_DZD] = minPriceDzd
            if (maxPriceDzd == null) prefs.remove(Keys.PREFERRED_MAX_PRICE_DZD) else prefs[Keys.PREFERRED_MAX_PRICE_DZD] = maxPriceDzd
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun setAcceptedTermsAndPrivacy(accepted: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCEPTED_TERMS_AND_PRIVACY] = accepted
        }
    }

    suspend fun setLanguageCode(languageCode: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_CODE] = languageCode.trim().ifBlank { "ar" }
        }
    }

    suspend fun clearUserPreferences() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.DARK_MODE_ENABLED)
            prefs.remove(Keys.PREFERRED_WILAYA_CODE)
            prefs.remove(Keys.PREFERRED_COMMUNE_ID)
            prefs.remove(Keys.PREFERRED_CATEGORY_ID)
            prefs.remove(Keys.PREFERRED_MIN_PRICE_DZD)
            prefs.remove(Keys.PREFERRED_MAX_PRICE_DZD)
            prefs.remove(Keys.NOTIFICATIONS_ENABLED)
            prefs.remove(Keys.ACCEPTED_TERMS_AND_PRIVACY)
            prefs.remove(Keys.LANGUAGE_CODE)
        }
    }

    /**
     * حفظ رموز المصادقة بشكل مشفر داخل DataStore.
     */
    suspend fun saveAuthTokens(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String = "",
        expiresAtEpochSeconds: Long
    ) {
        saveAuthSession(
            PersistedAuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = userId,
                email = email,
                expiresAtEpochSeconds = expiresAtEpochSeconds
            )
        )
    }

    suspend fun saveAuthSession(session: PersistedAuthSession) {
        val encAccess = tokenCipher.encrypt(session.accessToken)
        val encRefresh = tokenCipher.encrypt(session.refreshToken)
        dataStore.edit { prefs ->
            prefs[Keys.ENCRYPTED_ACCESS_TOKEN] = encAccess
            prefs[Keys.ENCRYPTED_REFRESH_TOKEN] = encRefresh
            prefs[Keys.AUTH_USER_ID] = session.userId
            prefs[Keys.AUTH_USER_EMAIL] = session.email
            prefs[Keys.AUTH_EXPIRES_AT_EPOCH_SECONDS] = session.expiresAtEpochSeconds
        }
    }

    suspend fun getAuthSession(): PersistedAuthSession? = authSessionFlow.first()

    suspend fun getAccessToken(): String? = accessTokenFlow.first()

    suspend fun getRefreshToken(): String? = refreshTokenFlow.first()

    /**
     * مسح رموز المصادقة فقط مع الإبقاء على تفضيلات المستخدم المحلية.
     */
    suspend fun clearAuthTokens() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ENCRYPTED_ACCESS_TOKEN)
            prefs.remove(Keys.ENCRYPTED_REFRESH_TOKEN)
            prefs.remove(Keys.AUTH_USER_ID)
            prefs.remove(Keys.AUTH_USER_EMAIL)
            prefs.remove(Keys.AUTH_EXPIRES_AT_EPOCH_SECONDS)
        }
    }

    /**
     * تطبيق واجهة `SecureSessionStore` للتكامل المباشر مع `SupabaseClient`.
     */
    override fun save(session: PersistedAuthSession) {
        runBlocking {
            saveAuthSession(session)
        }
    }

    override fun load(): PersistedAuthSession? = runBlocking {
        try {
            getAuthSession()
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun clear() {
        runBlocking {
            runCatching { clearAuthTokens() }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DataStoreRepository? = null

        fun getInstance(context: Context): DataStoreRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DataStoreRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        /**
         * إنشاء نسخة معزولة للاختبارات الآلية باستخدام ملف DataStore مؤقت.
         */
        fun createForTesting(
            storageFile: File,
            scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob()),
            tokenCipher: TokenCipher = AesGcmTokenCipher()
        ): DataStoreRepository {
            val testDataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { storageFile }
            )
            return DataStoreRepository(testDataStore, tokenCipher)
        }
    }
}

typealias UserPreferencesDataStoreRepository = DataStoreRepository
