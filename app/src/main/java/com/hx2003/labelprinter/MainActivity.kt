package com.hx2003.labelprinter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {
    // We create printerViewModel: PrinterViewModel = koinViewModel() here,
    // we only want 1 instance since some states must be shared between the screens
    // In this case, printerViewModel is scoped to the closest ViewModelStoreOwner, which is the MainActivity.
    // This means the ViewModel will survive configuration changes (like screen rotations),
    // because MainActivity's ViewModelStore is retained across activity recreation.
    //
    // We avoid declaring it inside HomeScreen or PreviewScreen which will scope it to the NavBackStackEntry
    // and also result in separate instances of printerViewModel for each screen. Moreover, if we pop of the NavEntry,
    // the state will be lost
    private val mainActivityViewModel by viewModel<MainActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // You can also declare like this, it should function similarity
            // val mainActivityViewModel: MainActivityViewModel = koinViewModel()

            // Passing mainActivityViewModel to Navigation composable,
            // which will then be passed to the screens composable
            Navigation(
                modifier = Modifier.fillMaxSize(),
                mainActivityViewModel = mainActivityViewModel
            )
        }
    }
}