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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.hx2003.labelprinter.utils.transformBitmap

data class CropViewState(
    var uri: Uri? = null,
    var uriLoaded: Boolean = false
)

enum class DitherOption {
    NONE,
    CLASSIC
}

enum class LabelSizeOption (val size: Int, val pixels: Int) {
    MM6(6, 32),
    MM9(9, 48),
    MM12(12, 64),
    MM18(24, 96),
    MM24(24, 128)
}

data class BitmapState (
    var labelWidth: LabelSizeOption = LabelSizeOption.MM12,
    var dither: DitherOption = DitherOption.CLASSIC,
    var colorThreshold: Float = 0.5f,
    var bitmap: Bitmap? = null
)

data class PrintReactiveState (
    var numCopies: Int = 1
)

class PrinterApplicationViewModel(
    private val printerUsbController: PrinterUsbController
) : ViewModel() {
    private val _cropViewState = MutableStateFlow(CropViewState())
    val cropViewState = _cropViewState.asStateFlow()

    private val _printReactiveState = MutableStateFlow(PrintReactiveState())
    val printConfigReactiveState = _printReactiveState.asStateFlow()

    private val _bitmapState = MutableStateFlow(BitmapState())
    val bitmapState = _bitmapState.asStateFlow()

    val printerUsbState = printerUsbController.printerUsbState

    @OptIn(ExperimentalCoroutinesApi::class)
    val transformedBitmapState: StateFlow<BitmapState?> = bitmapState
        .mapLatest { bitmapState ->
            // Basically, whenever bitmapState changes
            // we make a new bitmapState with the transformed bitmap
            bitmapState.copy(
                bitmap = transformBitmap(bitmapState = bitmapState)
            )
        }
        .stateIn(
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
        _bitmapState.update {
            it.copy(bitmap = bitmap)
        }
    }

    fun setDither(dither: DitherOption) {
        _bitmapState.update {
            it.copy(dither = dither)
        }
    }

    fun setColorThreshold(colorThreshold: Float) {
        _bitmapState.update {
            it.copy(colorThreshold = colorThreshold)
        }
    }

    fun setNumCopies(numCopies: Int) {
        _printReactiveState.update {
            it.copy(numCopies = numCopies)
        }
    }

    fun setSelectedPrinter(newSelectedPrinter: String) {
        printerUsbController.setSelectedPrinter(newSelectedPrinter)
    }

    suspend fun print(): PrintError {
        return printerUsbController.print()
    }

    suspend fun requestPermission(): PrintError {
        return printerUsbController.requestPermission()
    }

    /*var str: Int
        get() = printerUsbController.str
        set(value) {
            printerUsbController.str = value
        }*/
}