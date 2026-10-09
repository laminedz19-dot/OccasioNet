package dz.ocasionet.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dz.ocasionet.core.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val CairoFontFamily = FontFamily(
    Font(R.font.cairo, FontWeight.Normal),
    Font(R.font.cairo, FontWeight.SemiBold),
    Font(R.font.cairo, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal)
)

enum class AppColorPalette(val titleAr: String) {
    OCCASIONET_LOGO("هوية الشعار الرسمية (الأزرق الملكي والبرتقالي والأخضر)"),
    OCCASIONET_LOGO_DARK("هوية الشعار الليلية (الكحلي الملكي والبرتقالي)"),
    OCCASIONET_ORANGE_ACCENT("برتقالي الشعار الحيوي والأزرق الكحلي"),
    OCCASIONET_GREEN_ACCENT("أخضر الشعار المتجدد والأزرق الملكي")
}

object OccasioNetPaletteStore {
    private val _selectedPalette = MutableStateFlow(AppColorPalette.OCCASIONET_LOGO)
    val selectedPalette: StateFlow<AppColorPalette> = _selectedPalette.asStateFlow()

    fun selectPalette(palette: AppColorPalette) {
        _selectedPalette.value = palette
    }

    fun cycleNextPalette() {
        val all = AppColorPalette.entries
        val nextIndex = (all.indexOf(_selectedPalette.value) + 1) % all.size
        _selectedPalette.value = all[nextIndex]
    }
}

// 1. اللوحة الافتراضية المستوحاة مباشرة من شعار OccasioNet:
// الأزرق الملكي (#0047B3) + البرتقالي الحيوي (#FF7A00) + الأخضر المتجدد (#16A34A)
private val OccasioNetLogoLightScheme = lightColorScheme(
    primary = Color(0xFF0047B3),          // أزرق الشعار الملكي (Occasio)
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCEBFF), // خلفية زرقاء فاتحة ناعمة متناسقة مع الشعار
    onPrimaryContainer = Color(0xFF001F54),
    secondary = Color(0xFFFF7A00),        // برتقالي الشعار الساطع (Net & السهم العلوي)
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFEDD5),
    onSecondaryContainer = Color(0xFF7C2D12),
    tertiary = Color(0xFF16A34A),         // أخضر الشعار النابض (السهم السفلي)
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCFCE7),
    onTertiaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF4F8FF),       // خلفية بيضاء مزرقة نقية مطابقة لخلفية شاشة البداية
    onBackground = Color(0xFF001B44),     // كحلي داكن واضح للنصوص
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF001B44),
    surfaceVariant = Color(0xFFE3EEFF),   // بطاقات وحاويات متناسقة مع أزرق الشعار
    onSurfaceVariant = Color(0xFF003380),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF7F1D1D)
)

// 2. الوضع الليلي المستوحى من عمق أيقونة الشعار الزرقاء الداكنة
private val OccasioNetLogoDarkScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF001F54),
    primaryContainer = Color(0xFF003B95),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFFFF8C00),
    onSecondary = Color(0xFF3B1500),
    secondaryContainer = Color(0xFF7C2D12),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFF22C55E),
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFDCFCE7),
    background = Color(0xFF001538),       // كحلي ملكي عميق مطابق لخلفية الشعار الثانية
    onBackground = Color(0xFFF0F7FF),
    surface = Color(0xFF002259),
    onSurface = Color(0xFFF0F7FF),
    surfaceVariant = Color(0xFF003380),
    onSurfaceVariant = Color(0xFFBAE6FD),
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519)
)

// 3. لوحة تركيز البرتقالي الحيوي من الشعار مع الكحلي الملكي
private val OccasioNetOrangeAccentScheme = lightColorScheme(
    primary = Color(0xFFEA580C),          // برتقالي Net الرئيسي
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF431407),
    secondary = Color(0xFF0047B3),        // أزرق Occasio الملكي
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBFF),
    onSecondaryContainer = Color(0xFF001F54),
    tertiary = Color(0xFF16A34A),         // أخضر السهم
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFAF5),
    onBackground = Color(0xFF001B44),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF001B44),
    surfaceVariant = Color(0xFFFFE4C4),
    onSurfaceVariant = Color(0xFF7C2D12),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

// 4. لوحة تركيز الأخضر المتجدد من الشعار مع الأزرق الملكي
private val OccasioNetGreenAccentScheme = lightColorScheme(
    primary = Color(0xFF15803D),          // أخضر السهم السفلي في الشعار
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF0047B3),        // أزرق الشعار الملكي
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBFF),
    onSecondaryContainer = Color(0xFF001F54),
    tertiary = Color(0xFFFF7A00),         // برتقالي الشعار
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2FDF6),
    onBackground = Color(0xFF001B44),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF001B44),
    surfaceVariant = Color(0xFFD1FAE5),
    onSurfaceVariant = Color(0xFF065F46),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

// لوحة تطبيق الإدارة (OccasioNet Admin): كحلي الشعار العميق مع البرتقالي الذهبي والأخضر
private val OccasioNetAdminLogoScheme = darkColorScheme(
    primary = Color(0xFFFF8C00),          // برتقالي الشعار الذهبي للمشرفين
    onPrimary = Color(0xFF2B1100),
    primaryContainer = Color(0xFF003B95), // حاوية زرقاء ملكية
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFF22C55E),        // أخضر الشعار للاعتماد
    onSecondary = Color(0xFF052E16),
    secondaryContainer = Color(0xFF14532D),
    onSecondaryContainer = Color(0xFFDCFCE7),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF001F54),
    background = Color(0xFF001230),       // كحلي ملكي داكن جداً متناسق مع الشعار
    onBackground = Color(0xFFF0F7FF),
    surface = Color(0xFF001E4D),
    onSurface = Color(0xFFF0F7FF),
    surfaceVariant = Color(0xFF002E73),
    onSurfaceVariant = Color(0xFFBAE6FD),
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF881337),
    onErrorContainer = Color(0xFFFFE4E6)
)

val OccasioNetTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp
    ),
    titleLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = CairoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

val OccasioNetShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp)
)

private fun resolveUserColorScheme(palette: AppColorPalette): ColorScheme = when (palette) {
    AppColorPalette.OCCASIONET_LOGO -> OccasioNetLogoLightScheme
    AppColorPalette.OCCASIONET_LOGO_DARK -> OccasioNetLogoDarkScheme
    AppColorPalette.OCCASIONET_ORANGE_ACCENT -> OccasioNetOrangeAccentScheme
    AppColorPalette.OCCASIONET_GREEN_ACCENT -> OccasioNetGreenAccentScheme
}

@Composable
fun OccasioNetTheme(
    isAdminTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val activePalette by OccasioNetPaletteStore.selectedPalette.collectAsState()
    val scheme = if (isAdminTheme) OccasioNetAdminLogoScheme else resolveUserColorScheme(activePalette)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OccasioNetTypography,
            shapes = OccasioNetShapes,
            content = content
        )
    }
}
