package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageProcessor {

    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360 == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Enhances contrast and sharpens text for Arabic invoices and receipts.
     */
    fun enhanceInvoiceImage(bitmap: Bitmap, contrastFactor: Float = 1.35f, brightnessShift: Float = 10f): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        // ColorMatrix for contrast and brightness
        val cm = ColorMatrix(floatArrayOf(
            contrastFactor, 0f, 0f, 0f, brightnessShift,
            0f, contrastFactor, 0f, 0f, brightnessShift,
            0f, 0f, contrastFactor, 0f, brightnessShift,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun cropBitmap(bitmap: Bitmap, left: Float, top: Float, width: Float, height: Float): Bitmap {
        val x = (left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val y = (top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val w = (width * bitmap.width).toInt().coerceIn(1, bitmap.width - x)
        val h = (height * bitmap.height).toInt().coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, w, h)
    }

    fun saveBitmapToCache(context: Context, bitmap: Bitmap, prefix: String = "invoice_page"): Uri {
        val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        return Uri.fromFile(file)
    }
}
