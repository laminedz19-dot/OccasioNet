package dz.ocasionet.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dz.ocasionet.core.BuildConfig
import dz.ocasionet.core.model.AppSettingsData
import dz.ocasionet.core.model.AuditLogItem
import dz.ocasionet.core.model.CategoryItem
import dz.ocasionet.core.model.Commune
import dz.ocasionet.core.model.FavoriteRecord
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.NotificationItem
import dz.ocasionet.core.model.PaymentRequestItem
import dz.ocasionet.core.model.ReportItem
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.model.UserProfile
import dz.ocasionet.core.model.Wilaya
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class AuthSignUpRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String,
    @Json(name = "data") val data: Map<String, String> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class AuthSignInRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AuthEmailRequest(
    @Json(name = "email") val email: String,
    @Json(name = "type") val type: String? = null
)

@JsonClass(generateAdapter = true)
data class AuthPasswordUpdateRequest(
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthUserDto(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String? = null,
    @Json(name = "email_confirmed_at") val emailConfirmedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupabaseSessionDto(
    @Json(name = "access_token") val accessToken: String? = null,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null,
    @Json(name = "user") val user: SupabaseAuthUserDto? = null
)

@JsonClass(generateAdapter = true)
data class SubmitPaymentRpcBody(
    @Json(name = "p_payment_method") val paymentMethod: String,
    @Json(name = "p_receipt_storage_path") val receiptStoragePath: String,
    @Json(name = "p_receipt_mime_type") val receiptMimeType: String,
    @Json(name = "p_receipt_size_bytes") val receiptSizeBytes: Long,
    @Json(name = "p_transaction_reference") val transactionReference: String,
    @Json(name = "p_user_note") val userNote: String
)

@JsonClass(generateAdapter = true)
data class ReviewPaymentRpcBody(
    @Json(name = "p_request_id") val requestId: String,
    @Json(name = "p_decision") val decision: String,
    @Json(name = "p_rejection_reason") val rejectionReason: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateListingRpcBody(
    @Json(name = "p_payment_request_id") val paymentRequestId: String,
    @Json(name = "p_category_id") val categoryId: Int,
    @Json(name = "p_wilaya_code") val wilayaCode: Int,
    @Json(name = "p_commune_id") val communeId: Int,
    @Json(name = "p_title") val title: String,
    @Json(name = "p_description") val description: String,
    @Json(name = "p_price_dzd") val priceDzd: Long,
    @Json(name = "p_condition") val condition: String,
    @Json(name = "p_contact_phone") val contactPhone: String,
    @Json(name = "p_image_urls") val imageUrls: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AdminBanUserRpcBody(
    @Json(name = "p_target_user_id") val targetUserId: String,
    @Json(name = "p_is_banned") val isBanned: Boolean,
    @Json(name = "p_ban_reason") val banReason: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminUpdateSettingsRpcBody(
    @Json(name = "p_listing_fee_dzd") val listingFeeDzd: Long,
    @Json(name = "p_ccp_instructions_ar") val ccpInstructionsAr: String,
    @Json(name = "p_baridimob_instructions_ar") val baridimobInstructionsAr: String,
    @Json(name = "p_payment_notice_ar") val paymentNoticeAr: String
)

@JsonClass(generateAdapter = true)
data class AdminModerateListingRpcBody(
    @Json(name = "p_listing_id") val listingId: String,
    @Json(name = "p_new_status") val newStatus: String,
    @Json(name = "p_reason") val reason: String = ""
)

@JsonClass(generateAdapter = true)
data class SignedUrlRequest(
    @Json(name = "expiresIn") val expiresIn: Int = 120
)

@JsonClass(generateAdapter = true)
data class SignedUrlResponse(
    @Json(name = "signedURL") val signedUrl: String
)

/**
 * واجهة الاتصال الكاملة بـ Supabase (Auth + PostgREST + RPC + Storage).
 */
interface SupabaseRestService {

    // --- Supabase Auth ---
    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: AuthSignUpRequest): Response<SupabaseSessionDto>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(@Body body: AuthSignInRequest): Response<SupabaseSessionDto>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshSession(@Body body: RefreshTokenRequest): Response<SupabaseSessionDto>

    @GET("auth/v1/user")
    suspend fun getCurrentAuthUser(): Response<SupabaseAuthUserDto>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(@Body body: AuthEmailRequest): Response<Unit>

    @POST("auth/v1/resend")
    suspend fun resendConfirmationEmail(@Body body: AuthEmailRequest): Response<Unit>

    @PATCH("auth/v1/user")
    suspend fun updatePassword(@Body body: AuthPasswordUpdateRequest): Response<SupabaseAuthUserDto>

    @POST("auth/v1/logout")
    suspend fun signOut(): Response<Unit>

    // --- PostgREST Queries ---
    @GET("rest/v1/listings")
    suspend fun getListings(
        @Query("select") select: String = "*",
        @Query("status") statusEq: String? = "eq.published",
        @Query("order") order: String = "created_at.desc"
    ): Response<List<ListingItem>>

    @GET("rest/v1/listings")
    suspend fun getMyListings(
        @Query("seller_id") sellerIdEq: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<ListingItem>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/listings")
    suspend fun updateOwnListing(
        @Query("id") idEq: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<ListingItem>>

    @GET("rest/v1/categories")
    suspend fun getCategories(@Query("order") order: String = "sort_order.asc"): Response<List<CategoryItem>>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/categories")
    suspend fun createCategory(@Body category: Map<String, @JvmSuppressWildcards Any>): Response<List<CategoryItem>>

    @GET("rest/v1/wilayas")
    suspend fun getWilayas(@Query("order") order: String = "code.asc"): Response<List<Wilaya>>

    @GET("rest/v1/communes")
    suspend fun getCommunes(@Query("wilaya_code") wilayaEq: String? = null): Response<List<Commune>>

    @GET("rest/v1/app_settings")
    suspend fun getAppSettings(@Query("id") idEq: String = "eq.1"): Response<List<AppSettingsData>>

    @GET("rest/v1/profiles")
    suspend fun getProfiles(
        @Query("id") idEq: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<UserProfile>>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/profiles")
    suspend fun updateOwnProfile(
        @Query("id") idEq: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<UserProfile>>

    @GET("rest/v1/payment_requests")
    suspend fun getPaymentRequests(
        @Query("user_id") userIdEq: String? = null,
        @Query("status") statusEq: String? = null,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<PaymentRequestItem>>

    @GET("rest/v1/favorites")
    suspend fun getFavorites(@Query("user_id") userIdEq: String): Response<List<FavoriteRecord>>

    @POST("rest/v1/favorites")
    suspend fun addFavorite(@Body favorite: FavoriteRecord): Response<Unit>

    @DELETE("rest/v1/favorites")
    suspend fun removeFavorite(
        @Query("user_id") userIdEq: String,
        @Query("listing_id") listingIdEq: String
    ): Response<Unit>

    @POST("rest/v1/reports")
    suspend fun submitReport(@Body report: Map<String, @JvmSuppressWildcards Any>): Response<Unit>

    @GET("rest/v1/reports")
    suspend fun getReports(@Query("order") order: String = "created_at.desc"): Response<List<ReportItem>>

    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("user_id") userIdEq: String,
        @Query("order") order: String = "created_at.desc"
    ): Response<List<NotificationItem>>

    @GET("rest/v1/audit_logs")
    suspend fun getAuditLogs(@Query("order") order: String = "created_at.desc"): Response<List<AuditLogItem>>

    // --- Server-Side PostgreSQL RPCs ---
    @POST("rest/v1/rpc/is_admin")
    suspend fun rpcIsAdmin(): Response<Boolean>

    @POST("rest/v1/rpc/submit_payment_request")
    suspend fun rpcSubmitPaymentRequest(@Body body: SubmitPaymentRpcBody): Response<String>

    @POST("rest/v1/rpc/review_payment_request")
    suspend fun rpcReviewPaymentRequest(@Body body: ReviewPaymentRpcBody): Response<Boolean>

    @POST("rest/v1/rpc/create_listing_with_paid_receipt")
    suspend fun rpcCreateListingWithPaidReceipt(@Body body: CreateListingRpcBody): Response<String>

    @POST("rest/v1/rpc/admin_set_user_ban_status")
    suspend fun rpcAdminSetUserBanStatus(@Body body: AdminBanUserRpcBody): Response<Boolean>

    @POST("rest/v1/rpc/admin_update_app_settings")
    suspend fun rpcAdminUpdateAppSettings(@Body body: AdminUpdateSettingsRpcBody): Response<Boolean>

    @POST("rest/v1/rpc/admin_moderate_listing")
    suspend fun rpcAdminModerateListing(@Body body: AdminModerateListingRpcBody): Response<Boolean>

    // --- Supabase Storage ---
    @POST("storage/v1/object/{bucket}/{path}")
    suspend fun uploadStorageObject(
        @Path("bucket") bucket: String,
        @Path(value = "path", encoded = true) objectPath: String,
        @Header("Content-Type") mimeType: String,
        @Body fileBody: RequestBody
    ): Response<Unit>

    @DELETE("storage/v1/object/{bucket}/{path}")
    suspend fun deleteStorageObject(
        @Path("bucket") bucket: String,
        @Path(value = "path", encoded = true) objectPath: String
    ): Response<Unit>

    @POST("storage/v1/object/sign/payment-receipts/{path}")
    suspend fun createSignedReceiptUrl(
        @Path(value = "path", encoded = true) objectPath: String,
        @Body body: SignedUrlRequest = SignedUrlRequest(120)
    ): Response<SignedUrlResponse>
}
