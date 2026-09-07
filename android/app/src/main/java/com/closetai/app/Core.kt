package com.closetai.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object ClosetConfig {
    const val baseUrl = "https://kgcdvvurhbuqyqeylour.supabase.co"
    const val publishableKey = "sb_publishable_H73Z9qahc47g3-cQyBaS-Q_JuJAqnbF"
    const val termsVersion = "2026-09"
    const val privacyVersion = "2026-09"
}

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val expiresAtEpochSeconds: Long
) {
    val isUsable: Boolean get() = accessToken.isNotBlank() && userId.isNotBlank()
}

data class AuthResult(val session: Session?, val message: String)

data class WardrobeItem(
    val id: String,
    val name: String,
    val brand: String?,
    val category: String?,
    val productType: String?,
    val color: String?,
    val material: String?,
    val fit: String?,
    val mood: String?,
    val storagePath: String?,
    val status: String,
    val favorite: Boolean,
    val wearCount: Int,
    val occasions: List<String>,
    val styleTags: List<String>
)

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long
)

data class StylistLook(
    val id: String,
    val title: String,
    val itemIds: List<String>,
    val score: Int,
    val rationale: String,
    val tags: List<String>
)

data class StylistResponse(
    val summary: String,
    val looks: List<StylistLook>,
    val advice: List<String>,
    val followUps: List<String>
)

data class SearchResponse(
    val interpretation: String,
    val items: List<WardrobeItem>
)

data class FilterSelection(
    val brands: Set<String> = emptySet(),
    val colors: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val productTypes: Set<String> = emptySet()
) {
    val count: Int get() = brands.size + colors.size + categories.size + productTypes.size
}

data class SanitizedImage(
    val source: Uri,
    val bytes: ByteArray,
    val sha256: String,
    val displayName: String
)

fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

fun JSONObject.toWardrobeItem(): WardrobeItem = WardrobeItem(
    id = optString("id"),
    name = optString("name", "Wardrobe item"),
    brand = optStringOrNull("brand"),
    category = optStringOrNull("category"),
    productType = optStringOrNull("product_type"),
    color = optStringOrNull("primary_color"),
    material = optStringOrNull("fabric_family") ?: optStringOrNull("material"),
    fit = optStringOrNull("fit"),
    mood = optStringOrNull("mood"),
    storagePath = optStringOrNull("storage_path"),
    status = optString("status", "available"),
    favorite = optBoolean("is_favorite", false),
    wearCount = optInt("wear_count", 0),
    occasions = optJSONArray("occasions").toStringList(),
    styleTags = optJSONArray("style_tags").toStringList()
)
