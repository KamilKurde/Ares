package dev.kamilbak.ares.model

import kotlinx.serialization.Serializable

@Serializable
data class Target(
	val currentHp: Int,
	val maxHp: Int,
	val currentArmor: Int = 0,
	val maxArmor: Int = currentArmor,
	val isHidden: Boolean = false,
	val isFriendly: Boolean = false
)
