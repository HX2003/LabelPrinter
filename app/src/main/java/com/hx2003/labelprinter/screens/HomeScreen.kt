package com.hx2003.labelprinter.screens

import android.app.Activity
import android.graphics.Rect
import android.net.Uri
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canhub.cropper.CropImageView
import com.hx2003.labelprinter.MainActivityViewModel
import com.hx2003.labelprinter.R
import com.hx2003.labelprinter.utils.createImagePickerIntent
import com.hx2003.labelprinter.utils.getTempImageUri
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDone: () -> Unit = {},
    mainActivityViewModel: MainActivityViewModel
) {
    val cropViewState by mainActivityViewModel.cropViewState.collectAsStateWithLifecycle()

    val cropImageViewRef = remember { mutableStateOf<CropImageView?>(null) }

    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope() // Obtain a coroutineScope tied to the composable's lifecycle

    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            val intent = result.data
            if (intent != null && intent.data != null) {
                // The activity returned some data
                val uri = intent.data
                mainActivityViewModel.setCropViewUri(uri)
            } else {
                // The activity did not return any uri data,
                // this is the intended behaviour when using the camera
                // but we know that the image should be stored in the previously supplied temp uri
                val uri = getTempImageUri(context)
                mainActivityViewModel.setCropViewUri(uri)
            }
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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if(cropViewState.uriLoaded) {
                        IconButton(onClick = {
                            cropImageViewRef.value?.apply {
                                resetCropRect()
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
                                }
                            },
                            onFlipVerticalClick = {
                                cropImageViewRef.value?.apply {
                                    flipImageVertically()
                                }
                            }
                        )
                    }
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
                uri = cropViewState.uri,
                cropImageViewRef = cropImageViewRef,
                onSetImageUriCompleteListener = {
                    mainActivityViewModel.setUriLoadedFlag()
                }
            )
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
                        painterResource(R.drawable.material_symbols_outlined_add),
                        contentDescription = "Add Image"
                    )
                }

                if(cropViewState.uriLoaded) {
                    FloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                mainActivityViewModel.setCroppedBitmap(cropImageViewRef.value?.getCroppedImage())
                                mainActivityViewModel.setNumCopies(1)
                                // We need to request permission for the printer, in order to get the label size
                                // Although the user has not yet selected which label printer is desired,
                                // in most cases it is unlikely that the users have multiple label printers
                                // Even so, the user can reject the request and get the permission later
                                mainActivityViewModel.requestPermissionAndConnect()
                                mainActivityViewModel.queryPrinter()
                                mainActivityViewModel.clearPrintStatusAndPrintRequestResult()
                                onDone()
                            }
                        }
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
                    onFlipHorizontalClick()
                }
            )
            DropdownMenuItem(
                text = { Text("Flip Vertically") },
                onClick = {
                    expanded = false
                    onFlipVerticalClick()
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