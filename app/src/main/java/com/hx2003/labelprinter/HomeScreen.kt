package com.hx2003.labelprinter

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.canhub.cropper.CropImageView
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showSystemUi = true)
@Composable
fun HomeScreen(
    onDoneClick: () -> Unit = {},
    printerViewModel: PrinterViewModel = koinViewModel()
) {
    val cropViewReactiveState by printerViewModel.cropViewReactiveState.collectAsStateWithLifecycle()

    val cropImageViewRef = remember { mutableStateOf<CropImageView?>(null) }

    val context = LocalContext.current

    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            if (intent != null && intent.data != null) {
                // The activity returned some data
                val uri = intent.data
                printerViewModel.setCropViewUri(uri)
            } else {
                // The activity did not return any uri data,
                // this is the intended behaviour when using the camera
                // but we know that the image should be stored in the previously supplied temp uri
                val uri = getTempImageUri(context)
                printerViewModel.setCropViewUri(uri)
            }

            // Whenever we add a new image (or replace an image if the previous already exists),
            // we probably want to reset whatever configs like the crop rectangle, rotation and so on
            printerViewModel.cropViewInternalState = CropViewInternalState()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = { Text("Label Printer") },
                actions = {
                    IconButton(onClick = {
                        cropImageViewRef.value?.apply {
                            /*val sourceImageRect = wholeImageRect
                            Log.i("sourceImageRect", sourceImageRect.toString())
                            if(sourceImageRect != null) {
                                cropRect = when (rotatedDegrees) {
                                    90, 270 -> {
                                        // If the image is rotated 90 or 270 degrees
                                        Rect(0, 0, sourceImageRect.bottom, sourceImageRect.right)
                                    }
                                    0, 180 -> {
                                        sourceImageRect
                                    }
                                    else -> {
                                        // Note rotatedDegrees is a Integer
                                        // In our application only the nice 0, 90, 180 or 270 degrees rotation
                                        // are possible, other rotation angles are not implemented yet
                                        error("cannot handle rotatedDegrees which is not 0, 90, 180 or 270 degrees")
                                    }
                                }
                            }
                            Log.i("now cropRect", cropRect.toString())
                            //cropRect =*/
                            cropRect = wholeImageRect
                            scaleType = CropImageView.ScaleType.CENTER
                            scaleType = CropImageView.ScaleType.FIT_CENTER
                            resetCropRect()
                            resetCropRect()
                            rotatedDegrees = printerViewModel.cropViewInternalState.rotatedDegrees
                            isFlippedHorizontally = printerViewModel.cropViewInternalState.isFlippedHorizontally
                            isFlippedVertically = printerViewModel.cropViewInternalState.isFlippedVertically
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.material_symbols_outlined_fullscreen),
                            contentDescription = "Use Entire Image"
                        )
                    }
                    IconButton(onClick = {
                        cropImageViewRef.value?.apply {
                            rotateImage(90)
                            printerViewModel.cropViewInternalState.rotatedDegrees = rotatedDegrees
                            // Apparently, rotating the image may also cause a change in isFlippedHorizontally, isFlippedVertically
                            printerViewModel.cropViewInternalState.isFlippedHorizontally = isFlippedHorizontally
                            printerViewModel.cropViewInternalState.isFlippedVertically = isFlippedVertically
                        }
                    }) {
                        Icon(
                            painterResource(R.drawable.material_symbols_outlined_rotate_90_degrees_cw),
                            contentDescription = "Rotate 90 Degrees"
                        )
                    }
                    FlipIconButtonWithDropdown(
                        onFlipHorizontalClick = {
                            cropImageViewRef.value?.apply {
                                flipImageHorizontally()
                                printerViewModel.cropViewInternalState.isFlippedHorizontally = isFlippedHorizontally
                            }
                        },
                        onFlipVerticalClick = {
                            cropImageViewRef.value?.apply {
                                flipImageVertically()
                                printerViewModel.cropViewInternalState.isFlippedVertically = isFlippedVertically
                            }
                        }
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            CropImageViewComposable(
                uri = cropViewReactiveState.uri,
                cropImageViewRef = cropImageViewRef,
                onSetImageUriCompleteListener = {
                    // Called when the image URI finishes loading.
                    // This happens after setImageUriAsync inside CropImageViewComposable is triggered,
                    // either because the URI changed or the composable was recreated.
                    cropImageView ->
                    val cropViewInternalState = printerViewModel.cropViewInternalState
                    if(cropViewInternalState.uri != cropImageView.imageUri) {
                        cropViewInternalState.uri = cropImageView.imageUri
                        // A different image has been loaded
                        // CropImageView should automatically reset all the config like cropRect, rotation and so on...
                        // So lets sync our printerViewModel.cropViewInternalState to the new default config
                        cropViewInternalState.apply {
                            cropRect = cropImageView.cropRect
                            rotatedDegrees = cropImageView.rotatedDegrees
                            isFlippedHorizontally = cropImageView.isFlippedHorizontally
                            isFlippedVertically = cropImageView.isFlippedVertically
                        }
                    } else {
                        // The same image has been loaded
                        // This may be because a previous instance of CropImageView has been destroyed
                        // and thus lost its config like cropRect, rotation and so on...
                        // We need to restore back the previous crop settings

                        val copiedCropRect = Rect(cropViewInternalState.cropRect)
                        Log.i("copiedCropRect", copiedCropRect.toString())
                        cropImageView.apply {

                            cropRect = copiedCropRect
                            rotatedDegrees = cropViewInternalState.rotatedDegrees
                            isFlippedHorizontally = cropViewInternalState.isFlippedHorizontally
                            isFlippedVertically = cropViewInternalState.isFlippedVertically
                        }
                    }
                },
                onCropWindowChangedListener = { rect ->
                    Log.i("cropRect changed", rect.toString())
                    printerViewModel.cropViewInternalState.cropRect = rect
                })
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FloatingActionButton(
                    onClick = {
                        val intent = createImagePickerIntent(context)
                        activityLauncher.launch(intent)
                    }
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add Image"
                    )
                }

                FloatingActionButton(
                    onClick = onDoneClick
                ) {
                    Icon(
                        Icons.Outlined.Done,
                        contentDescription = "Done"
                    )
                }
            }
        }
    }
}

@Composable
fun FlipIconButtonWithDropdown(
    onFlipHorizontalClick: () -> Unit = {},
    onFlipVerticalClick: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                painterResource(R.drawable.material_symbols_outlined_flip),
                contentDescription = "Flip"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Flip Horizontally") },
                onClick = {
                    expanded = false
                    onFlipHorizontalClick();
                }
            )
            DropdownMenuItem(
                text = { Text("Flip Vertically") },
                onClick = {
                    expanded = false
                    onFlipVerticalClick();
                }
            )
        }
    }
}

@Composable
fun CropImageViewComposable(
    uri: Uri?,
    cropImageViewRef: MutableState<CropImageView?>,
    onSetImageUriCompleteListener: (CropImageView) -> Unit = {},
    onCropWindowChangedListener: (Rect?) -> Unit = {}
) {
    AndroidView(
        modifier = Modifier
            .fillMaxSize(),
        factory = { context ->
            val cropImageView = CropImageView(context)
            cropImageViewRef.value = cropImageView // store the reference

            cropImageView.setOnCropWindowChangedListener {
                onCropWindowChangedListener(cropImageView.cropRect)
            }

            cropImageView.setOnSetImageUriCompleteListener {
                cropImageView, uri, exception ->
                onSetImageUriCompleteListener(cropImageView)
            }

            cropImageView  // Returns cropImageView
        },
        update = {cropImageView ->
            cropImageView.setImageUriAsync(uri)
        }
    )
}