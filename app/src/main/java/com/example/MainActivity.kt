package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dz.ocasionet.core.network.SupabaseClient
import dz.ocasionet.user.ui.OccasioNetUserAppRoot

/**
 * تطبيق المستخدم المستقل بالكامل (OccasioNet User).
 * لا يحتوي على أي شاشات أو صلاحيات أو أكواد خاصة بتطبيق الإدارة (OccasioNet Admin)،
 * حيث يوجد تطبيق الإدارة في حزمة ومشروع مستقل تماماً (:adminApp -> dz.ocasionet.admin).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseClient.initializeSecureStore(applicationContext)
        enableEdgeToEdge()
        val deepLinkUri = intent?.dataString
        setContent {
            OccasioNetUserAppRoot(initialDeepLinkUri = deepLinkUri)
        }
    }
}
