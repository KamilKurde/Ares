package dev.kamilbak.ares.terminal

import dev.kamilbak.ares.logger
import dev.kamilbak.ares.model.settings
import dev.kamilbak.ares.util.*
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.MessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import org.slf4j.MarkerFactory
import kotlin.random.Random
import kotlin.random.nextInt

class Terminals {
	private val tag = MarkerFactory.getMarker("Terminals")
	private val terminals: MutableMap<String, Pair<Terminal, MessageInteractionResponseBehavior>> = mutableMapOf()

	private var onTerminalsChange: suspend () -> Unit = {}

	suspend fun create(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Terminals#create")
		val name = interaction.command.strings["name"] ?: return interaction.respondError(tag, "name not found")
		if (name in terminals.keys) return interaction.respondError(tag, "name already exists")

		val hacker = interaction.command.users["hacker"]

		val difficulty = interaction.command.integers["difficulty"]?.toInt() ?: 8
		val unknowns = interaction.command.integers["unknowns"]?.toInt() ?: 4
		val viruses = interaction.command.integers["viruses"]?.toInt() ?: 1
		val attempts = interaction.command.integers["attempts"]?.toInt() ?: 2

		logger.info(
			tag,
			"Creating terminal for ${hacker?.username}, with difficulty $difficulty, unknown $unknowns and viruses $viruses"
		)

		try {
			val terminal = Terminal.fromCommand(hacker?.id, difficulty, unknowns, viruses, attempts)

			val response = interaction.respondPublic {
				terminal(name, terminal)
			}

			terminals[name] = terminal to response

			onTerminalsChange()
		} catch (_: StackOverflowError) {
			interaction.respondError(
				tag,
				"Error while creating terminal, could not generate answers satisfying all constrains"
			)
		}
	}

	suspend fun hack(interaction: ChatInputCommandInteraction) {
		val name = interaction.command.strings["terminal_name"] ?: return interaction.respondError(
			tag, "terminal_name not found"
		)
		val (terminal, previousResponse) = terminals[name] ?: return interaction.respondError(
			tag, "terminal with given name not found"
		)
		terminal.hacker?.let {
			if (interaction.user.id != it) return interaction.respondError(
				tag, "Expecting <@$it>, and you are not them"
			)
		}

		val answerIndex =
			interaction.command.integers["answer"]?.toInt() ?: return interaction.respondError(tag, "answer not found")
		val answer = terminal.answers[answerIndex]
		when (val type = answer.type) {
			Terminal.Answer.Type.Correct -> interaction.respondPublic {
				content = "**ACCESS_GRANTED!**"

				previousResponse.edit {
					terminal(name, terminal, true)
				}
				terminals.remove(name)
				onTerminalsChange()
			}

			Terminal.Answer.Type.Incorrect, Terminal.Answer.Type.Virus -> interaction.respondPublic {
				val attemptsRemaining =
					(terminal.attemptsRemaining - if (type == Terminal.Answer.Type.Virus) 2 else 1).coerceAtLeast(0)
				val newTerminal = terminal.copy(attemptsRemaining = attemptsRemaining)
				content = buildString {
					append("**")
					if (type == Terminal.Answer.Type.Virus) {
						appendLine("ICE detected. You received ${Random.nextInt(1..6)} damage.")
					} else {
						appendLine("ACCESS_DENIED!")
					}
					if (attemptsRemaining == 0) {
						appendLine("TERMINAL_LOCKED_DOWN")
					}
					append("**")
				}

				if (attemptsRemaining == 0) {
					previousResponse.edit {
						terminal(name, newTerminal, false)
					}
					terminals.remove(name)
				} else {
					val newResponse = previousResponse.edit {
						terminal(name, newTerminal)
					}

					terminals[name] = newTerminal to newResponse
				}
			}
		}
	}

	suspend fun reveal(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Terminals#reveal")
		val name = interaction.command.strings["terminal_name"] ?: return interaction.respondError(
			tag, "terminal_name not found"
		)
		val (terminal, previousResponse) = terminals[name] ?: return interaction.respondError(
			tag, "terminal with given name not found"
		)
		terminal.hacker?.let {
			if (interaction.user.id != it) return interaction.respondError(
				tag, "Expecting <@$it>, and you are not them"
			)
		}

		if (terminal.attemptsRemaining == 1) return interaction.respondError(tag, "You can't reveal new characters")

		val newQuestion = terminal.run {
			val charToReveal = question.withIndex().filter { it.value == '?' }.random().index
			val correctAnswer = answers.first { it.type == Terminal.Answer.Type.Correct }.value

			question.toCharArray().apply {
				set(charToReveal, correctAnswer[charToReveal])
			}.concatToString()
		}

		val newTerminal = terminal.copy(question = newQuestion, attemptsRemaining = terminal.attemptsRemaining - 1)
		val newResponse = previousResponse.edit {
			terminal(name, newTerminal)
		}

		terminals[name] = newTerminal to newResponse

		interaction.respondPublic {
			content = "Revealed one digit in SEQUENCER_"
		}
	}

	suspend fun terminate(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Terminals#terminate")
		val terminalName = interaction.command.strings["terminal_name"] ?: return interaction.respondError(
			tag,
			"terminal_name not found"
		)

		val (_, removedResponse) = terminals.remove(terminalName) ?: return interaction.respondError(
			tag,
			"No terminal with $terminalName found"
		)
		removedResponse.delete()
		onTerminalsChange()
		interaction.respondEphemeral { content = "Terminal $terminalName removed" }
	}

	suspend fun registerOnTerminalsChange(register: suspend (targetNames: List<String>) -> Unit) {
		onTerminalsChange = {
			register(terminals.keys.sorted())
		}
		onTerminalsChange()
	}

	private fun MessageBuilder.terminal(name: String, terminal: Terminal, result: Boolean? = null) = embed {
		title = "CODE BREACH\t\t$name"
		description = buildText {
			append("SEQUENCER_")
			appendLine(terminal.question.toText(color = if (result == true) Text.Color.Green else Text.Color.Red))
			appendLine("${terminal.attemptsRemaining} ATTEMPT(S) REMAINING:")
			appendLine("-".repeat(37))
			terminal.answers.forEachIndexed { index, (answer) ->
				appendLine("0x00$index  <(=<(>$$,'};  ${answer.padStart(16, '0')}")
			}
		}.toString()
		image = when (result) {
			true -> settings.images.terminalSuccess
			false -> settings.images.terminalFailure
			null -> null
		}
	}
}