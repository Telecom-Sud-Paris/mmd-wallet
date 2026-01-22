package eu.tsp.wallet.presentation.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import eu.tsp.wallet.core.Constants
import eu.tsp.wallet.presentation.viewmodel.WalletViewModel
import eu.tsp.wallet.presentation.ui.MainScreen

/**
 * Base activity for wallet application
 * The specific user ID is determined by the activity class
 */
@RequiresApi(Build.VERSION_CODES.O)
abstract class BaseWalletActivity : ComponentActivity() {

    protected abstract val deviceUserId: String
    private lateinit var viewModel: WalletViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = WalletViewModel(application, deviceUserId)
        setContent {
            MainScreen(viewModel, deviceUserId)
        }
    }
}

/**
 * Activity for Transporter user
 */
@RequiresApi(Build.VERSION_CODES.O)
class TransporterActivity : BaseWalletActivity() {
    override val deviceUserId: String = Constants.USER_ID_TRANSPORTER
}

/**
 * Activity for Food Producer user
 */
@RequiresApi(Build.VERSION_CODES.O)
class FoodProducerActivity : BaseWalletActivity() {
    override val deviceUserId: String = Constants.USER_ID_FOOD_PRODUCER
}

/**
 * Activity for Food Processor user
 */
@RequiresApi(Build.VERSION_CODES.O)
class FoodProcessorActivity : BaseWalletActivity() {
    override val deviceUserId: String = Constants.USER_ID_FOOD_PROCESSOR
}
