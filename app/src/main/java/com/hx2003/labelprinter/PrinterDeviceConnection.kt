package com.hx2003.labelprinter

import android.graphics.Bitmap
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.util.Log
import androidx.core.graphics.get
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.time.DurationUnit
import kotlin.time.TimeSource.Monotonic.markNow
import kotlin.time.toDuration

/**
 * Represents possible error types that can occur during printer communication,
 * it does not include errors reported by the printer
 */
enum class PrinterCommunicationError {
    NO_PRINTER_ERROR, // There is no available printer
    PERMISSION_ERROR, // Permission was not granted to access the printer
    CONNECTION_NULL_ERROR, // DeviceConnection is null
    USB_SETUP_ERROR,  // Error setting up the usb connection
    TRANSFER_ERROR,   // USB bulkTransfer (read or write) error
    PARSING_ERROR,    // The received data has unexpected values
    GENERIC_ERROR     // Any other errors
    // NONE,          // No error occurred, communication was successful
}

/**
 * Represents possible error types that can occur during a print.
 * excluding communication error
 */
enum class PrintStatusError {
    CONFIG_NULL_ERROR,
    LABEL_SIZE_UNKNOWN_ERROR,
    LABEL_SIZE_MISMATCH_ERROR,
    DEVICE_ERROR // device is in a invalid state as reported by query status
}

enum class LabelSize (val size: Int, val pixels: Int) {
    MM6(6, 32),
    MM9(9, 48),
    MM12(12, 64),
    MM18(24, 96),
    MM24(24, 128),
    UNKNOWN(0, 64) // We set this to 64 pixels, so that at least something can be rendered
}
data class PrinterQueryValue(
    val labelSize: LabelSize = LabelSize.UNKNOWN,
    val status: Byte,
    val phaseType: Byte,
    val phase1: Byte,
    val phase2: Byte
)

data class PrinterQueryStatusError(
    val labelSize: LabelSize = LabelSize.UNKNOWN,
    val error1: Byte,
    val error2: Byte,
    val status: Byte,
    val phaseType: Byte,
    val phase1: Byte,
    val phase2: Byte
)

sealed interface OpenCommandResult {
    object Success: OpenCommandResult
    data class CommunicationError(val error: PrinterCommunicationError): OpenCommandResult
}

sealed interface QueryCommandResult {
    data class Success(val data: PrinterQueryValue): QueryCommandResult
    data class CommunicationError(val error: PrinterCommunicationError): QueryCommandResult
    data class DeviceError(val error: PrinterQueryStatusError): QueryCommandResult
}

sealed interface PrintCommandResult {
    object Success: PrintCommandResult
    data class CommunicationError(val error: PrinterCommunicationError): PrintCommandResult
    data class DeviceError(val error: PrintStatusError): PrintCommandResult
}

class PrinterDeviceConnection (
    private val usbManager: UsbManager,
    private val usbDevice: UsbDevice) {
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbEndpointIn: UsbEndpoint? = null
    private var usbEndpointOut: UsbEndpoint? = null

    private lateinit var usbWriteRequest: UsbRequest

    private lateinit var usbReadRequest: UsbRequest

    private val writeTimeout = 500
    private val readTimeout = 500

    // @TODO For very very long labels, this may be insufficient
    private val printTimeout = 7500
    private val printLastTimeout = 15000 // give extra time for the last label, since it needs to cut extra
    private val tag = "PrinterDeviceConnection"

    private var open = false

    fun open(): OpenCommandResult {
        try {
            val interfaceCount = usbDevice.interfaceCount
            if (usbDevice.interfaceCount != 1) {
                error("interfaceCount != 1, got $interfaceCount instead")
            }

            val usbInterface = usbDevice.getInterface(0)

            val endpointCount = usbInterface.endpointCount
            if (endpointCount != 2) {
                error("endpointCount != 2, got $endpointCount instead")
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

            if (usbEndpointIn == null) {
                error("usbEndpointIn is null")
            }

            if (usbEndpointOut == null) {
                error("usbEndpointOut is null")
            }

            usbDeviceConnection = usbManager.openDevice(usbDevice)

            if (usbDeviceConnection == null) {
                error("failed to open usb device, usbDeviceConnection is null")
            }

            if (!usbDeviceConnection!!.claimInterface(usbInterface, true)) {
                error("failed to claim usb device, usbDeviceConnection is null")
            }

            usbReadRequest = UsbRequest()

            if(!usbReadRequest.initialize(usbDeviceConnection, usbEndpointIn)) {
                error("failed to initialize usbReadRequest")
            }

            usbWriteRequest = UsbRequest()

            if(!usbWriteRequest.initialize(usbDeviceConnection, usbEndpointOut)) {
                error("failed to initialize usbWriteRequest")
            }

        } catch (e: Exception) {
            Log.w(tag, "open() error: $e")
            return OpenCommandResult.CommunicationError(PrinterCommunicationError.USB_SETUP_ERROR)
        }

        open = true

        return OpenCommandResult.Success
    }

    /**
     * Sends a query request for the selected printer
     * Note: The selected printer must already be connected via requestPermissionAndConnect()
     *
     * @param request When request is set to true, we manually request for the printer's status.
     *
     * @return QueryCommandResult
     **/

    @OptIn(ExperimentalUnsignedTypes::class)
    fun query(request: Boolean = true): QueryCommandResult {
        if (!open) {
            Log.w(tag, "either you forgot to call open(), or open() was previously not successful")
            return QueryCommandResult.CommunicationError(PrinterCommunicationError.USB_SETUP_ERROR)
        }

        val buffer = ByteArray(32)

        if (request) {
            try {
                bulkWrite(ubyteArrayOf(0x1Bu, 0x69u, 0x53u).asByteArray())
            } catch (e: Exception) {
                Log.w(tag, "query bulk write error, exception: $e")
                return QueryCommandResult.CommunicationError(PrinterCommunicationError.TRANSFER_ERROR)
            }
        }

        try {
            bulkRead(buffer)
        } catch (e: Exception) {
            Log.w(tag, "query bulk read error, exception: $e")
            return QueryCommandResult.CommunicationError(PrinterCommunicationError.TRANSFER_ERROR)
        }

        if (buffer[0].toUInt() != 0x80u && buffer[1].toUInt() != 0x20u) {
            Log.w(tag, "header is not valid")
            return QueryCommandResult.CommunicationError(PrinterCommunicationError.PARSING_ERROR)
        }

        val labelSizeRaw = buffer[10].toUInt()
        val labelSize = when (labelSizeRaw) {
            0u -> LabelSize.UNKNOWN // equal to 0, if no label installed / the lid is removed
            6u -> LabelSize.MM6
            9u -> LabelSize.MM9
            12u -> LabelSize.MM12
            18u -> LabelSize.MM18
            24u -> LabelSize.MM24
            else -> {
                Log.w(tag, "invalid label size: $labelSizeRaw, only 6, 9, 12, 18, 24 are supported")
                return QueryCommandResult.CommunicationError(PrinterCommunicationError.PARSING_ERROR)
            }
        }

        var hasDeviceErrorFlag = false

        val error1 = buffer[8]
        if (error1 > 0) {
            // bit 0 is "no media" error
            // bit 1 is "end of media" error
            // bit 2 is "tape cutter jam" error
            // bit 3 is "weak battery" error
            // bit 6 is "high voltage adapter" error
            Log.w(tag, "printer indicated error, byte 1: $error1")
            hasDeviceErrorFlag = true
        }

        val error2 = buffer[9]
        if (error2 > 0) {
            // bit 0 is "replace the media" error
            // bit 1 is "expansion buffer is full" error
            // bit 2 is "transmission" error
            // bit 3 is "transmission buffer full" error
            // bit 4 is "cover is open" error
            // bit 5 is "overheating" error
            Log.w(tag, "printer indicated error, byte 1: $error2")
            hasDeviceErrorFlag = true
        }

        val status = buffer[18]
        val phaseType = buffer[19]
        val phase1 = buffer[20]
        val phase2 = buffer[21]

        if (hasDeviceErrorFlag) {
            return QueryCommandResult.DeviceError(
                PrinterQueryStatusError(
                    labelSize = labelSize,
                    error1 = error1,
                    error2 = error2,
                    status = status,
                    phaseType = phaseType,
                    phase1 = phase1,
                    phase2 = phase2
                )
            )
        }
        return QueryCommandResult.Success(
            PrinterQueryValue(
                labelSize = labelSize,
                status = status,
                phaseType = phaseType,
                phase1 = phase1,
                phase2 = phase2
            )
        )
    }

    suspend fun print(config: PrintConfigTransformed): PrintCommandResult {
        if (!open) {
            Log.w(tag, "either you forgot to call open(), or open() was previously not successful")
            return PrintCommandResult.CommunicationError(PrinterCommunicationError.USB_SETUP_ERROR)
        }

        // query the printer once again for its latest status,
        // to make sure nothing changed
        val queryResult = query()

        when (queryResult) {
            is QueryCommandResult.Success -> {
                val actual = queryResult.data.labelSize
                val expected = config.labelSize

                if (actual != expected) {
                    Log.w(tag, "label size mismatch got $actual, expected $expected")
                    return PrintCommandResult.DeviceError(PrintStatusError.LABEL_SIZE_MISMATCH_ERROR)
                }
            }

            is QueryCommandResult.CommunicationError -> {
                Log.w(tag, "query CommunicationError")
                return PrintCommandResult.CommunicationError(queryResult.error)
            }

            is QueryCommandResult.DeviceError -> {
                Log.w(tag, "query DeviceError")
                return PrintCommandResult.DeviceError(PrintStatusError.DEVICE_ERROR)
            }
        }

        try {
            clearBuffers()
        } catch (e: Exception) {
            Log.w(tag, "print bulk write error when clearing buffers, exception: $e")
            return PrintCommandResult.CommunicationError(PrinterCommunicationError.TRANSFER_ERROR)
        }

        for (i in 0 until config.numCopies) {
            val firstPage = (i == 0)
            val lastPage = (i == (config.numCopies - 1))

            try {
                setNotifyMode(notify = true)
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
            } catch (e: Exception) {
                Log.w(tag, "print bulk write error, exception: $e")
                return PrintCommandResult.CommunicationError(PrinterCommunicationError.TRANSFER_ERROR)
            }

            // since we set notify mode to be true
            // we do not need to send query request (in fact usb write must not be allowed during printing according to the manufacturer)
            // the printer will notify us when the printer status changes
            //
            // we will keep polling to check whether there are any status messages until the timeout
            val printTimeoutDuration = when (lastPage) {
                false -> printTimeout.toDuration(DurationUnit.MILLISECONDS)
                true -> printLastTimeout.toDuration(DurationUnit.MILLISECONDS)
            }
            val mark = markNow()
            var printCompleted = false
            Log.i(tag, "polling for status, query bulk read errors are expected")
            while(!printCompleted && (mark.elapsedNow() < printTimeoutDuration)) {
                delay(250)

                val queryResult = query(request = false)

                when (queryResult) {
                    is QueryCommandResult.CommunicationError -> {
                        // do nothing, it probably timed out, which is normal
                        // warnings will appear in the log
                        // we will try again later
                    }

                    is QueryCommandResult.DeviceError -> {
                        // the printer reported an error,
                        // we should stop printing
                        return PrintCommandResult.CommunicationError(PrinterCommunicationError.TRANSFER_ERROR)
                    }

                    is QueryCommandResult.Success -> {
                        Log.i("queryResult", queryResult.data.toString())
                        Log.i("queryResult status", queryResult.data.status.toString())
                        Log.i("queryResult phase type", queryResult.data.phaseType.toString())
                        if(queryResult.data.phaseType.toInt() == 0) {
                            printCompleted = true
                        }
                    }
                }
            }

            if (!printCompleted) {
                Log.w(tag, "timeout waiting for printer status")
                return PrintCommandResult.CommunicationError(PrinterCommunicationError.TRANSFER_ERROR)
            }
        }

        return PrintCommandResult.Success
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun clearBuffers() {
        bulkWrite(ubyteArrayOf(0x1Bu, 0x40u).asByteArray()) // initialize and clear buffers
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun setNotifyMode(notify: Boolean) {
        bulkWrite(ubyteArrayOf(
            0x1Bu,
            0x69u,
            if (notify) 0x00u else 0x01u
        ).asByteArray()) // initialize and clear buffers
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun setEachMode(autocut: Boolean, mirrorPrinting: Boolean) {
        val bytes = ubyteArrayOf(0x1Bu, 0x69u, 0x4Du).asByteArray()

        val autocutBit = if (autocut) 1 else 0
        val mirrorPrintingBit = if (mirrorPrinting) 1 else 0

        val byte = ((autocutBit shl 6) or (mirrorPrintingBit shl 7)).toByte()
        bulkWrite(bytes + byte)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun setMargin(dots: UInt) {
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
    private fun setCompressionMode() {
        val bytes = ubyteArrayOf(0x4Du, 0x00u).asByteArray()
        bulkWrite(bytes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun setRasterMode() {
        val bytes = ubyteArrayOf(0x1Bu, 0x69u, 0x61u, 0x01u).asByteArray()

        // differences between datasheets?
        // val bytes = ubyteArrayOf(0x1Bu, 0x69u, 0x52u, 0x01u).asByteArray()

        bulkWrite(bytes)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun sendRasterGraphics(bitmap: Bitmap, lastPage: Boolean) {
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
    private fun setPrintInformation(validFlags: UByte, labelType: UByte, labelSize: UByte, labelLength: UByte, numRasterLines: UInt, firstPage: Boolean) {
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
        try {
            usbDeviceConnection?.close()
        } catch (e: Exception) {
            Log.w(tag, "error closing exception: $e")
        }

        try {
            usbReadRequest.close()
        } catch (e: Exception) {
            Log.w(tag, "error closing exception: $e")
        }

        try {
            usbWriteRequest.close()
        } catch (e: Exception) {
            Log.w(tag, "error closing exception: $e")
        }
    }

    private fun bulkRead(data: ByteArray, timeout: Int = readTimeout): Int {
        val buffer = ByteBuffer.wrap(data)
        if(!usbReadRequest.queue(buffer)) {
            error("failed to queue usb read")
        }

        usbDeviceConnection!!.requestWait(timeout.toLong())

        if(buffer.position() != data.size) {
            error("usb read presumably failed as buffer position was not incremented. buffer.position() is ${buffer.position()}, expected ${data.size}")
        }

        //val readLen = usbDeviceConnection!!.bulkTransfer(usbEndpointIn, data, data.size, timeout)
        //if (readLen <= 0) {
        //    error("bulkRead failed")
        //}

        return buffer.position()
    }

    // With support for >16384 byte arrays
    private fun bulkWrite(data: ByteArray, timeout: Int = writeTimeout): Int {
        var offset = 0
        while (offset < data.size) {
            val remaining = data.size - offset
            val writeSize = minOf(16384, remaining)
            val buffer = ByteBuffer.wrap(data.copyOfRange(offset, offset + writeSize))

            if(!usbWriteRequest.queue(buffer)) {
                error("failed to queue usb write")
            }

            usbDeviceConnection!!.requestWait(timeout.toLong())

            if(buffer.position() != writeSize) {
                error("usb write presumably failed as buffer position was not incremented. buffer.position() is ${buffer.position()}, expected $writeSize")
            }
            //val writeLen = usbDeviceConnection!!.bulkTransfer(usbEndpointOut, data, offset,writeSize, timeout)
            //if (writeLen <= 0) {
            //    error("bulkWrite of ${data.size} bytes failed:")
            //}
            offset += writeSize
        }

        return offset
    }
}