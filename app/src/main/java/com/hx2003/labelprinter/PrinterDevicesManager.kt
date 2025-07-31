package com.hx2003.labelprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import kotlin.collections.mapOf

// The possible error codes returned from a print()
enum class PrintError {
    NONE,
    NO_PRINTER_FOUND,
    PERMISSION_NOT_GRANTED,
    GENERIC_ERROR
}

data class PrinterUsbState (
    var availablePrinters: Map<String, UsbDevice> = mapOf(),
    var selectedPrinter: String? = null,
    var printerReady: Boolean = false
)

class PrinterDevicesManager {
    private val tag = "PrinterDevicesManager"
    private val _printerUsbState = MutableStateFlow(PrinterUsbState())
    val printerUsbState = _printerUsbState.asStateFlow()

    data class UsbIdentifier(
        val vid: Int = 0,
        val pid: Int = 0
    )

    val allowedUsbIdentifiers = mutableSetOf<UsbIdentifier>()

    private var usbManager: UsbManager
    private var context: Context

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private var requestPermissionDeferrable: CompletableDeferred<Boolean>? = null

    private var printerDeviceConnectionMutex = Mutex()
    private var printerDeviceConnection: PrinterDeviceConnection? = null

    constructor(context: Context) {
        this.context = context

        val xml = context.resources.getXml(R.xml.usb_device_filter)
        var eventType = xml.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && xml.name == "usb-device") {
                val vid = xml.getAttributeIntValue(null, "vendor-id", -1)
                val pid = xml.getAttributeIntValue(null, "product-id", -1)
                if (vid != -1 && pid != -1) {
                    allowedUsbIdentifiers.add(UsbIdentifier(vid, pid))
                }
            }
            eventType = xml.next()
        }

        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        filter.addAction(ACTION_USB_PERMISSION)

        registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        updateDevices()

        CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                printerDeviceConnectionMutex.withLock {
                    Log.i("heatrbest", "heratbest")
                    queryStatus()
                }
                delay(333)
            }
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when(intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    Log.d(tag, "attached usb device $device")
                    updateDevices(device)
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    Log.d(tag, "detached usb device $device")
                    updateDevices()
                }
                ACTION_USB_PERMISSION -> {
                    // From testing ACTION_USB_PERMISSION is only triggered,
                    // when manually asking permission via requestPermission
                    //
                    // Do note that permission can also be granted when the user connects to the usb device,
                    // explicitly calling requestPermission
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        Log.d(tag, "permission granted for usb device $device")
                        requestPermissionDeferrable?.complete(true)
                    } else {
                        Log.d(tag, "permission denied for usb device $device")
                        requestPermissionDeferrable?.complete(false)
                    }
                }
            }
        }
    }

    /**
     * Updates the current list of available printers and also the selected printer
     *
     * @param newUsbDevice If a UsbDevice is provided, we first check whether it is available,
     *                     and set the selected printer to the provided UsbDevice
     **/
    private fun updateDevices(newUsbDevice: UsbDevice? = null) {
        _printerUsbState.update { oldState ->
            var newSelectedPrinter: String? = null

            val newAvailablePrinters = usbManager.deviceList.filter { (key, usbDevice) ->
                // Check if the id of the usb device is in inside our allowedUsbIdentifiers
                allowedUsbIdentifiers.contains(
                    UsbIdentifier(vid = usbDevice.vendorId, pid = usbDevice.productId)
                )
            }

            if(newAvailablePrinters.isNotEmpty()) {
                newSelectedPrinter = if(newAvailablePrinters.containsKey(oldState.selectedPrinter)) {
                    // If possible, keep the same selected printer
                    oldState.selectedPrinter
                } else{
                    // The old selected printer is not available anymore,
                    // hence we pick any available printer
                    newAvailablePrinters.keys.first()
                }
            }

            // If a UsbDevice is provided, make it our selected printer
            newAvailablePrinters.forEach { (key, usbDevice) ->
                if(usbDevice == newUsbDevice) {
                    newSelectedPrinter = key
                }
            }

            oldState.copy(
                availablePrinters = newAvailablePrinters,
                selectedPrinter = newSelectedPrinter
            )
        }

        Log.d(tag, "usb device list ${printerUsbState.value.availablePrinters}")
    }

    fun setSelectedPrinter(newSelectedPrinter: String) {
        _printerUsbState.update { oldState ->
            // Check if the newSelectedPrinter is a available device
            if(oldState.availablePrinters.isNotEmpty() && oldState.availablePrinters.containsKey(newSelectedPrinter)) {
                return@update oldState.copy(
                    selectedPrinter = newSelectedPrinter
                )
            }

            return@update oldState
        }
    }

    suspend fun print(): PrintError {
        val res: PrintError = requestPermissionAndConnect()

        if(res != PrintError.NONE) return res

        val printerUsbStateValue = _printerUsbState.value

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (e: NoSuchElementException) {
            return PrintError.NO_PRINTER_FOUND
        }

       /* withContext(Dispatchers.IO) {
            try {

            } catch (e: Exception) {
                return@withContext PrintError.GENERIC_ERROR
            }
        }*/

        return PrintError.NONE
    }

    /**
     * Request permission for the selected printer,
     * if permission is granted, connect to the printer and get its status
     *
     * @return [PrintError] indicating the result of the request. This is a subset of possible [PrintError] values:
     *  * - [PrintError.NONE]: Permission granted successfully
     *  * - [PrintError.NO_PRINTER_FOUND]: No available printer found
     *  * - [PrintError.PERMISSION_NOT_GRANTED]: The user denied the permission request
     **/
    suspend fun requestPermissionAndConnect(): PrintError {
        val printerUsbStateValue = _printerUsbState.value

        if(printerUsbStateValue.selectedPrinter == null) {
            return PrintError.NO_PRINTER_FOUND
        }

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (e: NoSuchElementException) {
            return PrintError.NO_PRINTER_FOUND
        }

        // Create a CompletableDeferred
        requestPermissionDeferrable = CompletableDeferred<Boolean>()

        // The below code is tested with Android 15
        // From Android 12 onwards use PendingIntent.FLAG_MUTABLE
        // From Android 14 onwards use explicit intent by using setPackage
        val permissionIntent = Intent(ACTION_USB_PERMISSION)
        permissionIntent.setPackage(context.packageName)

        val pendingIntent =
            PendingIntent.getBroadcast(context, 0, permissionIntent, PendingIntent.FLAG_MUTABLE)
        usbManager.requestPermission(usbDevice, pendingIntent)

        // Wait for the requestPermissionDeferrable flag to be set
        if (!requestPermissionDeferrable!!.await()) return PrintError.PERMISSION_NOT_GRANTED

        // Gracefully close the previous connection
        printerDeviceConnection?.close()

        try {
            // Create a new connection
            printerDeviceConnection = PrinterDeviceConnection(usbManager, usbDevice)

            queryStatus()
        } catch (e: Exception) {
            return PrintError.GENERIC_ERROR
        }

        return PrintError.NONE
    }

    private suspend fun queryStatus() {
        printerDeviceConnectionMutex.withLock {
            if(printerDeviceConnection != null) {
                // Get status
                //printerDeviceConnection.queryStatus()
            }
        }
    }
}