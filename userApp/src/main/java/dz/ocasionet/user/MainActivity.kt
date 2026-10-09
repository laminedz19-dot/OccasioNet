package dz.ocasionet.user

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dz.ocasionet.core.network.SupabaseClient
import dz.ocasionet.user.ui.OccasioNetUserAppRoot
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val deepLinkUriState = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseClient.initializeSecureStore(applicationContext)
        enableEdgeToEdge()
        deepLinkUriState.value = intent?.dataString
        setContent {
            val currentDeepLink by deepLinkUriState.collectAsState()
            OccasioNetUserAppRoot(initialDeepLinkUri = currentDeepLink)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUriState.value = intent.dataString
    }
}
