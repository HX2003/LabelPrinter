package com.hx2003.labelprinter

import android.graphics.Bitmap
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.graphics.get
import com.hx2003.labelprinter.utils.MyResult
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import kotlin.experimental.and

enum class LabelSize (val size: Int, val pixels: Int) {
    MM6(6, 32),
    MM9(9, 48),
    MM12(12, 64),
    MM18(24, 96),
    MM24(24, 128),
    UNKNOWN(0, 64) // We set this to 64 pixels, so that at least something can be rendered
}

data class PrinterQueryValue (
    val labelSize: LabelSize = LabelSize.MM12
)

class PrinterDeviceConnection(
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice) {
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbEndpointIn: UsbEndpoint? = null
    private var usbEndpointOut: UsbEndpoint? = null

    private val writeTimeout = 1000
    private val readTimeout = 1000

    private val tag = "PrinterDeviceConnection"

    private var open = false

    fun open(): Boolean {
        val interfaceCount = usbDevice.interfaceCount
        if (usbDevice.interfaceCount != 1) {
            Log.w(tag, "interfaceCount != 1, got $interfaceCount instead")
            return false
        }

        val usbInterface = usbDevice.getInterface(0)

        val endpointCount = usbInterface.endpointCount
        if (endpointCount != 2) {
            Log.w(tag, "endpointCount != 2, got $endpointCount instead")
            return false
        }

        for (i in 0 until endpointCount) {
            val endpoint = usbInterface.getEndpoint(i)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (endpoint.direction == UsbConstants.USB_DIR_IN) {
                    usbEndpointIn = endpoint
                } else {
                    usbEndpointOut = endpoint
                }
            }
        }

        if(usbEndpointIn == null) {
            Log.w(tag, "usbEndpointIn is null")
            return false
        }

        if(usbEndpointOut == null) {
            Log.w(tag, "usbEndpointOut is null")
            return false
        }

        usbDeviceConnection = usbManager.openDevice(usbDevice)

        if(usbDeviceConnection == null) {
            Log.w(tag, "failed to open usb device, usbDeviceConnection is null")
            return false
        }

        if(!usbDeviceConnection!!.claimInterface(usbInterface, true)) {
            Log.w(tag, "failed to claim usb device, usbDeviceConnection is null")
            return false
        }

        open = true

        return true
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    suspend fun query(): MyResult<PrinterQueryValue, PrinterError> {
        if(!open) {
            error("you forgot to call open()")
        }

        val buffer = ByteArray(32)

        try {
            bulkWrite(ubyteArrayOf(0x1Bu, 0x69u, 0x53u).asByteArray())
            bulkRead(buffer)
        } catch(e: Exception) {
            Log.w(tag, "query bulk write/ bulk read error, exception: $e")
            return MyResult.HasError(PrinterError.GENERIC_ERROR)
        }

        if(buffer[0].toUInt() != 0x80u && buffer[1].toUInt() != 0x20u) {
            Log.w(tag, "header is not valid")
            return MyResult.HasError(PrinterError.GENERIC_ERROR)
        }

        val errorInfoRaw1 = buffer[8]
        if((errorInfoRaw1 and 0b111) > 0) {
            // bit 0 is "no media" error
            // bit 1 is "end of media" error
            // bit 2 it "tape cutter jam" error
            Log.w(tag, "printer error byte 1 has error: $errorInfoRaw1")
            return MyResult.HasError(PrinterError.GENERIC_ERROR)
        }

        val errorInfoRaw2 = buffer[9]
        if((errorInfoRaw2 and 0b11111) > 0) {
            // bit 0 is "replace the media" error
            // bit 1 is "expansion buffer is full" error
            // bit 2 is "transmission" error
            // bit 3 is "transmission buffer full" error
            // bit 4 is "cover is open" error
            Log.w(tag, "printer error byte 2 has error: $errorInfoRaw2")
            return MyResult.HasError(PrinterError.GENERIC_ERROR)
        }

        val labelSizeRaw = buffer[10].toUInt()
        val labelSize = when (labelSizeRaw) {
            6u -> LabelSize.MM6
            9u -> LabelSize.MM9
            12u -> LabelSize.MM12
            18u -> LabelSize.MM18
            24u -> LabelSize.MM24
            else -> {
                Log.w(tag, "invalid label size: $labelSizeRaw, only 6, 9, 12, 18, 24 are supported")
                // labelSizeRaw is also equal to 0, if the lid is removed
                // Instead of returning the value LabelSize.UNKNOWN, we return an error code
                return MyResult.HasError(PrinterError.LABEL_SIZE_UNKNOWN)
            }
        }

        return MyResult.NoError(PrinterQueryValue(
            labelSize = labelSize
        ))
    }

    suspend fun print(config: PrintConfigTransformed): MyResult<Unit, PrinterError> {
        if(!open) {
            error("you forgot to call open()")
        }

        try {
            clearBuffers()

            for(i in 0 until config.numCopies) {
                val firstPage = (i == 0)
                val lastPage = (i == (config.numCopies - 1))

                setRasterMode()
                setPrintInformation(
                    validFlags = 0x84u,
                    labelSize = config.labelSize.size.toUByte(),
                    labelLength = 0x00u,
                    numRasterLines = config.bitmap!!.width.toUInt(),
                    labelType = 0x00u,
                    firstPage = firstPage
                )
                setEachMode(autocut = true, mirrorPrinting = false)
                setMargin(dots = 20u) // small margin around 3mm
                setCompressionMode()
                sendRasterGraphics(config.bitmap!!, lastPage)

                Log.i("sent out", "sent out")
                delay(7000)
                Log.i("next =", "next t")
            }
        } catch(e: Exception) {
            Log.w(tag, "print bulk write error, exception: $e")
            return MyResult.HasError(PrinterError.GENERIC_ERROR)
        }

        return MyResult.NoError(Unit)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun clearBuffers() {
        bulkWrite(ubyteArrayOf(0x1Bu, 0x40u).asByteArray()) // initialize and clear buffers
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun setNotifyMode(notify: Boolean) {
        bulkWrite(ubyteArrayOf(
            0x1Bu,
            0x69u,
            if (notify) 0x00u else 0x01u
        ).asByteArray()) // initialize and clear buffers
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun setEachMode(autocut: Boolean, mirrorPrinting: Boolean) {
        val bytes = ubyteArrayOf(0x1Bu, 0x69u, 0x4Du).asByteArray()

        val autocutBit = if (autocut) 1 else 0
        val mirrorPrintingBit = if (mirrorPrinting) 1 else 0

        val byte = ((autocutBit shl 6) or (mirrorPrintingBit shl 7)).toByte()
        bulkWrite(bytes + byte)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun setMargin(dots: UInt) {
        val bytes = ubyteArrayOf(
            0x1Bu,
            0x69u,
            0x64u,
            (dots and 0xFFu).toUByte(),
            ((dots shr 8) and 0xFFu).toUByte()
        ).asByteArray()

        bulkWrite(bytes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun setCompressionMode() {
        val bytes = ubyteArrayOf(0x4Du, 0x00u).asByteArray()
        bulkWrite(bytes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun setRasterMode() {
        val bytes = ubyteArrayOf(0x1Bu, 0x69u, 0x61u, 0x01u).asByteArray()

        // differences between datasheets?
        // val bytes = ubyteArrayOf(0x1Bu, 0x69u, 0x52u, 0x01u).asByteArray()

        bulkWrite(bytes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun sendRasterGraphics(bitmap: Bitmap, lastPage: Boolean) {
        // Our printer head has 128 dots per raster line (vertical line)
        val pixelsPerRasterLine = 128

        // The label is vertically centered,
        // if the image is 128 pixels, then we send the full 128 pixels
        // if the image is 96 pixels tall, then we send 16 pixels + 96 pixels (+ 16 pixels optional)
        // if the image is 64 pixels tall, then we send 32 pixels + 64 pixels (+ 32 pixels optional)
        // if the image is 48 pixels tall, then we send 40 pixels + 32 pixels (+ 40 pixels optional)
        // if the image is 32 pixels tall, then we send 48 pixels + 32 pixels (+ 48 pixels optional)

        val numBytesPadding = ((pixelsPerRasterLine - bitmap.height) / 2) / 8
        val numBytesPerRasterLine = numBytesPadding + bitmap.height / 8
        val startRasterLineHeader: ByteArray =
            ubyteArrayOf(0x47u, numBytesPerRasterLine.toUByte(), 0x00u).toByteArray()

        val stream = ByteArrayOutputStream()
        for(x in 0 until bitmap.width) {
            stream.write(startRasterLineHeader)

            repeat( numBytesPadding) {
                stream.write(0)
            }

            for(j in 0 until bitmap.height/8) {
                var value = 0
                for(k in 0 until 8) {
                    value = value or ((bitmap[x, j*8 + k] and 0b1) shl (7 - k))
                }
                stream.write(value.inv())
            }
        }

        if(lastPage) {
            // print command with feeding
            stream.write(0x1a)
        } else {
            // print command
            stream.write(0x0c)
        }

        bulkWrite(stream.toByteArray())
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun setPrintInformation(validFlags: UByte, labelType: UByte, labelSize: UByte, labelLength: UByte, numRasterLines: UInt, firstPage: Boolean) {
        val bytes = ubyteArrayOf(
            0x1Bu,
            0x69u,
            0x7Au,
            validFlags,
            labelType,
            labelSize,
            labelLength,
            (numRasterLines and 0xFFu).toUByte(),
            ((numRasterLines shr 8) and 0xFFu).toUByte(),
            ((numRasterLines shr 16) and 0xFFu).toUByte(),
            ((numRasterLines shr 24) and 0xFFu).toUByte(),
            if (firstPage) 0x00u else 0x01u,
            0x00u
            ).asByteArray()

        bulkWrite(bytes)
    }
    fun close() {
        usbDeviceConnection?.close()
    }

    private suspend fun bulkRead(data: ByteArray): Int {
        delay(10)

        val readLen = usbDeviceConnection!!.bulkTransfer(usbEndpointIn, data, data.size, readTimeout)
        if (readLen <= 0) {
            error("bulkRead failed")
        }
        return readLen
    }

    // With support for >16384 byte arrays
    private suspend fun bulkWrite(data: ByteArray): Int {
        delay(10)

        var offset = 0
        while (offset < data.size) {
            val remaining = data.size - offset
            val writeSize = minOf(16384, remaining)
            val writeLen = usbDeviceConnection!!.bulkTransfer(usbEndpointOut, data, offset,writeSize, writeTimeout)
            if (writeLen <= 0) {
                error("bulkWrite failed")
            }
            offset += writeLen
        }
        return offset
    }
}