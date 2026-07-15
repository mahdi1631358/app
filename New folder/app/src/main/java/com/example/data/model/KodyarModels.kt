package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class KodyarDatabaseResponse(
    val errorCodes: List<KodyarErrorCode>?,
    val spareParts: List<KodyarSparePart>?,
    val technicians: List<KodyarTechnician>?,
    val commonProblems: List<KodyarCommonProblem>?,
    val categoriesList: List<String>?,
    val brandsList: List<String>?,
    val citiesList: List<KodyarCity>?
)

@JsonClass(generateAdapter = true)
data class KodyarCity(
    val name: String? = null,
    val title: String? = null,
    val city: String? = null,
    val cityName: String? = null,
    val name_fa: String? = null,
    val nameFarsi: String? = null,
    val slug: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarErrorCode(
    val id: String?,
    val code: String?,
    val brand: String?,
    val category: String?,
    val title: String?,
    val description: String?,
    val causes: Any?, // Can be List<String> or String
    val steps: Any?, // Can be List<String> or String
    val hazardLevel: String?,
    val videoUrl: String?,
    val isApproved: Boolean?
)

@JsonClass(generateAdapter = true)
data class KodyarSparePart(
    val id: String?,
    val name: String?,
    val brand: String?,
    val price: Double?,
    val stock: Int?,
    val image: String?,
    val imageUrl: String?
)

@JsonClass(generateAdapter = true)
data class KodyarTechnician(
    val id: String?,
    val name: String?,
    val city: String?,
    val isVerified: Boolean?,
    val completedOrders: Int?,
    val bio: String?,
    val categories: List<String>?
)

@JsonClass(generateAdapter = true)
data class KodyarCommonProblem(
    val id: String?,
    val title: String?,
    val brand: String?,
    val category: String?,
    val description: String?,
    val causes: Any?,
    val steps: Any?
)

@JsonClass(generateAdapter = true)
data class KodyarUser(
    val id: String,
    val full_name: String,
    val phone: String,
    val subscription: KodyarSubscription?,
    val role: String? = "customer",
    val city: String? = null,
    val categories: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class KodyarSubscription(
    val is_premium: Boolean,
    val expiry_date: String?
)

@JsonClass(generateAdapter = true)
data class KodyarResponse(
    val status: String,
    val user: KodyarUser?,
    val error: String?,
    val repairs: List<KodyarRepairOrder>?,
    val data: List<KodyarRepairOrder>?,
    val error_count: Int?,
    val problem_count: Int?
)

@JsonClass(generateAdapter = true)
data class KodyarRepairOrder(
    val id: String?,
    val technician_id: String?,
    val technician_name: String?,
    val description: String?,
    val city: String?,
    val status: String?, // pending, accepted, ongoing, completed, rejected
    val created_at: String?
)

@JsonClass(generateAdapter = true)
data class KodyarSubscriptionPlan(
    val id: String,
    val name: String,
    val duration_days: Int,
    val price: Double,
    val description: String? = null
)

@JsonClass(generateAdapter = true)
data class KodyarPlansResponse(
    val status: String,
    val plans: List<KodyarSubscriptionPlan>?
)

@JsonClass(generateAdapter = true)
data class PartPurchaseOrder(
    val id: String,
    val partId: String,
    val partName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val address: String,
    val notes: String,
    val dateStr: String,
    val status: String // pending, sent, delivered
)

