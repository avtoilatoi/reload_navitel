package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Navitel AutoReload PIP", appName)
  }

  @Test
  fun `verify default app settings`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings = AppSettings.getInstance(context)
    assertNotNull(settings)
    assertEquals("com.navitel", settings.targetPackage)
    assertEquals(true, settings.autoDetectPip)
    assertEquals(true, settings.serviceEnabled)
  }
}

