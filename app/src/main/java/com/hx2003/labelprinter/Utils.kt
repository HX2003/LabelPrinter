package com.hx2003.labelprinter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

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