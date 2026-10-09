package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appContext_and_Algeria58Wilayas_areVerified() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OccasioNet", appName)
        assertEquals(58, AlgeriaGeographyCatalog.wilayas.size)
        assertTrue(AlgeriaGeographyCatalog.communesForWilaya(16).isNotEmpty())
    }
}
