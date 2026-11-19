package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class CategoryIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector
)

object CategoryVisuals {
    val iconOptions: List<CategoryIconOption> = listOf(
        CategoryIconOption("restaurant", "Makan", Icons.Rounded.Restaurant),
        CategoryIconOption("coffee", "Kopi", Icons.Rounded.Coffee),
        CategoryIconOption("directions_car", "Transport", Icons.Rounded.DirectionsCar),
        CategoryIconOption("shopping_bag", "Belanja", Icons.Rounded.ShoppingBag),
        CategoryIconOption("shopping_cart", "Groseri", Icons.Rounded.ShoppingCart),
        CategoryIconOption("receipt_long", "Tagihan", Icons.Rounded.ReceiptLong),
        CategoryIconOption("home", "Rumah", Icons.Rounded.Home),
        CategoryIconOption("phone_android", "Pulsa/Data", Icons.Rounded.PhoneAndroid),
        CategoryIconOption("medical_services", "Kesehatan", Icons.Rounded.MedicalServices),
        CategoryIconOption("movie", "Hiburan", Icons.Rounded.Movie),
        CategoryIconOption("school", "Pendidikan", Icons.Rounded.School),
        CategoryIconOption("fitness_center", "Olahraga", Icons.Rounded.FitnessCenter),
        CategoryIconOption("flight", "Travel", Icons.Rounded.Flight),
        CategoryIconOption("card_giftcard", "Hadiah", Icons.Rounded.CardGiftcard),
        CategoryIconOption("pets", "Hewan", Icons.Rounded.Pets),
        CategoryIconOption("payments", "Gaji", Icons.Rounded.Payments),
        CategoryIconOption("savings", "Tabungan", Icons.Rounded.Savings),
        CategoryIconOption("account_balance", "Investasi", Icons.Rounded.AccountBalance),
        CategoryIconOption("work", "Usaha", Icons.Rounded.Work),
        CategoryIconOption("more_horiz", "Lainnya", Icons.Rounded.MoreHoriz)
    )

    val colorHexOptions: List<String> = listOf(
        "#0E6B4E",
        "#10B981",
        "#EA580C",
        "#D97706",
        "#0284C7",
        "#4F46E5",
        "#7C3AED",
        "#E11D48",
        "#0D9488",
        "#475569"
    )

    fun iconForKey(iconKey: String): ImageVector =
        iconOptions.firstOrNull { it.key == iconKey }?.icon ?: Icons.Rounded.Category

    fun parseColor(hex: String, fallback: Color = Color(0xFF0E6B4E)): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            fallback
        }
    }

    fun parseColorInt(hex: String, fallback: Int = 0xFF0E6B4E.toInt()): Int {
        return try {
            android.graphics.Color.parseColor(hex)
        } catch (_: Exception) {
            fallback
        }
    }
}
