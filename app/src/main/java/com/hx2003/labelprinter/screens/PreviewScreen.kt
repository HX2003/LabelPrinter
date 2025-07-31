package com.hx2003.labelprinter.screens

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hx2003.labelprinter.DitherOption
import com.hx2003.labelprinter.PrintError
import com.hx2003.labelprinter.MainActivityViewModel
import com.hx2003.labelprinter.R
import kotlinx.coroutines.launch
import java.util.SortedMap
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    onDone: () -> Unit = {},
    onBack: () -> Unit = {},
    mainActivityViewModel: MainActivityViewModel
) {
    val warningDialogOpened = remember { mutableStateOf(false) }
    val warningDialogText = remember { mutableStateOf("") }

    val printReactiveState by mainActivityViewModel.printConfigReactiveState.collectAsStateWithLifecycle()
    val bitmapState by mainActivityViewModel.bitmapState.collectAsStateWithLifecycle()
    val transformedBitmapState by mainActivityViewModel.transformedBitmapState.collectAsStateWithLifecycle()
    val printerUsbState by mainActivityViewModel.printerUsbState.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope() // Obtain a coroutineScope tied to the composable's lifecycle

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {Text(stringResource(R.string.print_preview))},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                ) {
                    val transformBitmap = transformedBitmapState?.bitmap
                    PrintPreviewCanvas(
                        modifier = Modifier
                            .fillMaxSize(),
                        bitmap = transformBitmap
                    )
                    if(transformBitmap != null) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .background(color = Color.Black.copy(alpha = 0.3f))
                                .padding(6.dp)
                        ) {
                            Text(
                                text = "w: ${transformBitmap.width}px, h: ${transformBitmap.height}px",
                                color = Color.White
                            )
                        }
                    }
                }
                Column (
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f)
                        .padding(
                            horizontal = 16.dp,
                            vertical = 24.dp
                        )
                        .verticalScroll(rememberScrollState())
                        .overscroll(rememberOverscrollEffect()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OptionControl(
                        label = stringResource(R.string.printer),
                        selectedOption = printerUsbState.selectedPrinter,
                        options = printerUsbState.availablePrinters.mapValues { (_, value) ->
                            value.productName ?: ""
                        }.toSortedMap(),
                        onOptionSelected = { selected ->
                            coroutineScope.launch {
                                mainActivityViewModel.setSelectedPrinter(selected)
                                mainActivityViewModel.requestPermissionAndConnect()
                            }
                        },
                        noAvailableOptionsText = stringResource(R.string.no_printer_found),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    IntegerControl(
                        label = stringResource(R.string.copies),
                        value = printReactiveState.numCopies,
                        onValueChange = {
                            mainActivityViewModel.setNumCopies(it)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OptionControl(
                        label = stringResource(R.string.dither),
                        selectedOption = bitmapState.dither.toString(),
                        options = sortedMapOf(
                            Pair(DitherOption.NONE.toString(), stringResource(R.string.none)),
                            Pair(DitherOption.CLASSIC.toString(),stringResource(R.string.classic))
                        ),
                        onOptionSelected = {
                            selected -> mainActivityViewModel.setDither(DitherOption.valueOf(selected))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if(bitmapState.dither == DitherOption.NONE) {
                        SliderControl(
                            label = stringResource(R.string.threshold),
                            value = bitmapState.colorThreshold,
                            onValueChange = {
                                mainActivityViewModel.setColorThreshold(it)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Info(
                        label = stringResource(R.string.label_size),
                        value = stringResource(R.string.unknown),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier.padding( 16.dp)
                ) {
                    val stringWarningNoPrinterFound =
                        stringResource(R.string.warning_no_printer_found)
                    val stringWarningPermissionNotGranted =
                        stringResource(R.string.warning_permission_not_granted)
                    val stringWarningPrinterGeneric =
                        stringResource(R.string.warning_printer_generic)

                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .alpha(if (printerUsbState.availablePrinters.isNotEmpty()) 1.0f else 0.3f),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        onClick = {
                            coroutineScope.launch {
                                val res: PrintError = mainActivityViewModel.print()
                                when (res) {
                                    PrintError.NONE -> onDone()
                                    PrintError.NO_PRINTER_FOUND -> {
                                        warningDialogText.value = stringWarningNoPrinterFound
                                        warningDialogOpened.value = true
                                    }

                                    PrintError.PERMISSION_NOT_GRANTED -> {
                                        warningDialogText.value = stringWarningPermissionNotGranted
                                        warningDialogOpened.value = true
                                    }

                                    PrintError.GENERIC_ERROR -> {
                                        warningDialogText.value = stringWarningPrinterGeneric
                                        warningDialogOpened.value = true
                                    }
                                }
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.print),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            WarningAlertDialog (
                text = warningDialogText.value,
                opened = warningDialogOpened.value,
                onDismissRequest = {
                    warningDialogOpened.value = false
                }
            )
        }
    }
}

@Composable
fun PrintPreviewCanvas(
    modifier: Modifier = Modifier,
    bitmap: Bitmap? = null,
    bgLightColor: Color = Color.hsl(0f, 0f, 0.4f),
    bgDarkColor: Color = Color.hsl(0f, 0f, 0.5f),
    tileSize: Dp = 4.dp
) {

    val tileSizePx = with(LocalDensity.current) { tileSize.toPx() }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (x in 0 until ceil(canvasWidth / tileSizePx).toInt()) {
            for (y in 0 until ceil(canvasHeight / tileSizePx).toInt()) {
                val isDarkTile = (x + y) % 2 == 1
                drawRect(
                    color = if (isDarkTile) bgDarkColor else bgLightColor,
                    topLeft = Offset(x * tileSizePx, y * tileSizePx),
                    size = Size(tileSizePx, tileSizePx)
                )
            }
        }

        if(bitmap != null) {
            val canvasAspectRatio = canvasWidth / canvasHeight
            val originalBitmapAspectRatio =
                bitmap.width.toFloat() / bitmap.height.toFloat()

            // We want the bitmap to fit the 90% of the longest edge by default
            var scaleCorrection = 0.9f

            if (canvasAspectRatio > originalBitmapAspectRatio) {
                scaleCorrection *= canvasHeight / bitmap.height
            } else {
                scaleCorrection *= canvasWidth / bitmap.width
            }

            withTransform({
                scale(scaleCorrection, scaleCorrection) // 2. Scale the image up/down (pivot center)
                translate(
                    -bitmap.width / 2f + canvasWidth / 2f,
                    -bitmap.height / 2f + canvasHeight / 2f
                ) // 1. Centers the image
            }) {
                // drawImage(imageBitmap.asImageBitmap())
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply {
                        isFilterBitmap = false // disables bilinear filtering
                    }
                    canvas.nativeCanvas.drawBitmap(bitmap, 0f, 0f, paint)
                }
            }
        }
    }
}

@Composable
fun IntegerControl(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 1..10
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Decrease button
            IconButton(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .padding(0.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(if (value > valueRange.first) 1f else 0.38f),
                onClick = { if (value > valueRange.first) onValueChange(value - 1) },
                enabled = value > valueRange.first,
            ) {
                Icon(
                    painterResource(R.drawable.material_symbols_outlined_remove),
                    contentDescription = "Decrease",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Value display
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .width(40.dp)
                    .wrapContentWidth(),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Increase button
            IconButton(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .padding(0.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(if (value < valueRange.last) 1f else 0.38f),
                onClick = { if (value < valueRange.last) onValueChange(value + 1) },
                enabled = value < valueRange.last
            ) {
                Icon(
                    painterResource(R.drawable.material_symbols_outlined_add),
                    contentDescription = "Increase",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionControl(
    modifier: Modifier = Modifier,
    label: String,
    selectedOption: String?,
    options: SortedMap<String, String>,
    onOptionSelected: (String) -> Unit,
    noAvailableOptionsText: String = "" // What to display if options is empty
) {
    var showDialog by remember { mutableStateOf(false) }

    // For ripple touch effect
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true)
            ) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var displayText = ""
            if(options.isEmpty()) {
                displayText = noAvailableOptionsText
            }
            if (selectedOption != null && options.containsKey(selectedOption)) {
                displayText = options.getValue(selectedOption)
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if(options.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "Show options",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showDialog && options.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showDialog = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 8.dp)
                )

                options.forEach { option ->
                    // For ripple touch effect
                    val optionInteractionSource = remember { MutableInteractionSource() }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = optionInteractionSource,
                                indication = ripple(bounded = true)
                            ) {
                                onOptionSelected(option.key)
                                showDialog = false
                            }
                            .padding(
                                horizontal = 32.dp,
                                vertical = 16.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = option.value,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (option.key == selectedOption) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderControl(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)

    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium
        )

        val sliderColors = SliderDefaults.colors(
            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )

        Slider(
            value = value,
            onValueChange = onValueChange,
            thumb = {
                val interactionSource = remember { MutableInteractionSource() }

                SliderDefaults.Thumb(
                    modifier = Modifier
                        .size(width = 20.dp, height = 20.dp),
                    interactionSource = interactionSource
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    modifier = Modifier.height(6.dp),
                    sliderState = sliderState,
                    colors = sliderColors,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = {
                        // Do nothing, don't draw the stop indicator
                    }
                )
            }
        )
    }
}

@Composable
fun Info(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarningAlertDialog(
        modifier: Modifier = Modifier,
        text: String,
        opened: Boolean = false,
        onDismissRequest: () -> Unit,
) {
    if(opened) {
        BasicAlertDialog(
            modifier = modifier,
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            )
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.warning),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = "Warning",
                            tint = AlertDialogDefaults.iconContentColor
                        )
                    }
                    Text(
                        text = text,
                    )
                    TextButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AlertDialogDefaults.textContentColor
                        )
                    }
                }
            }
        }
    }
}