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

        // Providers may report unknown or dishonest lengths. Bound the stream itself.
        val input = resolver.openInputStream(uri)?.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                require(output.size().toLong() + count <= maxInputBytes) { "Image exceeds 20 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("Image could not be opened")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input, 0, input.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image dimensions" }
        require(bounds.outWidth.toLong() * bounds.outHeight <= 100_000_000L) { "Image dimensions are too large" }
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxDimension * 2) {
            options.inSampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(input, 0, input.size, options)
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

        val visited = mutableSetOf<Uri>()
        fun walk(node: DocumentFile, depth: Int = 0) {
            if (depth > 32 || !visited.add(node.uri) || visited.size > 10000) return
            if (result.size >= limit) return
            if (node.isFile) {
                if (node.type?.lowercase() in supported) result += node.uri
                return
            }
            node.listFiles().forEach {
                if (result.size < limit) walk(it, depth + 1)
            }
        }

        walk(root)
        return result
    }
}
