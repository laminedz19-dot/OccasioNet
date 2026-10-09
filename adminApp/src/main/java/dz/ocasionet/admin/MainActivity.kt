package dz.ocasionet.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dz.ocasionet.admin.ui.OccasioNetAdminAppRoot
import dz.ocasionet.core.network.SupabaseClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseClient.initializeSecureStore(applicationContext)
        enableEdgeToEdge()
        setContent {
            OccasioNetAdminAppRoot()
        }
    }
}
