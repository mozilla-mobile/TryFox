package org.mozilla.tryfox

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.mozilla.tryfox.data.FakeNotificationManager
import org.mozilla.tryfox.data.managers.DefaultNotificationManager
import org.mozilla.tryfox.data.managers.NotificationManager

@RunWith(AndroidJUnit4::class)
class MainActivityNotificationPermissionTest {
    @After
    fun restoreNotificationManager() {
        GlobalContext.get().declare<NotificationManager>(
            DefaultNotificationManager(ApplicationProvider.getApplicationContext()),
        )
    }

    @Test
    fun appLaunch_requestsNotificationPermission_whenItIsMissing() {
        val notificationManager = FakeNotificationManager(hasNotificationPermission = false)
        GlobalContext.get().declare<NotificationManager>(notificationManager)

        ActivityScenario.launch(MainActivity::class.java).use { }

        assertEquals(1, notificationManager.permissionRequestCount)
    }

    @Test
    fun appLaunch_doesNotRequestNotificationPermission_whenItIsGranted() {
        val notificationManager = FakeNotificationManager(hasNotificationPermission = true)
        GlobalContext.get().declare<NotificationManager>(notificationManager)

        ActivityScenario.launch(MainActivity::class.java).use { }

        assertEquals(0, notificationManager.permissionRequestCount)
    }
}
