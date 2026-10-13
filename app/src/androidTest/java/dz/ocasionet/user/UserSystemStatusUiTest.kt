package dz.ocasionet.user

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dz.ocasionet.user.ui.UserSystemStatesContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class UserSystemStatusUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun systemStatusUsesPlainLanguageAndRefreshActionWorks() {
        val refreshCalled = AtomicBoolean(false)
        composeRule.setContent {
            UserSystemStatesContent(
                connectionLabel = "البيانات محدثة",
                darkModeEnabled = false,
                preferredWilayaCode = 16,
                onRefresh = { refreshCalled.set(true) }
            )
        }

        composeRule.onNodeWithText("حالة التطبيق والاتصال").assertIsDisplayed()
        composeRule.onNodeWithText("حالة البيانات: البيانات محدثة").assertIsDisplayed()
        composeRule.onNodeWithText("المظهر: فاتح • الولاية المفضلة: 16").assertIsDisplayed()
        composeRule.onNodeWithText("تحديث حالة الاتصال").performClick()
        assertTrue("The refresh callback should be invoked", refreshCalled.get())
        composeRule.onNodeWithText("Supabase", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("RLS", substring = true).assertDoesNotExist()
    }
}
