package com.closetai.app

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiContractTest {
    @Test fun wardrobePaginationKeepsFetchingUntilEmpty() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[{\"id\":\"first\"}]"))
            server.enqueue(MockResponse().setBody("[{\"id\":\"second\"}]"))
            server.enqueue(MockResponse().setBody("[]"))
            val rows = ClosetApi(server.url("/").toString().trimEnd('/')).items("test-token")
            assertEquals(listOf("first", "second"), rows.map { it.id })
            val first = server.takeRequest()
            assertEquals("Bearer test-token", first.getHeader("Authorization"))
            assertTrue(first.path!!.contains("offset=0"))
            assertTrue(server.takeRequest().path!!.contains("offset=1"))
            assertTrue(server.takeRequest().path!!.contains("offset=2"))
        }
    }

    @Test fun emptyPreferenceUpdateIsNotReportedAsSuccess() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            assertThrows(IllegalStateException::class.java) {
                runBlocking { ClosetApi(server.url("/").toString().trimEnd('/')).savePreferences("token", "user", UserPreferencesAndroid()) }
            }
        }
    }

    @Test fun serverErrorsDoNotExposeSensitiveResponseBodies() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(500).setBody("secret-debug-value"))
            val error = assertThrows(java.io.IOException::class.java) {
                runBlocking { ClosetApi(server.url("/").toString().trimEnd('/')).items("token") }
            }
            assertFalse(error.message!!.contains("secret-debug-value"))
        }
    }
}
