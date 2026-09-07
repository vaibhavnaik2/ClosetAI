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
    @Test fun wearRequestCarriesStableIdempotencyKeyAndRejectsFalse() = runBlocking {
        MockWebServer().use { server ->
            val api = ClosetApi(server.url("/").toString().trimEnd('/'))
            val item = "11111111-1111-4111-8111-111111111111"
            val event = "22222222-2222-4222-8222-222222222222"
            server.enqueue(MockResponse().setBody("true"))
            api.recordWear("token", item, event)
            val request = server.takeRequest()
            assertEquals("/rest/v1/rpc/record_item_worn", request.path)
            val body = org.json.JSONObject(request.body.readUtf8())
            assertEquals(item, body.getString("p_item_id"))
            assertEquals(event, body.getString("p_event_id"))
            server.enqueue(MockResponse().setBody("false"))
            assertThrows(IllegalStateException::class.java) { runBlocking { api.recordWear("token", item, event) } }
            Unit
        }
    }

    @Test fun missingItemUpdateIsNotReportedAsSuccess() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[]"))
            assertThrows(IllegalStateException::class.java) {
                runBlocking { ClosetApi(server.url("/").toString().trimEnd('/')).updateItem("token", "11111111-1111-4111-8111-111111111111", "Jacket", "laundry") }
            }
        }
    }

}
