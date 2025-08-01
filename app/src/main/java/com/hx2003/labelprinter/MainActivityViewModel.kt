package com.hx2003.labelprinter

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.hx2003.labelprinter.utils.transformBitmap
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class CropViewState(
    var uri: Uri? = null,
    var uriLoaded: Boolean = false
)

enum class DitherOption {
    NONE,
    CLASSIC
}

data class PrintConfig (
    val numCopies: Int = 1,
    var dither: DitherOption = DitherOption.NONE,
    var colorThreshold: Float = 0.5f,
    var bitmap: Bitmap? = null
)

data class PrintConfigTransformed (
    val numCopies: Int = 1,
    var dither: DitherOption = DitherOption.CLASSIC,
    var colorThreshold: Float = 0.5f,
    var bitmap: Bitmap? = null,
    // Added labelSize
    var labelSize: LabelSize = LabelSize.MM12,
)

class MainActivityViewModel(
    private val printerDevicesManager: PrinterDevicesManager
) : ViewModel() {
    private val _cropViewState = MutableStateFlow(CropViewState())
    val cropViewState = _cropViewState.asStateFlow()

    val printerState = printerDevicesManager.printerState

    private val _printConfig = MutableStateFlow(PrintConfig())
    val printConfig = _printConfig.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val printConfigTransformed: StateFlow<PrintConfigTransformed?> = printConfig
        .combine(
            printerState
        ) {
            printConfig, printerState ->
            val numCopies = printConfig.numCopies
            val dither = printConfig.dither
            val colorThreshold = printConfig.colorThreshold

            val queryResult = printerState.queryResult
            val labelSize = when (queryResult) {
                is QueryCommandResult.Success -> {
                    queryResult.data.labelSize
                }
                is QueryCommandResult.CommunicationError -> {
                    LabelSize.UNKNOWN
                }
                is QueryCommandResult.DeviceError -> {
                    LabelSize.UNKNOWN
                }
                null -> {
                    LabelSize.UNKNOWN
                }
            }

            // Basically, whenever printConfig or printerState changes
            // we make a new printConfigTransformed with the transformed bitmap
            PrintConfigTransformed(
                numCopies = numCopies,
                dither = dither,
                colorThreshold = colorThreshold,
                bitmap = transformBitmap(
                    sourceBitmap = printConfig.bitmap,
                    dither = dither,
                    colorThreshold = colorThreshold,
                    labelSize = labelSize
                ),
                labelSize = labelSize
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = null
        )

    fun setCropViewUri(uri: Uri?) {
        _cropViewState.update {
            it.copy(uri = uri)
        }
    }
    fun setUriLoadedFlag() {
        _cropViewState.update {
            it.copy(uriLoaded = true)
        }
    }

    fun setCroppedBitmap(bitmap: Bitmap?) {
        _printConfig.update {
            it.copy(bitmap = bitmap)
        }
    }

    fun setDither(dither: DitherOption) {
        _printConfig.update {
            it.copy(dither = dither)
        }
    }

    fun setColorThreshold(colorThreshold: Float) {
        _printConfig.update {
            it.copy(colorThreshold = colorThreshold)
        }
    }

    fun setNumCopies(numCopies: Int) {
        _printConfig.update {
            it.copy(numCopies = numCopies)
        }
    }

    fun setSelectedPrinter(newSelectedPrinter: String) {
        printerDevicesManager.setSelectedPrinter(newSelectedPrinter)
    }

    fun clearPrintStatusAndPrintRequestResult() {
        printerDevicesManager.clearPrintStatusAndPrintRequestResult()
    }

    suspend fun requestPermissionAndConnect() {
        viewModelScope.async {
            printerDevicesManager.requestPermissionAndConnect()
        }.await()
    }

    suspend fun queryPrinter() {
        viewModelScope.async {
            printerDevicesManager.query()
        }.await()
    }

    fun print() {
        viewModelScope.launch {
            printerDevicesManager.print(printConfigTransformed.value)
        }
    }


    /*var str: Int
        get() = printerUsbController.str
        set(value) {
            printerUsbController.str = value
        }*/
}