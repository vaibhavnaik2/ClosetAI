package com.closetai.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReleaseSmokeTest {
    @Test fun launcherStartsAndRecreatesWithoutAccount() {
        Robolectric.buildActivity(MainActivity::class.java).use { activity ->
            activity.setup().visible()
            assertFalse(activity.get().isFinishing)
            activity.recreate()
            assertFalse(activity.get().isFinishing)
        }
    }

    @Test fun logoutClearsAccountScopedData() {
        val vm = ClosetViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.signOut()
        assertNull(vm.session)
        assertTrue(vm.items.isEmpty())
        assertTrue(vm.driveFiles.isEmpty())
        assertTrue(vm.utilityData.isEmpty())
        assertNull(vm.pendingExport)
        assertNull(vm.pendingExternalUrl)
        assertFalse(vm.busy)
    }

    @Test fun unsupportedImportFailsBeforeDecoding() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertThrows(IllegalArgumentException::class.java) {
            ImageSanitizer.sanitize(context, Uri.parse("content://untrusted/file.exe"))
        }
    }

    @Test fun missingMetadataDoesNotInventBrand() {
        val item = org.json.JSONObject("{\"id\":\"test\",\"name\":\"Shirt\"}").toWardrobeItem()
        assertNull(item.brand)
        assertFalse(item.favorite)
        assertEquals(0, item.wearCount)
    }
}
