package dev.kamilbak.ares.terminal

import dev.kamilbak.ares.logger
import dev.kamilbak.ares.util.*
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.embed
import org.slf4j.MarkerFactory

class Terminals {
	private val tag = MarkerFactory.getMarker("Terminals")
	private val terminals: MutableMap<String, Terminal> = mutableMapOf()

	private var onTerminalsChange: suspend () -> Unit = {}

	suspend fun create(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Terminals#create")
		val name = interaction.command.strings["name"] ?: return interaction.respondError(tag, "name not found")
		if (name in terminals.keys) return interaction.respondError(tag, "name already exists")

		val hacker = interaction.command.users["hacker"] ?: return interaction.respondError(tag, "hacker not found")

		val difficulty = interaction.command.integers["difficulty"]?.toInt() ?: 8
		val unknowns = interaction.command.integers["unknowns"]?.toInt() ?: 4
		val viruses = interaction.command.integers["viruses"]?.toInt() ?: 1

		logger.info(
			tag,
			"Creating terminal for ${hacker.username}, with difficulty $difficulty, unknown $unknowns and viruses $viruses"
		)
		val terminal = Terminal.fromCommand(hacker.id, difficulty, unknowns, viruses, 2)
		terminals[name] = terminal

		interaction.respondPublic {
			embed {
				title = "CODE BREACH\t\t$name"
				description = buildText {
					append("SEQUENCER_")
					appendLine(terminal.question.toText(color = Text.Color.Red))
					appendLine("${terminal.attemptsRemaining} ATTEMPT(S) REMAINING:")
					appendLine("-".repeat(37))
					terminal.answers.forEachIndexed { index, (answer) ->
						appendLine("0x00$index  <(=<(>$$,'};  ${answer.padStart(16, '0')}")
					}
				}.toString()
			}
		}

		onTerminalsChange()
	}

	suspend fun registerOnTerminalsChange(register: suspend (targetNames: List<String>) -> Unit) {
		onTerminalsChange = {
			register(terminals.keys.sorted())
		}
		onTerminalsChange()
	}
}