package dev.kamilbak.ares.terminal

import dev.kamilbak.ares.logger
import dev.kamilbak.ares.util.*
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.MessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import org.slf4j.MarkerFactory

class Terminals {
	private val tag = MarkerFactory.getMarker("Terminals")
	private val terminals: MutableMap<String, Pair<Terminal, MessageInteractionResponseBehavior>> = mutableMapOf()

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

		val response = interaction.respondPublic {
			terminal(name, terminal)
		}

		terminals[name] = terminal to response

		onTerminalsChange()
	}

	suspend fun hack(interaction: ChatInputCommandInteraction) {
		val name = interaction.command.strings["terminal_name"] ?: return interaction.respondError(
			tag,
			"terminal_name not found"
		)
		val (terminal, previousResponse) = terminals[name] ?: return interaction.respondError(
			tag, "terminal with " +
					"given name not " +
					"found"
		)
		val answerIndex =
			interaction.command.integers["answer"]?.toInt() ?: return interaction.respondError(tag, "answer not found")

		val ansnwer = terminal.answers[answerIndex].second

		when (ansnwer) {
			Terminal.AnswerType.Correct -> interaction.respondPublic {
				content = "Access granted"
			}

			Terminal.AnswerType.Incorrect -> interaction.respondPublic {
				val attemptsRemaining = terminal.attemptsRemaining - 1
				content = if (attemptsRemaining == 0) "Access denied, terminal locked down" else "Access denied"

				val newTerminal = terminal.copy(attemptsRemaining = attemptsRemaining)

				val newResponse = previousResponse.edit {
					terminal(name, newTerminal)
				}

				terminals[name] = newTerminal to newResponse
			}

			Terminal.AnswerType.Virus -> interaction.respondPublic {

			}
		}
	}

	suspend fun registerOnTerminalsChange(register: suspend (targetNames: List<String>) -> Unit) {
		onTerminalsChange = {
			register(terminals.keys.sorted())
		}
		onTerminalsChange()
	}

	private fun MessageBuilder.terminal(name: String, terminal: Terminal) = embed {
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