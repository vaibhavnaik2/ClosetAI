package com.closetai.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

object ImageSanitizer {
    private val supported = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
    private const val maxInputBytes = 20L * 1024L * 1024L
    private const val maxDimension = 2048

    fun sanitize(context: Context, uri: Uri): SanitizedImage {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.lowercase()
            ?: throw IllegalArgumentException("Unknown image type")
        require(mime in supported) { "Unsupported image type: $mime" }

        val length = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(length <= 0 || length <= maxInputBytes) { "Image exceeds 20 MB" }

        val bitmap = resolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: throw IllegalArgumentException("Image could not be decoded")

        val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat())
        val clean = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        val output = ByteArrayOutputStream()
        require(clean.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "Could not sanitize image" }
        val bytes = output.toByteArray()
        require(bytes.isNotEmpty() && bytes.size <= maxInputBytes) { "Sanitized image is invalid" }

        if (clean !== bitmap) clean.recycle()
        bitmap.recycle()

        val sha = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

        val display = DocumentFile.fromSingleUri(context, uri)?.name ?: "clothing-photo.jpg"
        return SanitizedImage(uri, bytes, sha, display)
    }

    fun imageUrisInTree(context: Context, tree: Uri, limit: Int = 1000): List<Uri> {
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        val result = mutableListOf<Uri>()

        fun walk(node: DocumentFile) {
            if (result.size >= limit) return
            if (node.isFile) {
                if (node.type?.lowercase() in supported) result += node.uri
                return
            }
            node.listFiles().forEach {
                if (result.size < limit) walk(it)
            }
        }

        walk(root)
        return result
    }
}
