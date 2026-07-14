package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.billing.BazaarBillingManager
import com.example.data.AssistantRepository
import com.example.data.api.RetrofitClient
import com.example.data.db.AppDatabase
import com.example.ui.AssistantViewModel
import com.example.ui.AssistantViewModelFactory
import com.example.ui.screens.AssistantScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    lateinit var bazaarBillingManager: BazaarBillingManager
    lateinit var viewModel: AssistantViewModel

    companion object {
        const val BAZAAR_PURCHASE_REQUEST_CODE = 4004
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize dependencies
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AssistantRepository(database.assistantDao(), RetrofitClient.service)
        
        // Instantiate ViewModel
        viewModel = ViewModelProvider(
            this,
            AssistantViewModelFactory(repository, applicationContext)
        )[AssistantViewModel::class.java]

        // Initialize Cafe Bazaar Billing Manager
        bazaarBillingManager = BazaarBillingManager(this)
        bazaarBillingManager.startConnection()

        // Sync owned subscriptions from Cafe Bazaar to local database state
        lifecycleScope.launch {
            bazaarBillingManager.ownedSubscriptions.collect { ownedSkus ->
                val activeSku = ownedSkus.firstOrNull {
                    it == "ir.golden.com" || it == "ir.silver.com" || it == "ir.almas.com" ||
                    it == "1_month" || it == "3_month" || it == "6_month" || it == "12_month"
                }
                if (activeSku != null) {
                    viewModel.setPremiumUserLocally(activeSku)
                }
            }
        }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AssistantScreen(
                        viewModel = viewModel,
                        onPurchasePlan = { sku ->
                            bazaarBillingManager.launchPurchaseFlow(this@MainActivity, sku, BAZAAR_PURCHASE_REQUEST_CODE)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == BAZAAR_PURCHASE_REQUEST_CODE) {
            val responseCode = data?.getIntExtra("RESPONSE_CODE", 0) ?: 0
            val purchaseData = data?.getStringExtra("INAPP_PURCHASE_DATA") ?: ""
            val signature = data?.getStringExtra("INAPP_DATA_SIGNATURE") ?: ""

            if (resultCode == RESULT_OK && responseCode == 0) {
                if (bazaarBillingManager.verifyPurchaseSignature(purchaseData, signature)) {
                    try {
                        val json = JSONObject(purchaseData)
                        val sku = json.optString("productId")
                        if (!sku.isNullOrBlank()) {
                            viewModel.setPremiumUserLocally(sku)
                            Toast.makeText(this, "اشتراک شما با موفقیت فعال شد!", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error parsing purchase data: ${e.message}")
                        Toast.makeText(this, "خطا در پردازش اطلاعات خرید.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "امضای خرید معتبر نیست.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "خرید لغو شد یا با خطا مواجه گردید.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        bazaarBillingManager.startConnection()
    }

    override fun onDestroy() {
        super.onDestroy()
        bazaarBillingManager.endConnection()
    }
}
