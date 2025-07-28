package com.hx2003.labelprinter

import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

// https://insert-koin.io/docs/quickstart/android/#koin-module-classic-or-constructor-dsl
// Koin (A dependency injection library) module declaration to specify dependencies that then can be used
//
// 'single': One unique instance for the entire app
// 'factory': A new instance each time we ask for this definition
// 'viewModel': Specifically for Android's ViewModel instance
val MyApplicationModules = module {
    singleOf(::USBController)
    viewModelOf(::PrinterViewModel)
}