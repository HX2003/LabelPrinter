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
import com.hx2003.labelprinter.utils.MyResult
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


enum class PrinterPrintStatus {
    NONE,
    IN_PROGRESS,
    COMPLETED
}

enum class PrinterError {
    NONE,
    NO_PRINTER_FOUND,
    PERMISSION_NOT_GRANTED,
    GENERIC_ERROR,
    LABEL_SIZE_UNKNOWN,
    LABEL_SIZE_MISMATCH
}

data class PrinterState (
    var availablePrinters: Map<String, UsbDevice> = mapOf(),
    var selectedPrinter: String? = null,
    var ready: Boolean = false,
    val printResult: MyResult<PrinterPrintStatus, PrinterError> = MyResult.NoError(PrinterPrintStatus.NONE),
    val queryResult: MyResult<PrinterQueryValue, PrinterError> = MyResult.NoError(PrinterQueryValue()),
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

    fun clearPrintRequestResult() {
        _printerState.update {
            it.copy(printResult = MyResult.NoError(PrinterPrintStatus.NONE))
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
            it.copy(printResult = MyResult.NoError(PrinterPrintStatus.IN_PROGRESS))
        }

        val printResult = mPrint(config)
        _printerState.update {
            it.copy(printResult = printResult)
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
    private suspend fun mPrint(config: PrintConfigTransformed?): MyResult<PrinterPrintStatus, PrinterError> {
        val printerUsbStateValue = _printerState.value

        if (printerUsbStateValue.selectedPrinter == null) {
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
        }

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (_: NoSuchElementException) {
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
        }

        if(!usbManager.hasPermission(usbDevice)) return MyResult.HasError(PrinterError.PERMISSION_NOT_GRANTED)

        if(config == null) {
            Log.w(tag, "config is null")
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
        }

        if(config.bitmap == null) {
            Log.w(tag, "config bitmap is null")
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
        }

        query() // query the printer for its latest status

        // We will do one more check on the label size
        if(config.labelSize == LabelSize.UNKNOWN) {
            return MyResult.HasError(PrinterError.LABEL_SIZE_UNKNOWN)
        }

        val queryResult =  _printerState.value.queryResult
        when (queryResult) {
            is MyResult.NoError -> {
                val actual = queryResult.data.labelSize
                val expected = config.labelSize

                if(actual != expected) {
                    Log.w(tag, "label size mismatch got $actual, expected $expected")
                    return MyResult.HasError(PrinterError.LABEL_SIZE_MISMATCH)
                }
            }
            is MyResult.HasError -> {
                Log.w(tag, "query result is invalid")
                return MyResult.HasError(PrinterError.GENERIC_ERROR)
            }
        }

        return withContext(Dispatchers.IO) {
            printerDeviceConnectionMutex.withLock {
                printerDeviceConnection.let {
                    if (it == null) {
                        return@withContext MyResult.HasError(PrinterError.GENERIC_ERROR)
                    }

                    val res = it.print(config)
                    when (res) {
                        is MyResult.NoError -> {
                            return@withContext MyResult.NoError(PrinterPrintStatus.COMPLETED)
                        }

                        is MyResult.HasError -> {
                            return@withContext res
                        }
                    }
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
    private suspend fun mQuery(): MyResult<PrinterQueryValue, PrinterError> {
        val printerUsbStateValue = _printerState.value

        if (printerUsbStateValue.selectedPrinter == null) {
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
        }

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (_: NoSuchElementException) {
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
        }

        if(!usbManager.hasPermission(usbDevice)) return MyResult.HasError(PrinterError.PERMISSION_NOT_GRANTED)

        return withContext(Dispatchers.IO) {
            printerDeviceConnectionMutex.withLock {
                 printerDeviceConnection.let {
                    if (it == null) {
                        return@withContext MyResult.HasError(PrinterError.GENERIC_ERROR)
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
    suspend fun requestPermissionAndConnect(): MyResult<Unit, PrinterError> {
        val printerUsbStateValue = _printerState.value

        if (printerUsbStateValue.selectedPrinter == null) {
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
        }

        val usbDevice = try {
            printerUsbStateValue.availablePrinters.getValue(printerUsbStateValue.selectedPrinter!!)
        } catch (_: NoSuchElementException) {
            return MyResult.HasError(PrinterError.NO_PRINTER_FOUND)
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
                return MyResult.HasError(PrinterError.PERMISSION_NOT_GRANTED)
            }
        }

        printerDeviceConnectionMutex.withLock {
            // Gracefully close the previous connection
            printerDeviceConnection?.close()

            // Create a new connection
            printerDeviceConnection = PrinterDeviceConnection(usbManager, usbDevice)

            if (!printerDeviceConnection!!.open()) {
                return@requestPermissionAndConnect MyResult.HasError(PrinterError.GENERIC_ERROR)
            }
        }

        return MyResult.NoError(Unit)
    }
}