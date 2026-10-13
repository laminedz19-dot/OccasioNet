package dz.ocasionet.admin

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dz.ocasionet.admin.ui.AdminLoginScreen
import dz.ocasionet.admin.ui.AdminViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminLoginUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun loginScreenShowsSafeCopyAndDebugOnlyPreview() {
        composeRule.setContent {
            AdminLoginScreen(viewModel = AdminViewModel())
        }

        composeRule.onNodeWithText("سياسة حماية لوحة الإدارة").assertIsDisplayed()
        composeRule.onNodeWithText("دخول وضع المعاينة المحلي (نسخة التطوير فقط)")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("إعدادات المطور؛ لا تظهر في نسخة الإنتاج.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNode(hasText("Supabase", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasText("RLS", substring = true)).assertDoesNotExist()
    }
}
