package dev.kamilbak.ares.duel

import dev.kord.core.entity.User
import org.jetbrains.annotations.Range

data class Duel(
	val player: User,
	val oponent: String,
	val opponentBonus: Int,
	val playerScore: @Range(from = 0, to = 100) Int = 50
)
