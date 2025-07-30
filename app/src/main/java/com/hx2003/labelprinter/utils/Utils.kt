package com.hx2003.labelprinter.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.hx2003.labelprinter.BitmapState
import com.hx2003.labelprinter.DitherOption
import java.io.File
import androidx.core.graphics.get
import androidx.core.graphics.set

fun transformBitmap(bitmapState: BitmapState): Bitmap? {
    // Rescale the sourceBitmap,
    // convert to grayscale
    // then optionally apply dithering or directly convert to B&W
    val sourceBitmap = bitmapState.bitmap
    if(sourceBitmap == null) return null

    val scalingFactor = bitmapState.labelWidth.pixels.toFloat() / sourceBitmap.height

    // Convert the high-res image to a low-res image
    // suitable for printing
    val resizedBitmap = sourceBitmap.scale(
        width =  (sourceBitmap.width * scalingFactor).toInt(),
        height = (sourceBitmap.height * scalingFactor).toInt()
    )

    val grayScaleBitmap = createBitmap(
        width = resizedBitmap.width,
        height = resizedBitmap.height,
        config = Bitmap.Config.ARGB_8888,
        hasAlpha = false
    )

    val canvas = Canvas(grayScaleBitmap)
    val colorMatrix = ColorMatrix()
    colorMatrix.setSaturation(0f)
    val paint = Paint()
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(resizedBitmap, 0f, 0f, paint)

    val height = grayScaleBitmap.height
    val width = grayScaleBitmap.width

    if(bitmapState.dither == DitherOption.NONE) {
        val threshold: Int = (bitmapState.colorThreshold * 255).toInt()
        // If pixel grayscale is below threshold it is set to black otherwise white
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel: Int = grayScaleBitmap[x, y]
                if((pixel and 0xFF) < threshold) {
                    grayScaleBitmap[x, y] = Color.BLACK
                } else {
                    grayScaleBitmap[x, y] = Color.WHITE
                }
            }
        }

        return grayScaleBitmap
    } else {
        // While the code below may be slow,
        // it is not a huge concern as we reduced the size of the image earlier

        // Convert AVVV, where V is the grayscale value
        // to only V
        for (x in 0 until width) {
            for (y in 0 until height) {
                grayScaleBitmap[x, y] = grayScaleBitmap[x, y] and 0xFF
            }
        }

        val threshold: Int = 128
        // Floyd–Steinberg dithering
        for (y in 0 until height) {
            for (x in 0 until width) {
                val oldValue: Int = grayScaleBitmap[x, y]
                val newValue: Int = if(oldValue < threshold) 0 else 255

                grayScaleBitmap[x, y] = newValue

                val quantizationError = oldValue - newValue

                if (x + 1 < width) {
                    grayScaleBitmap[x + 1, y] = grayScaleBitmap[x + 1, y] + quantizationError * 7 / 16
                }
                if (x - 1 >= 0 && y + 1 < height) {
                    grayScaleBitmap[x - 1, y + 1] = grayScaleBitmap[x - 1, y + 1] + quantizationError * 3 / 16
                }
                if (y + 1 < height) {
                    grayScaleBitmap[x    , y + 1] = grayScaleBitmap[x    , y + 1] + quantizationError * 5 / 16
                }
                if( x + 1 < width && y + 1 < height) {
                    grayScaleBitmap[x + 1, y + 1] = grayScaleBitmap[x + 1, y + 1] + quantizationError * 1 / 16
                }
            }
        }

        // Now convert V, where V is the grayscale value
        // to the standard ARGB value which is AVVV in our case
        for (x in 0 until width) {
            for (y in 0 until height) {
                val v = grayScaleBitmap[x, y]
                grayScaleBitmap[x, y] = Color.argb(0xFF, v, v, v)
            }
        }

        return grayScaleBitmap
    }
}

fun getTempImageUri(context: Context): Uri {
    val file = File(context.cacheDir, "temp_camera_image.jpg")

    if (!file.exists()) {
        Log.w("getTempImageUri", "Cannot find temp image file from camera")
    }

    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}

fun createImagePickerIntent(context: Context): Intent {
    val photoFile = File(context.cacheDir, "temp_camera_image.jpg").apply {
            createNewFile()
            deleteOnExit()
        }

    val photoURI = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)

    val allIntents: MutableList<Intent> = ArrayList()

    allIntents.add(
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
    )

    allIntents.add(
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    )
    /*
    val packageManager = context.packageManager

    fun addQueriedIntents(packageManager: PackageManager, baseIntent: Intent, allIntents: MutableList<Intent>) {

        val resolveInfos = packageManager.queryIntentActivities(baseIntent, 0)
        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            val className = resolveInfo.activityInfo.name

            val intent = Intent(baseIntent).apply {
                component = ComponentName(packageName, className)
            }
            intent.setPackage(packageName)

            allIntents.add(intent)
        }
    }

    addQueriedIntents(packageManager, Intent(MediaStore.ACTION_IMAGE_CAPTURE), allIntents)

    val photoFile = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        "temp_image.jpg"
    )
    val photoURI = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)

    val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    addQueriedIntents(packageManager, cameraIntent, allIntents)

    val baseIntent = Intent(Intent.ACTION_GET_CONTENT)
    baseIntent.type = "image/*";

    //baseIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

    addQueriedIntents(packageManager, baseIntent, allIntents)

    Log.i("tag", allIntents.toString())

     val intent = Intent.createChooser(allIntents.removeAt(0), null)
    intent.putExtra(Intent.EXTRA_ALTERNATE_INTENTS , allIntents.toTypedArray())
    launcher.launch(intent)
     */*/

    val intent = Intent.createChooser(allIntents.removeAt(0), null)
    intent.putExtra(Intent.EXTRA_INITIAL_INTENTS , allIntents.toTypedArray())

    return intent
}