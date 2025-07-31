package com.hx2003.labelprinter

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.util.Log

data class PrinterStatusQueryResult (
    val labelSize: Int = 0,
    val error: Boolean
)

class PrinterDeviceConnection {
    private var usbDeviceConnection: UsbDeviceConnection? = null
    private var usbEndpointIn: UsbEndpoint? = null
    private var usbEndpointOut: UsbEndpoint? = null

    private val writeTimeout = 500
    private val readTimeout = 500

    constructor(usbManager: UsbManager, usbDevice: UsbDevice) {
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

        if(usbEndpointIn == null) {
            error("usbEndpointIn is null")
        }

        if(usbEndpointOut == null) {
            error("usbEndpointOut is null")
        }

        usbDeviceConnection = usbManager.openDevice(usbDevice)

        if(usbDeviceConnection == null) {
            error("failed to open usb device, usbDeviceConnection is null")
        }

        if(!usbDeviceConnection!!.claimInterface(usbInterface, true)) {
            error("failed to claim usb device, usbDeviceConnection is null")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun queryStatus(): PrinterStatusQueryResult {
        val buffer = ByteArray(32)

        try {
            bulkWrite(ubyteArrayOf(0x1Bu, 0x40u).asByteArray()) // init
            bulkWrite(ubyteArrayOf(0x1Bu, 0x69u, 0x53u).asByteArray())
            bulkRead(buffer)
        } catch(_: Exception) {
            return PrinterStatusQueryResult(
                error = true
            )
        }

        val labelSize = buffer[10].toUInt()
        val statusByte = buffer[18]

        Log.i("labelSize", labelSize.toString())
        Log.i("status", statusByte.toString())

        return PrinterStatusQueryResult(
            error = true
        )
    }

    fun close() {
        usbDeviceConnection?.close()
    }

    private fun bulkRead(data: ByteArray): Int {
        val readLen = usbDeviceConnection!!.bulkTransfer(usbEndpointIn, data, data.size, readTimeout)
        if (readLen <= 0) {
            error("bulkRead failed")
        }
        return readLen
    }

    // With support for >16384 byte arrays
    private fun bulkWrite(data: ByteArray): Int {
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