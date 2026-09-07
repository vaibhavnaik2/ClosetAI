package com.closetai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DriveStatus(
    val configured: Boolean,
    val connected: Boolean,
    val email: String?
)

data class UserPreferencesAndroid(
    val theme: String = "system",
    val accentHex: String = "#111111",
    val gridDensity: String = "comfortable",
    val stylistPersona: String = "quiet_luxury",
    val includeAccessories: Boolean = true,
    val preferredColors: List<String> = emptyList(),
    val avoidColors: List<String> = emptyList(),
    val preferredBrands: List<String> = emptyList(),
    val styleGoals: List<String> = emptyList(),
    val climateProfile: String? = null,
    val defaultOccasion: String? = null,
    val privacyMode: String = "standard",
    val autoAnalyze: Boolean = true
)

class ClosetApi {
    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: ByteArray? = null,
        contentType: String = "application/json; charset=utf-8",
        extraHeaders: Map<String, String> = emptyMap()
    ): Request {
        val builder = Request.Builder()
            .url(ClosetConfig.baseUrl + path)
            .header("apikey", ClosetConfig.publishableKey)

        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        extraHeaders.forEach { (k, v) -> builder.header(k, v) }

        val media = contentType.toMediaType()
        val rb = body?.toRequestBody(media)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(rb ?: ByteArray(0).toRequestBody(media))
            "PATCH" -> builder.patch(rb ?: ByteArray(0).toRequestBody(media))
            "DELETE" -> builder.delete(rb)
            else -> error("Unsupported method $method")
        }
        return builder.build()
    }

    private suspend fun execute(request: Request): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (!response.isSuccessful) {
                val text = bytes.decodeToString()
                val message = runCatching {
                    val j = JSONObject(text)
                    j.optString("msg").ifBlank {
                        j.optString("message").ifBlank { j.optString("error").ifBlank { text } }
                    }
                }.getOrDefault(text)
                throw IllegalStateException("HTTP ${response.code}: ${message.take(500)}")
            }
            bytes
        }
    }

    private suspend fun json(request: Request): JSONObject =
        JSONObject(execute(request).decodeToString().ifBlank { "{}" })

    suspend fun signIn(email: String, password: String): AuthResult {
        val payload = JSONObject().put("email", email.trim()).put("password", password)
        val result = json(request(
            "/auth/v1/token?grant_type=password",
            "POST",
            body = payload.toString().toByteArray()
        ))
        return AuthResult(sessionFromAuth(result), "Signed in")
    }

    suspend fun signUp(email: String, password: String): AuthResult {
        val meta = JSONObject()
            .put("terms_version", ClosetConfig.termsVersion)
            .put("privacy_version", ClosetConfig.privacyVersion)
            .put("client", "android")
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .put("data", meta)

        val result = json(request("/auth/v1/signup", "POST", body = payload.toString().toByteArray()))
        val session = sessionFromAuth(result)
        return if (session != null) {
            AuthResult(session, "Account created")
        } else {
            AuthResult(null, "Account created. Check your email if confirmation is enabled.")
        }
    }

    suspend fun refresh(refreshToken: String): Session {
        val payload = JSONObject().put("refresh_token", refreshToken)
        val result = json(request(
            "/auth/v1/token?grant_type=refresh_token",
            "POST",
            body = payload.toString().toByteArray()
        ))
        return sessionFromAuth(result) ?: error("Session refresh failed")
    }

    suspend fun sendPasswordReset(email: String) {
        val payload = JSONObject().put("email", email.trim())
        execute(request("/auth/v1/recover", "POST", body = payload.toString().toByteArray()))
    }

    private fun sessionFromAuth(j: JSONObject): Session? {
        val access = j.optString("access_token")
        if (access.isBlank()) return null
        val refresh = j.optString("refresh_token")
        val user = j.optJSONObject("user") ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            userId = user.optString("id"),
            email = user.optString("email").takeIf { it.isNotBlank() },
            expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + j.optLong("expires_in", 3600)
        )
    }

    suspend fun bootstrap(token: String): JSONObject =
        json(request("/functions/v1/app-bootstrap", "POST", token, "{}".toByteArray()))

    suspend fun items(token: String): List<WardrobeItem> {
        val select = "id,name,brand,category,product_type,primary_color,fabric_family,material,fit,mood,storage_path,status,is_favorite,wear_count,occasions,style_tags"
        val bytes = execute(request(
            "/rest/v1/wardrobe_items?select=$select&status=neq.archived&order=created_at.desc&limit=1000",
            token = token
        ))
        val array = JSONArray(bytes.decodeToString())
        return buildList {
            for (i in 0 until array.length()) add(array.getJSONObject(i).toWardrobeItem())
        }
    }

    suspend fun preferences(token: String, userId: String): UserPreferencesAndroid {
        val bytes = execute(request(
            "/rest/v1/user_preferences?select=*&user_id=eq.$userId&limit=1",
            token = token
        ))
        val array = JSONArray(bytes.decodeToString())
        if (array.length() == 0) return UserPreferencesAndroid()
        return parsePreferences(array.getJSONObject(0))
    }

    suspend fun savePreferences(token: String, userId: String, prefs: UserPreferencesAndroid): UserPreferencesAndroid {
        val payload = JSONObject()
            .put("theme", prefs.theme)
            .put("accent_hex", prefs.accentHex)
            .put("grid_density", prefs.gridDensity)
            .put("stylist_persona", prefs.stylistPersona)
            .put("include_accessories", prefs.includeAccessories)
            .put("preferred_colors", JSONArray(prefs.preferredColors))
            .put("avoid_colors", JSONArray(prefs.avoidColors))
            .put("preferred_brands", JSONArray(prefs.preferredBrands))
            .put("style_goals", JSONArray(prefs.styleGoals))
            .put("climate_profile", prefs.climateProfile ?: JSONObject.NULL)
            .put("default_occasion", prefs.defaultOccasion ?: JSONObject.NULL)
            .put("privacy_mode", prefs.privacyMode)
            .put("auto_analyze", prefs.autoAnalyze)
            .put("updated_at", java.time.Instant.now().toString())

        val bytes = execute(request(
            "/rest/v1/user_preferences?user_id=eq.$userId&select=*",
            "PATCH",
            token,
            payload.toString().toByteArray(),
            extraHeaders = mapOf("Prefer" to "return=representation")
        ))
        val array = JSONArray(bytes.decodeToString())
        return if (array.length() > 0) parsePreferences(array.getJSONObject(0)) else prefs
    }

    private fun parsePreferences(j: JSONObject) = UserPreferencesAndroid(
        theme = j.optString("theme", "system"),
        accentHex = j.optString("accent_hex", "#111111"),
        gridDensity = j.optString("grid_density", "comfortable"),
        stylistPersona = j.optString("stylist_persona", "quiet_luxury"),
        includeAccessories = j.optBoolean("include_accessories", true),
        preferredColors = j.optJSONArray("preferred_colors").toStringList(),
        avoidColors = j.optJSONArray("avoid_colors").toStringList(),
        preferredBrands = j.optJSONArray("preferred_brands").toStringList(),
        styleGoals = j.optJSONArray("style_goals").toStringList(),
        climateProfile = j.optStringOrNull("climate_profile"),
        defaultOccasion = j.optStringOrNull("default_occasion"),
        privacyMode = j.optString("privacy_mode", "standard"),
        autoAnalyze = j.optBoolean("auto_analyze", true)
    )

    suspend fun imageBytes(token: String, storagePath: String): ByteArray {
        val encoded = storagePath.split("/").joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
        }
        return execute(request(
            "/storage/v1/object/authenticated/wardrobe-private/$encoded",
            token = token
        ))
    }

    suspend fun uploadSanitized(session: Session, image: SanitizedImage): JSONObject {
        val path = "${session.userId}/android/${UUID.randomUUID()}.jpg"
        execute(request(
            "/storage/v1/object/wardrobe-private/$path",
            "POST",
            session.accessToken,
            image.bytes,
            "image/jpeg",
            mapOf("x-upsert" to "false")
        ))

        val analysis = JSONObject()
            .put("storage_path", path)
            .put("sha256", image.sha256)
            .put("source_kind", "disk")
            .put("source_ref", "android:${image.displayName}")

        return json(request(
            "/functions/v1/analyze-wardrobe-item",
            "POST",
            session.accessToken,
            analysis.toString().toByteArray()
        ))
    }

    suspend fun importUrl(token: String, url: String): JSONObject {
        val body = JSONObject().put("url", url.trim())
        return json(request(
            "/functions/v1/import-url",
            "POST",
            token,
            body.toString().toByteArray()
        ))
    }

    suspend fun driveStatus(token: String): DriveStatus {
        val body = JSONObject().put("action", "status")
        val j = json(request("/functions/v1/google-drive", "POST", token, body.toString().toByteArray()))
        return DriveStatus(j.optBoolean("configured"), j.optBoolean("connected"), j.optStringOrNull("account_email"))
    }

    suspend fun driveAuthUrl(token: String): String {
        val body = JSONObject().put("action", "auth_url")
        return json(request("/functions/v1/google-drive", "POST", token, body.toString().toByteArray()))
            .getString("auth_url")
    }

    suspend fun driveFiles(token: String): List<DriveFile> {
        val body = JSONObject().put("action", "list")
        val j = json(request("/functions/v1/google-drive", "POST", token, body.toString().toByteArray()))
        val arr = j.optJSONArray("files") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                add(DriveFile(
                    id = f.optString("id"),
                    name = f.optString("name", "Drive image"),
                    mimeType = f.optString("mimeType"),
                    size = f.optLong("size", 0)
                ))
            }
        }
    }

    suspend fun importDrive(token: String, fileId: String): JSONObject {
        val body = JSONObject().put("action", "import").put("file_id", fileId)
        return json(request("/functions/v1/google-drive", "POST", token, body.toString().toByteArray()))
    }

    suspend fun naturalSearch(token: String, queryText: String): SearchResponse {
        val body = JSONObject().put("query", queryText).put("limit", 120)
        val j = json(request("/functions/v1/closet-search", "POST", token, body.toString().toByteArray()))
        val arr = j.optJSONArray("items") ?: JSONArray()
        val found = buildList {
            for (i in 0 until arr.length()) add(arr.getJSONObject(i).toWardrobeItem())
        }
        return SearchResponse(j.optString("interpretation"), found)
    }

    suspend fun stylist(token: String, mode: String, prompt: String): StylistResponse {
        val body = JSONObject().put("mode", mode).put("prompt", prompt)
        val j = json(request("/functions/v1/ai-stylist", "POST", token, body.toString().toByteArray()))
        val looksArray = j.optJSONArray("looks") ?: JSONArray()
        val looks = buildList {
            for (i in 0 until looksArray.length()) {
                val l = looksArray.getJSONObject(i)
                add(StylistLook(
                    id = l.optString("id", "look-$i"),
                    title = l.optString("title", "Look ${i + 1}"),
                    itemIds = l.optJSONArray("item_ids").toStringList(),
                    score = l.optInt("score", 0),
                    rationale = l.optString("rationale"),
                    tags = l.optJSONArray("tags").toStringList()
                ))
            }
        }
        return StylistResponse(
            summary = j.optString("summary"),
            looks = looks,
            advice = j.optJSONArray("advice").toStringList(),
            followUps = j.optJSONArray("follow_ups").toStringList()
        )
    }

    suspend fun saveLook(token: String, userId: String, look: StylistLook, occasion: String?, vibe: String?) {
        val payload = JSONObject()
            .put("user_id", userId)
            .put("title", look.title)
            .put("occasion", occasion ?: JSONObject.NULL)
            .put("vibe", vibe ?: JSONObject.NULL)
            .put("item_ids", JSONArray(look.itemIds))
            .put("score", look.score)
            .put("rationale", look.rationale)
            .put("tags", JSONArray(look.tags))
            .put("generated_by", "ai-stylist-android")
            .put("saved", true)
        execute(request(
            "/rest/v1/outfits",
            "POST",
            token,
            payload.toString().toByteArray(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        ))
    }

    suspend fun setFavorite(token: String, itemId: String, favorite: Boolean) {
        val payload = JSONObject().put("is_favorite", favorite)
        execute(request(
            "/rest/v1/wardrobe_items?id=eq.$itemId",
            "PATCH",
            token,
            payload.toString().toByteArray(),
            extraHeaders = mapOf("Prefer" to "return=minimal")
        ))
    }

    suspend fun accountExport(token: String): ByteArray =
        execute(request("/functions/v1/account-export", "POST", token, "{}".toByteArray()))

    suspend fun deleteAccount(token: String) {
        val body = JSONObject().put("confirmation", "DELETE MY CLOSET")
        execute(request("/functions/v1/delete-account", "POST", token, body.toString().toByteArray()))
    }
}
