package com.malluwayne.batbloom.plants

import androidx.compose.ui.graphics.Color

enum class PlantKind { Flower, Cactus, Mushroom }
enum class PetalShape { Round, Pointed, Wing, Star }

data class PlantSpec(
    val id: String,
    val name: String,
    val cost: Int,
    val description: String,
    val kind: PlantKind,
    val stem: Color,
    val leaf: Color,
    val petal: Color,
    val petalInner: Color,
    val center: Color,
    val glow: Color,
    val petals: Int = 5,
    val shape: PetalShape = PetalShape.Round,
)

/** Batbloom's own night plants. Coins from finished sessions unlock them; nothing is bought with money. */
object PlantCatalog {
    val all = listOf(
        PlantSpec(
            "moonflower", "Moonflower", 0, "Opens its pale petals only after dark.",
            PlantKind.Flower,
            stem = Color(0xFF4F8A6E), leaf = Color(0xFF5FA882),
            petal = Color(0xFFE8ECFF), petalInner = Color(0xFFFFFFFF), center = Color(0xFFFFE08A),
            glow = Color(0xFFBFD0FF), petals = 5, shape = PetalShape.Round,
        ),
        PlantSpec(
            "bat_orchid", "Bat Orchid", 150, "Velvet petals shaped like little wings.",
            PlantKind.Flower,
            stem = Color(0xFF3F6B5A), leaf = Color(0xFF4C8C6F),
            petal = Color(0xFF6B3FA0), petalInner = Color(0xFFA77BE0), center = Color(0xFFFF8FB1),
            glow = Color(0xFFB48CFF), petals = 4, shape = PetalShape.Wing,
        ),
        PlantSpec(
            "glowcap", "Glowcap", 250, "A cluster of mushrooms that glow teal in the dark.",
            PlantKind.Mushroom,
            stem = Color(0xFFD9E4E0), leaf = Color(0xFF5FA882),
            petal = Color(0xFF1FB7A6), petalInner = Color(0xFF7FF5E3), center = Color(0xFFE8FFFB),
            glow = Color(0xFF4FF0D8),
        ),
        PlantSpec(
            "night_cactus", "Night Cactus", 350, "Tough, patient, and it blooms pink at midnight.",
            PlantKind.Cactus,
            stem = Color(0xFF3E8C6A), leaf = Color(0xFF58B08A),
            petal = Color(0xFFFF7FB0), petalInner = Color(0xFFFFC2DA), center = Color(0xFFFFE08A),
            glow = Color(0xFFFF9FC8), petals = 8, shape = PetalShape.Pointed,
        ),
        PlantSpec(
            "starbell", "Starbell", 500, "Gold star petals that catch the moonlight.",
            PlantKind.Flower,
            stem = Color(0xFF557A4A), leaf = Color(0xFF6E9E5C),
            petal = Color(0xFFFFC94D), petalInner = Color(0xFFFFF0B8), center = Color(0xFFFF8A3D),
            glow = Color(0xFFFFD27F), petals = 6, shape = PetalShape.Star,
        ),
        PlantSpec(
            "ember_lily", "Ember Lily", 800, "Burns bright red, for the long study nights.",
            PlantKind.Flower,
            stem = Color(0xFF4A6B45), leaf = Color(0xFF5E8A55),
            petal = Color(0xFFE0453A), petalInner = Color(0xFFFF9A6B), center = Color(0xFFFFE08A),
            glow = Color(0xFFFF6A4D), petals = 6, shape = PetalShape.Pointed,
        ),
    )

    fun byId(id: String): PlantSpec = all.firstOrNull { it.id == id } ?: all.first()
}
