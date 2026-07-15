package com.example.billing

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.ConnectionState
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SafeMap(private val delegate: MutableMap<String, Any>) : MutableMap<String, Any> by delegate {
    override fun get(key: String): Any? {
        val stackTrace = Thread.currentThread().stackTrace
        val isRegisterCall = stackTrace.any { it.methodName == "register" }
        if (isRegisterCall && key == "payment_service_key") {
            return null
        }
        return delegate[key]
    }

    override fun containsKey(key: String): Boolean {
        if (key == "payment_service_key") {
            val stackTrace = Thread.currentThread().stackTrace
            val isRegisterCall = stackTrace.any { it.methodName == "register" }
            if (isRegisterCall) {
                return false
            }
        }
        return delegate.containsKey(key)
    }
}

class SafeActivityResultRegistry(private val baseRegistry: ActivityResultRegistry) : ActivityResultRegistry() {
    init {
        try {
            val field = ActivityResultRegistry::class.java.getDeclaredField("mKeyToCallback")
            field.isAccessible = true
            val originalMap = field.get(this) as? MutableMap<String, Any>
            if (originalMap != null) {
                val customMap = SafeMap(originalMap)
                field.set(this, customMap)
            }
        } catch (t: Throwable) {
            Log.e("SafeRegistry", "Error setting up custom map: ${t.message}", t)
        }
    }

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?
    ) {
        baseRegistry.onLaunch(requestCode, contract, input, options)
    }
}

class BazaarBillingManager(private val activity: ComponentActivity) {

    var safeRegistry: SafeActivityResultRegistry? = null

    private val paymentConfiguration = PaymentConfiguration(
        localSecurityCheck = SecurityCheck.Enable(BAZAAR_PUBLIC_KEY)
    )

    fun isBazaarInstalled(context: android.content.Context): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.farsitel.bazaar", 0)
            true
        } catch (e: Exception) {
            try {
                pm.getPackageInfo("ir.cafebazaar.pardakht", 0)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private val payment by lazy(LazyThreadSafetyMode.NONE) {
        Payment(context = activity, config = paymentConfiguration)
    }

    private var paymentConnection: Connection? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _ownedSubscriptions = MutableStateFlow<List<String>>(emptyList())
    val ownedSubscriptions: StateFlow<List<String>> = _ownedSubscriptions

    companion object {
        const val TAG = "BazaarBillingManager"
        const val BAZAAR_PUBLIC_KEY = "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwClvraihE80YiRtjgtmlctO0BO5fq1epUWH8q8L36Q30Dd9XXgMte5ijHfoC9vjBw37SGuYedlBMga1w/0KcaVC/CiAdza/+bVkIU1GjVTxZ14489JkjTka4uuJdvS1ciQMB8lKL6lKbyNrnSubpjVEHmPNwrO86ezkFCxF5Y/sd66Q1ZStUlKOAESqLX/RbAU2mVzJbjc9fCvggN36pX72Ma2SOEMxqKwaUZPM55UCAwEAAQ=="
    }

    fun startConnection() {
        Log.d(TAG, "Starting Bazaar billing connection with Poolakey...")
        paymentConnection = payment.connect {
            connectionSucceed {
                Log.d(TAG, "Poolakey connection succeeded.")
                _isConnected.value = true
                queryPurchasedSubscriptions()
            }
            connectionFailed { error ->
                Log.e(TAG, "Poolakey connection failed: ${error.message}")
                _isConnected.value = false
            }
            disconnected {
                Log.d(TAG, "Poolakey disconnected.")
                _isConnected.value = false
            }
        }
    }

    fun endConnection() {
        try {
            paymentConnection?.disconnect()
            paymentConnection = null
            _isConnected.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting Poolakey: ${e.message}")
        }
    }

    fun queryPurchasedSubscriptions(onComplete: ((List<String>) -> Unit)? = null) {
        val conn = paymentConnection
        if (conn == null || conn.getState() != ConnectionState.Connected) {
            Log.d(TAG, "Cannot query purchased items: Poolakey not connected.")
            onComplete?.invoke(emptyList())
            return
        }

        scope.launch {
            val verifiedSkus = mutableSetOf<String>()
            
            payment.getPurchasedProducts {
                querySucceed { purchasedItems ->
                    purchasedItems.forEach { item ->
                        verifiedSkus.add(item.productId)
                    }
                    
                    // Now check subscribed products
                    payment.getSubscribedProducts {
                        querySucceed { subscribedItems ->
                            subscribedItems.forEach { item ->
                                verifiedSkus.add(item.productId)
                            }
                            
                            _ownedSubscriptions.value = verifiedSkus.toList()
                            Log.d(TAG, "Verified owned subscriptions (Poolakey): $verifiedSkus")
                            onComplete?.invoke(verifiedSkus.toList())
                        }
                        queryFailed { error ->
                            Log.e(TAG, "Failed to query subscribed products: ${error.message}")
                            _ownedSubscriptions.value = verifiedSkus.toList()
                            onComplete?.invoke(verifiedSkus.toList())
                        }
                    }
                }
                queryFailed { error ->
                    Log.e(TAG, "Failed to query purchased products: ${error.message}")
                    payment.getSubscribedProducts {
                        querySucceed { subscribedItems ->
                            subscribedItems.forEach { item ->
                                verifiedSkus.add(item.productId)
                            }
                            _ownedSubscriptions.value = verifiedSkus.toList()
                            onComplete?.invoke(verifiedSkus.toList())
                        }
                        queryFailed {
                            onComplete?.invoke(emptyList())
                        }
                    }
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, sku: String, requestCode: Int = 0): Boolean {
        if (isBazaarInstalled(activity)) {
            val conn = paymentConnection
            if (conn == null || conn.getState() != ConnectionState.Connected) {
                Log.d(TAG, "Connection to Bazaar not active, trying to reconnect...")
                startConnection()
                Toast.makeText(activity, "در حال برقراری ارتباط با بازار... لطفا مجدداً تلاش کنید.", Toast.LENGTH_LONG).show()
                return false
            }
        } else {
            Log.d(TAG, "Bazaar is not installed. Activating demo plan...")
            val current = _ownedSubscriptions.value.toMutableSet()
            current.add(sku)
            val mappedSku = when (sku) {
                "1_month" -> "ir.golden.com"
                "3_month" -> "ir.silver.com"
                "6_month" -> "ir.almas.com"
                "12_month" -> "12_month"
                else -> sku
            }
            current.add(mappedSku)
            _ownedSubscriptions.value = current.toList()
            Toast.makeText(activity, "برنامه بازار روی دستگاه شما نصب نیست. جهت تست، اشتراک دمو فعال شد!", Toast.LENGTH_LONG).show()
            return true
        }

        val mappedSku = when (sku) {
            "1_month" -> "ir.golden.com"
            "3_month" -> "ir.silver.com"
            "6_month" -> "ir.almas.com"
            "12_month" -> "12_month"
            else -> sku
        }

        val isSubscription = mappedSku == "ir.golden.com" || mappedSku == "ir.silver.com" || mappedSku == "ir.almas.com" || mappedSku == "12_month"
        val request = PurchaseRequest(productId = mappedSku, payload = "codyar_developer_payload")

        val registry = safeRegistry ?: SafeActivityResultRegistry((activity as ComponentActivity).activityResultRegistry).also {
            safeRegistry = it
        }

        try {
            if (isSubscription) {
                payment.subscribeProduct(
                    registry = registry,
                    request = request
                ) {
                    purchaseFlowBegan {
                        Log.d(TAG, "Subscription purchase flow began for SKU: $sku")
                    }
                    failedToBeginFlow { error ->
                        Log.e(TAG, "Failed to begin subscription flow: ${error.message}")
                        Toast.makeText(activity, "خطا در شروع خرید اشتراک بازار: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                    purchaseSucceed { purchaseInfo ->
                        Log.d(TAG, "Subscription purchase succeeded: $purchaseInfo")
                        Toast.makeText(activity, "اشتراک شما با موفقیت فعال شد!", Toast.LENGTH_LONG).show()
                        queryPurchasedSubscriptions()
                    }
                    purchaseCanceled {
                        Toast.makeText(activity, "خرید لغو شد.", Toast.LENGTH_SHORT).show()
                    }
                    purchaseFailed {
                        Toast.makeText(activity, "خرید با خطا مواجه شد.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                payment.purchaseProduct(
                    registry = registry,
                    request = request
                ) {
                    purchaseFlowBegan {
                        Log.d(TAG, "Product purchase flow began for SKU: $sku")
                    }
                    failedToBeginFlow { error ->
                        Log.e(TAG, "Failed to begin product purchase flow: ${error.message}")
                        Toast.makeText(activity, "خطا در شروع خرید محصول بازار: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                    purchaseSucceed { purchaseInfo ->
                        Log.d(TAG, "Product purchase succeeded: $purchaseInfo")
                        Toast.makeText(activity, "خرید شما با موفقیت انجام شد!", Toast.LENGTH_LONG).show()
                        queryPurchasedSubscriptions()
                    }
                    purchaseCanceled {
                        Toast.makeText(activity, "خرید لغو شد.", Toast.LENGTH_SHORT).show()
                    }
                    purchaseFailed {
                        Toast.makeText(activity, "خرید با خطا مواجه شد.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during launchPurchaseFlow: ${e.message}", e)
            Toast.makeText(activity, "خطا در فرآیند پرداخت بازار: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    fun verifyPurchaseSignature(signedData: String, signature: String): Boolean {
        if (signedData.isEmpty() || signature.isEmpty()) return false
        return try {
            val keyBytes = android.util.Base64.decode(BAZAAR_PUBLIC_KEY, android.util.Base64.DEFAULT)
            val keySpec = java.security.spec.X509EncodedKeySpec(keyBytes)
            val keyFactory = java.security.KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            val sig = java.security.Signature.getInstance("SHA1withRSA")
            sig.initVerify(publicKey)
            sig.update(signedData.toByteArray())
            sig.verify(android.util.Base64.decode(signature, android.util.Base64.DEFAULT))
        } catch (e: Exception) {
            Log.e(TAG, "Signature verification failed: ${e.message}")
            false
        }
    }
}
