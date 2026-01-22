package eu.tsp.wallet.core

/**
 * Application-wide constants
 */
object Constants {
    // Shared Preferences
    const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
    const val PREF_KEY_WALLET_KEY = "walletKey"

    // Aries Configuration
    const val GENESIS_PATH = "bcovrin-genesis.txn"
    const val MEDIATOR_INVITATION_URL = "https://public.mediator.indiciotech.io?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiMDVlYzM5NDItYTEyOS00YWE3LWEzZDQtYTJmNDgwYzNjZThhIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL3B1YmxpYy5tZWRpYXRvci5pbmRpY2lvdGVjaC5pbyIsICJyZWNpcGllbnRLZXlzIjogWyJDc2dIQVpxSktuWlRmc3h0MmRIR3JjN3U2M3ljeFlEZ25RdEZMeFhpeDIzYiJdLCAibGFiZWwiOiAiSW5kaWNpbyBQdWJsaWMgTWVkaWF0b3IifQ=="
    const val MEDIATOR_LABEL = "Indicio Public Mediator"
    const val PUBLIC_DID_SEED = "00000000000000000000000AFKIssuer"

    // Polling Intervals
    const val CONNECTIONS_REFRESH_INTERVAL_MS = 3000L
    const val MESSAGES_REFRESH_INTERVAL_MS = 2000L
    const val NOTIFICATION_DISPLAY_DURATION_MS = 5000L
    const val MESSAGE_SENT_NOTIFICATION_DURATION_MS = 3000L

    // Credential Attributes
    val DEFAULT_CREDENTIAL_ATTRIBUTES = listOf(
        "access_requester",
        "access_granter",
        "resource_owner"
    )

    // User IDs
    const val USER_ID_TRANSPORTER = "Transporter"
    const val USER_ID_FOOD_PRODUCER = "FoodProducer"
    const val USER_ID_FOOD_PROCESSOR = "FoodProcessor"
}
