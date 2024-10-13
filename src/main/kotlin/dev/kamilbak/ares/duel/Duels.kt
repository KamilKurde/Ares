package dev.kamilbak.ares.duel

import dev.kamilbak.ares.util.*
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.MessageInteractionResponseBehavior
import dev.kord.core.entity.effectiveName
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import org.slf4j.MarkerFactory
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.random.Random
import kotlin.random.nextInt

class Duels {
	private val duels: MutableMap<String, Pair<Duel, MessageInteractionResponseBehavior>> = mutableMapOf()

	private var onDuelsChange: suspend () -> Unit = {}

	suspend fun versus(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Duels#versus")

		val player = interaction.command.users["player"]
			?: return interaction.respondError(tag, "player not found")
		val opponent = interaction.command.strings["opponent"]
			?: return interaction.respondError(tag, "opponent not found")
		val bonus = interaction.command.integers["bonus"]?.toInt() ?: 0

		if (opponent in duels.keys) return interaction.respondError(tag, "opponent already partakes in a duel")

		val duel = Duel(player, opponent, bonus + Random.nextInt(1..10))
		val response = interaction.respondPublic {
			duel(duel)
		}

		duels[opponent] = duel to response

		onDuelsChange()
	}

	private fun MessageBuilder.duel(duel: Duel) = embed {
		title = "VERSUS"
		description = buildText {
			val longerNameLength = maxOf(duel.player.effectiveName.length, duel.oponent.length).coerceAtLeast(9)
			val segments = (longerNameLength * 2) + 4 - (2 * 6)

			append(
				duel.oponent.padStart(longerNameLength),
				color = Text.Color.Red,
				decoration = Text.Decoration.Bold
			)
			append(" v", color = Text.Color.Red)
			append("s ", color = Text.Color.Blue)
			appendLine(
				duel.player.effectiveName.padEnd(longerNameLength),
				color = Text.Color.Blue,
				decoration = Text.Decoration.Bold
			)
			append((100 - duel.playerScore).toString().padStart(3, '0') + "%", color = Text.Color.Red)
			append(" [", color = Text.Color.Red)
			append("=".repeat(floor((100f - duel.playerScore) / 100 * segments).toInt()), color = Text.Color.Red)
			append("=".repeat(ceil(duel.playerScore.toFloat() / 100 * segments).toInt()), color = Text.Color.Blue)
			append("] ", color = Text.Color.Blue)
			append(duel.playerScore.toString().padStart(3, '0') + "%", color = Text.Color.Blue)
		}.toString()
	}
}