package `in`.lendan.tiffin.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Kitchen(
    val id: String,
    val name: String,
    val cuisine: String,
    val pricePerTiffin: Int,
    val isVeg: Boolean,
    val rating: Double,
    val weeklyMenu: List<MenuDay>,
)

@Serializable
data class MenuDay(
    val day: String,
    val dishes: List<String>,
)
