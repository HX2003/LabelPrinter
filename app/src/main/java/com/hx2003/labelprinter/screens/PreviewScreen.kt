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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hx2003.labelprinter.DitherOption
import com.hx2003.labelprinter.LabelSize
import com.hx2003.labelprinter.MainActivityViewModel
import com.hx2003.labelprinter.PrintCommandResult
import com.hx2003.labelprinter.PrintStatus
import com.hx2003.labelprinter.PrintStatusError
import com.hx2003.labelprinter.PrinterCommunicationError
import com.hx2003.labelprinter.R
import kotlinx.coroutines.delay
import java.util.SortedMap
import kotlin.math.ceil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    onDone: () -> Unit = {},
    onBack: () -> Unit = {},
    mainActivityViewModel: MainActivityViewModel
) {
    val printConfig by mainActivityViewModel.printConfig.collectAsStateWithLifecycle()
    val printConfigTransformed by mainActivityViewModel.printConfigTransformed.collectAsStateWithLifecycle()
    val printerState by mainActivityViewModel.printerState.collectAsStateWithLifecycle()

    var warningDialogOpened = false
    var warningDialogText: String? = null

    val printingInProgress = when(printerState.printStatus) {
        PrintStatus.NOT_STARTED -> false
        PrintStatus.IN_PROGRESS -> true
        PrintStatus.COMPLETED -> false
    }

    val printResult = printerState.printResult
    when (printResult) {
        is PrintCommandResult.Success -> {
            onDone()
        }
        is PrintCommandResult.CommunicationError -> {
            when (printResult.error) {
                PrinterCommunicationError.NO_PRINTER_ERROR -> {
                    warningDialogText = stringResource(R.string.no_printer_error_description)
                    warningDialogOpened = true
                }
                PrinterCommunicationError.PERMISSION_ERROR -> {
                    warningDialogText = stringResource(R.string.permission_error_description)
                    warningDialogOpened = true
                }
                PrinterCommunicationError.CONNECTION_NULL_ERROR -> {
                    warningDialogText = stringResource(R.string.connection_null_error_description)
                    warningDialogOpened = true
                }
                PrinterCommunicationError.USB_SETUP_ERROR -> {
                    warningDialogText = stringResource(R.string.usb_setup_error_description)
                    warningDialogOpened = true
                }
                PrinterCommunicationError.TRANSFER_ERROR -> {
                    warningDialogText = stringResource(R.string.transfer_error_description)
                    warningDialogOpened = true
                }
                PrinterCommunicationError.PARSING_ERROR -> {
                    warningDialogText = stringResource(R.string.parsing_error_description)
                    warningDialogOpened = true
                }
                PrinterCommunicationError.GENERIC_ERROR -> {
                    warningDialogText = stringResource(R.string.generic_error_description)
                    warningDialogOpened = true
                }
            }
        }
        is PrintCommandResult.DeviceError -> {
            when (printResult.error) {
                PrintStatusError.CONFIG_NULL_ERROR -> {
                    warningDialogText = stringResource(R.string.config_null_error_description)
                    warningDialogOpened = true
                }
                PrintStatusError.LABEL_SIZE_UNKNOWN_ERROR -> {
                    warningDialogText = stringResource(R.string.label_size_unknown_error_description)
                    warningDialogOpened = true
                }
                PrintStatusError.LABEL_SIZE_MISMATCH_ERROR -> {
                    warningDialogText = stringResource(R.string.label_size_mismatch_error_description)
                    warningDialogOpened = true
                }
                PrintStatusError.DEVICE_ERROR -> {
                    warningDialogText = stringResource(R.string.device_error_description)
                    warningDialogOpened = true
                }
            }
        }
        null -> {}
    }

    var labelSizeText = stringResource(R.string.unknown)
    printConfigTransformed.let {
        if (it != null) {
            labelSizeText = it.labelSize.size.toString() + "mm"
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                // Heartbeat
                // Periodically query the connected printer for its' status
                // This is necessary, as we printer's status influences the UI
                // Tied to PreviewScreen, so it will not waste user's battery by querying the printer in the background
                delay(333)
                mainActivityViewModel.queryPrinter()
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
                    val transformBitmap = printConfigTransformed?.bitmap
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
                        selectedOption = printerState.selectedPrinter,
                        options = printerState.availablePrinters.mapValues { (_, value) ->
                            value.productName ?: ""
                        }.toSortedMap(),
                        onOptionSelected = { selected ->
                                mainActivityViewModel.setSelectedPrinter(selected)
                                mainActivityViewModel.requestPermissionAndConnect()
                                mainActivityViewModel.queryPrinter()
                        },
                        noAvailableOptionsText = stringResource(R.string.no_printer_found),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    IntegerControl(
                        label = stringResource(R.string.copies),
                        value = printConfig.numCopies,
                        onValueChange = {
                            mainActivityViewModel.setNumCopies(it)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OptionControl(
                        label = stringResource(R.string.dither),
                        selectedOption = printConfig.dither.toString(),
                        options = sortedMapOf(
                            Pair(DitherOption.NONE.toString(), stringResource(R.string.none)),
                            Pair(DitherOption.CLASSIC.toString(),stringResource(R.string.classic))
                        ),
                        onOptionSelected = {
                            selected -> mainActivityViewModel.setDither(DitherOption.valueOf(selected))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if(printConfig.dither == DitherOption.NONE) {
                        SliderControl(
                            label = stringResource(R.string.threshold),
                            value = printConfig.colorThreshold,
                            onValueChange = {
                                mainActivityViewModel.setColorThreshold(it)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Info(
                        label = stringResource(R.string.label_size),
                        value = labelSizeText,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier.padding( 16.dp)
                ) {
                    val alpha = if (printerState.availablePrinters.isNotEmpty() && printConfigTransformed != null && printConfigTransformed?.labelSize != LabelSize.UNKNOWN)  {
                        1.0f
                    } else {
                        0.3f
                    }
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .alpha(alpha),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        onClick = {
                            // note requestPermissionAndConnect() actually causes a reconnection
                            // if already previously connected, this is not ideal but...
                            mainActivityViewModel.requestPermissionAndConnect()
                            mainActivityViewModel.print()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.print),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Displays any error messages after print() has completed
            PrintingStatusDialog(
                titleText =
                    if(printingInProgress) stringResource(R.string.printing) else stringResource(R.string.warning),
                warnText = warningDialogText,
                showProgressBar = printingInProgress,
                showDismissButton = !printingInProgress,
                opened = printingInProgress || warningDialogOpened,
                onDismissRequest = {
                    mainActivityViewModel.clearPrintStatusAndPrintRequestResult()
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

            scaleCorrection *= if (canvasAspectRatio > originalBitmapAspectRatio) {
                canvasHeight / bitmap.height
            } else {
                canvasWidth / bitmap.width
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
fun PrintingStatusDialog(
        modifier: Modifier = Modifier,
        titleText: String,
        warnText: String?,
        showProgressBar: Boolean,
        showDismissButton: Boolean,
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
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if(warnText != null) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = "Warning",
                                tint = AlertDialogDefaults.iconContentColor
                            )
                        }
                    }
                    if(warnText != null) {
                        Text(
                            text = warnText,
                        )
                    }
                    if(showProgressBar) {
                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )

                        LinearProgressIndicator (
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )

                        Spacer(
                            modifier = Modifier.height(4.dp)
                        )
                    }
                    if(showDismissButton) {
                        TextButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = stringResource(R.string.dismiss),
                                style = MaterialTheme.typography.bodyLarge,
                                color = AlertDialogDefaults.textContentColor
                            )
                        }
                    }
                }
            }
        }
    }
}