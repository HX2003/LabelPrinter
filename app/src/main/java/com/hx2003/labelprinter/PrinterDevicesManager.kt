package com.hx2003.labelprinter

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.xmlpull.v1.XmlPullParser
import kotlin.collections.mapOf

sealed interface RequestPermissionAndConnectResult {
    object Success: RequestPermissionAndConnectResult
    data class Failure(val error: PrinterCommunicationError): RequestPermissionAndConnectResult
}

enum class PrintStatus {
    NOT_STARTED, // print has not started
    IN_PROGRESS, // print is in progress (ui should show loading screen)
    COMPLETED    // print has completed (regardless success or not)
}

data class PrinterState (
    var availablePrinters: Map<String, UsbDevice> = mapOf(),
    var selectedPrinter: String? = null,
    var printStatus: PrintStatus = PrintStatus.NOT_STARTED,
    val printResult: PrintCommandResult? = null, // only updated after the completion (regardless success or not) of a print
    val queryResult: QueryCommandResult? = null, // only updated after the completion (regardless success or not) of a query
)

class PrinterDevicesManager {
    private val tag = "PrinterDevicesManager"
    private val _printerState = MutableStateFlow(PrinterState())
    val printerState = _printerState.asStateFlow()

    data class UsbIdentifier(
        val vid: Int = 0,
        val pid: Int = 0
    )

    val allowedUsbIdentifiers = mutableSetOf<UsbIdentifier>()

    private var usbManager: UsbManager
    private var context: Context

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private var requestPermissionDeferrable: CompletableDeferred<Boolean>? = null
    private var usbPermissionTimeout: Long = 500

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
        _printerState.update { oldState ->
            var newSelectedPrinter: String? = null

            val newAvailablePrinters = usbManager.deviceList.filterValues { usbDevice ->
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

        Log.d(tag, "usb device list ${printerState.value.availablePrinters}")
    }

    fun setSelectedPrinter(newSelectedPrinter: String) {
        _printerState.update { oldState ->
            // Check if the newSelectedPrinter is a available device
            if(oldState.availablePrinters.isNotEmpty() && oldState.availablePrinters.containsKey(newSelectedPrinter)) {
                return@update oldState.copy(
                    selectedPrinter = newSelectedPrinter
                )
            }

            return@update oldState
        }
    }

    fun clearPrintStatusAndPrintRequestResult() {
        _printerState.update {
            it.copy(
                printStatus = PrintStatus.NOT_STARTED,
                printResult = null)
        }
    }


    /**
     * Sends a print request for the selected printer
     * Note: The selected printer must already be connected via requestPermissionAndConnect()
     *
     * @param config The print configuration including the final black and white bitmap, label size and more
     *
     * The result of the print request is stored in _printerState.printResult
     **/
    suspend fun print(config: PrintConfigTransformed?) {
        _printerState.update {
            it.copy(printStatus = PrintStatus.IN_PROGRESS)
        }

        val printResult = mPrint(config)
        _printerState.update {
            it.copy(
                printStatus = PrintStatus.COMPLETED,
                printResult = printResult
            )
        }
    }

    /**
     * Sends a print request for the selected printer
     * Note: The selected printer must already be connected via requestPermissionAndConnect()
     *
     * @param config The print configuration including the final black and white bitmap, label size and more
     *
     * @return Result<PrinterPrintStatus, PrinterError> indicating the result of the print request
     **/
    private suspend fun mPrint(config: PrintConfigTransformed?): PrintCommandResult {
        val printerUsbStateValue = _printerState.value

        if (printerUsbStateValue.selectedPrinter == null) {
            return PrintCommandResult.CommunicationError(PrinterCommunicationError.NO_PRINTER_ERROR)
        }

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (_: NoSuchElementException) {
            return PrintCommandResult.CommunicationError(PrinterCommunicationError.NO_PRINTER_ERROR)
        }

        if(!usbManager.hasPermission(usbDevice)) return PrintCommandResult.CommunicationError(PrinterCommunicationError.PERMISSION_ERROR)

        if(config == null) {
            Log.w(tag, "config is null")
            return PrintCommandResult.DeviceError(PrintStatusError.CONFIG_NULL_ERROR)
        }

        if(config.bitmap == null) {
            Log.w(tag, "config bitmap is null")
            return PrintCommandResult.DeviceError(PrintStatusError.CONFIG_NULL_ERROR)
        }

        query() // query the printer for its latest status

        // We will do one more check on the label size
        if(config.labelSize == LabelSize.UNKNOWN) {
            return PrintCommandResult.DeviceError(PrintStatusError.LABEL_SIZE_UNKNOWN_ERROR)
        }

        val queryResult = _printerState.value.queryResult
        when (queryResult) {
            is QueryCommandResult.Success -> {
                val actual = queryResult.data.labelSize
                val expected = config.labelSize

                if(actual != expected) {
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
                PrintCommandResult.DeviceError(PrintStatusError.DEVICE_ERROR)
            }
            null -> {
                Log.w(tag, "queryResult is null (weird?)")
                return PrintCommandResult.CommunicationError(PrinterCommunicationError.GENERIC_ERROR)
            }
        }

        return withContext(Dispatchers.IO) {
            // This mutex is very important
            printerDeviceConnectionMutex.withLock {
                printerDeviceConnection.let {
                    if (it == null) {
                        return@withContext PrintCommandResult.CommunicationError(PrinterCommunicationError.CONNECTION_NULL_ERROR)
                    }

                    return@withContext it.print(config)
                }
            }
        }
    }

    /**
     * Sends a query request for the selected printer
     * Note: The selected printer must already be connected via requestPermissionAndConnect()
     *
     * The result of the query request is stored in _printerState.queryResult
     **/
    suspend fun query() {
        val queryResult = mQuery()
        _printerState.update {
            it.copy(queryResult = queryResult)
        }
    }

    /**
     * Sends a query request for the selected printer
     * Note: The selected printer must already be connected via requestPermissionAndConnect()
     *
     * @return Result<PrinterQueryValue, PrinterError> indicating the result of the query request
     **/
    private suspend fun mQuery(): QueryCommandResult {
        val printerUsbStateValue = _printerState.value

        if (printerUsbStateValue.selectedPrinter == null) {
            return QueryCommandResult.CommunicationError(PrinterCommunicationError.NO_PRINTER_ERROR)
        }

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (_: NoSuchElementException) {
            return QueryCommandResult.CommunicationError(PrinterCommunicationError.NO_PRINTER_ERROR)
        }

        if(!usbManager.hasPermission(usbDevice)) return QueryCommandResult.CommunicationError(PrinterCommunicationError.PERMISSION_ERROR)

        return withContext(Dispatchers.IO) {
            // This mutex is very important
            printerDeviceConnectionMutex.withLock {
                 printerDeviceConnection.let {
                    if (it == null) {
                        return@withContext QueryCommandResult.CommunicationError(PrinterCommunicationError.CONNECTION_NULL_ERROR)
                    }
                    return@withContext it.query()
                }
            }
        }
    }

    /**
     * Request permission for the selected printer,
     * if permission is granted, connect to the printer and get its status
     *
     * @return Result<Unit, PrinterError> indicating the result of the request permission and connection
     **/
    suspend fun requestPermissionAndConnect(): RequestPermissionAndConnectResult {
        val printerUsbStateValue = _printerState.value

        if (printerUsbStateValue.selectedPrinter == null) {
            return RequestPermissionAndConnectResult.Failure(PrinterCommunicationError.NO_PRINTER_ERROR)
        }

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (_: NoSuchElementException) {
            return RequestPermissionAndConnectResult.Failure(PrinterCommunicationError.NO_PRINTER_ERROR)
        }

        if (!usbManager.hasPermission(usbDevice)) {
            // Since we do not have permission, we shall request it
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
            val result = withTimeoutOrNull(usbPermissionTimeout) {
                requestPermissionDeferrable!!.await()
            }

            if (result == null || !result) {
                return RequestPermissionAndConnectResult.Failure(PrinterCommunicationError.PERMISSION_ERROR)
            }
        }

        // this mutex is very important
        printerDeviceConnectionMutex.withLock {
            // Gracefully close the previous connection
            printerDeviceConnection?.close()

            // Create a new connection
            printerDeviceConnection = PrinterDeviceConnection(usbManager, usbDevice)

            val res = printerDeviceConnection!!.open()
            when(res) {
                is OpenCommandResult.Success -> {
                    return@requestPermissionAndConnect RequestPermissionAndConnectResult.Success
                }
                is OpenCommandResult.CommunicationError -> {
                    return@requestPermissionAndConnect RequestPermissionAndConnectResult.Failure(res.error)
                }
            }
        }
    }
}