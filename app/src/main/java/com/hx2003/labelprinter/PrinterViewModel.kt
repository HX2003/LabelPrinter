package com.hx2003.labelprinter

import android.graphics.Rect
import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CropViewInternalState(
    var uri: Uri? = null,
    var cropRect: Rect? = null,
    var rotatedDegrees: Int = 0,
    var isFlippedHorizontally: Boolean = false,
    var isFlippedVertically: Boolean = false,
)

data class CropViewReactiveState(
    var uri: Uri? = null
)

class PrinterViewModel(
    private val usbController: USBController
) : ViewModel() {
    private val _cropViewReactiveState = MutableStateFlow(CropViewReactiveState())
    val cropViewReactiveState = _cropViewReactiveState.asStateFlow()

    // We shall manually update the cropViewInternalState whenever CropImageView is updated
    // This is so that we can restore CropImageView to the same config after a print
    var cropViewInternalState = CropViewInternalState()

    fun setCropViewUri(uri: Uri?) {
        _cropViewReactiveState.update {
            it.copy(uri = uri)
        }
    }

    var str: Int
        get() = usbController.str
        set(value) {
            usbController.str = value
        }
}