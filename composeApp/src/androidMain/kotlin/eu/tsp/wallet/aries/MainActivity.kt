// MainActivity.kt
package eu.tsp.wallet.aries

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import eu.tsp.wallet.aries.model.MainViewModel
import eu.tsp.wallet.aries.view.MainScreen

@RequiresApi(Build.VERSION_CODES.O)
class MainActivityTransporter : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceUserId = "Transporter"
        viewModel = MainViewModel(application, deviceUserId)
        setContent {
            MainScreen(viewModel, deviceUserId)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class MainActivityFoodProducer: ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceUserId = "FoodProducer"
        viewModel = MainViewModel(application, deviceUserId)
        setContent {
            MainScreen(viewModel, deviceUserId)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
class MainActivityFoodProcessor : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceUserId = "FoodProcessor"
        viewModel = MainViewModel(application, deviceUserId)
        setContent {
            MainScreen(viewModel, deviceUserId)
        }
    }
}