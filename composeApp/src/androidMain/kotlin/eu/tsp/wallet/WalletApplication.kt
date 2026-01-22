package eu.tsp.wallet

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

/**
 * Custom Application class that sets up global exception handling
 * to prevent crashes from uncaught coroutine exceptions in third-party libraries.
 */
class WalletApplication : Application() {

    companion object {
        private const val TAG = "WalletApplication"

        /**
         * Global exception handler for coroutines.
         * This catches exceptions from coroutines that don't have their own exception handler.
         */
        val globalExceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Uncaught coroutine exception: ${throwable.message}", throwable)
            // Don't rethrow - this prevents the app from crashing
            // The exception is logged for debugging purposes
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Set up a default uncaught exception handler for the main thread
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Check if this is a PoolTimeout error from the Aries framework
            if (throwable.message?.contains("Pool timeout") == true ||
                throwable.cause?.message?.contains("Pool timeout") == true) {
                Log.e(TAG, "Caught PoolTimeout error, preventing crash: ${throwable.message}", throwable)
                // Don't crash for pool timeout errors - just log them
                return@setDefaultUncaughtExceptionHandler
            }

            // For other exceptions, use the default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "WalletApplication initialized with global exception handling")
    }
}
