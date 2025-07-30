package com.hx2003.labelprinter

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class PrinterApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        // https://insert-koin.io/docs/quickstart/android/#start-koin
        // Start Koin (A dependency injection library) with our Android application
        startKoin {
            androidContext(this@PrinterApplication)
            modules(PrinterApplicationModules)
        }
    }
}