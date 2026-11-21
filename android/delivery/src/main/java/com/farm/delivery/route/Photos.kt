package com.farm.delivery.route

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** A fresh file in the cache for the camera to write the proof photo into. */
fun newPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "pod_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Reads the picked photo, applies its EXIF rotation, scales it to at most [maxSide] px and
 * re-encodes it as JPEG (quality 70, like the old app's picker setting).
 */
suspend fun readPhotoAsJpeg(context: Context, uri: Uri, maxSide: Int = 1600): ByteArray = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2

    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: throw IllegalStateException("Could not read the photo")

    val rotation = runCatching {
        resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    }.getOrDefault(0)

    val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
    val matrix = Matrix().apply {
        if (scale < 1f) postScale(scale, scale)
        if (rotation != 0) postRotate(rotation.toFloat())
    }
    val out = if (scale < 1f || rotation != 0) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else bitmap

    ByteArrayOutputStream().use { stream ->
        out.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        stream.toByteArray()
    }
}
