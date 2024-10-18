package dev.kamilbak.ares

import dev.kamilbak.ares.duel.Duels
import dev.kamilbak.ares.model.settings
import dev.kamilbak.ares.terminal.Terminals
import dev.kord.common.entity.Choice
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.core.Kord
import dev.kord.core.entity.application.GuildChatInputCommand
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.*
import dev.kord.rest.request.KtorRequestException
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.runBlocking
import org.slf4j.MarkerFactory

class Bot(private val guildId: Snowflake) {
	private val combat: Combat = Combat()
	private val duels: Duels = Duels()
	private val terminals: Terminals = Terminals()

	suspend fun initialize(kord: Kord) {
		val tag = MarkerFactory.getMarker("Bot#initialize")
		logger.info(tag, "Removing old commands")
		kord.removeOldCommands()
		logger.info(tag, "Setting up command hook")
		kord.on<ChatInputCommandInteractionCreateEvent>(consumer = ::onCommand)
		logger.info(tag, "Setting up new commands")
		kord.setUpCommands()
		logger.info(tag, "Logging in")
		kord.login()
	}

	fun cleanUp(kord: Kord) = runBlocking {
		val tag = MarkerFactory.getMarker("Bot#cleanUp")
		logger.info(tag, "Saving combat")
		combat.save()
		logger.info(tag, "Removing commands")
		kord.removeOldCommands()
	}

	private suspend fun Kord.removeOldCommands() {
		val tag = MarkerFactory.getMarker("Bot#removeOldCommands")
		merge(getGlobalApplicationCommands(), getGuildApplicationCommands(guildId)).collect {
			try {
				it.delete()
			} catch (_: KtorRequestException) {
				logger.error(tag, "Failed to remove command ${it.name}")
			}
		}
	}

	private suspend fun onCommand(command: ChatInputCommandInteractionCreateEvent) {
		val invoker = command.interaction.command
		logger.info(MarkerFactory.getMarker("Bot#onCommand"), "Parsing a ${invoker.rootName} command")
		when {
			invoker.rootName == "contra" -> duels.cancel(command.interaction)
			invoker.rootName == "duel" -> duels.duel(command.interaction)
			invoker.rootName == "versus" -> duels.versus(command.interaction)
			invoker.rootName == "terminal" -> terminals.create(command.interaction)
			invoker.rootName == "terminate" -> terminals.terminate(command.interaction)
			invoker.rootName == "hack" -> terminals.hack(command.interaction)
			invoker.rootName == "reveal" -> terminals.reveal(command.interaction)
			invoker.rootName == "attack" -> combat.attack(command.interaction)
			invoker.rootName == "status" -> combat.status(command.interaction)
			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "add" ->
				combat.add(command.interaction)

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "edit" ->
				combat.edit(command.interaction)

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "end" ->
				combat.end(command.interaction)

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "start" ->
				combat.start(command.interaction)

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "remove" ->
				combat.remove(command.interaction)

			invoker.rootName == "heal" -> combat.heal(command.interaction)
		}
	}

	private suspend fun Kord.setUpCommands() {
		val attackCommand = registerCommand("attack", "Damages target by given HP") {
			attackCommandOptions(emptyList())
		}
		val combatCommand = registerCommand("combat", "Controls status of a combat") {
			combatCommandOptions(emptyList())
		}
		val healCommand = registerCommand("heal", "Heals a target") {
			healCommandOptions(emptyList())
		}

		combat.registerOnTargetChange {
			attackCommand.edit {
				attackCommandOptions(it)
			}
			combatCommand.edit {
				combatCommandOptions(it)
			}
			healCommand.edit {
				healCommandOptions(it)
			}
		}

		registerCommand("versus", "Creates a duel") {
			user("player", "Player which partakes in a duel") {
				required = true
			}
			string("opponent", "Opponent which partakes in a duel") {
				required = true
				maxLength = 20
			}
			integer("bonus", "Additional bonus to add to opponent rolls") {
				required = false
				minValue = 1
				maxValue = 100
			}
			attachment("image", "Image to be used in status responses") {
				required = false
			}
		}

		val contraCommand = registerCommand("contra", "Cancels a duel") {
			contraCommandOptions(emptyList())
		}
		val duelCommand = registerCommand("duel", "Partakes in a duel") {
			duelCommandOptions(emptyList())
		}

		duels.registerOnDuelsChange {
			contraCommand.edit {
				contraCommandOptions(it)
			}
			duelCommand.edit {
				duelCommandOptions(it)
			}
		}

		val revealCommand = registerCommand("reveal", "Reveals one character in sequencer by giving up one attempt") {
			revealCommandOptions(emptyList())
		}
		registerCommand("terminal", "Launched a terminal minigame") {
			string("name", "Name of the terminal") {
				required = true
			}
			user("hacker", "User that is connected to that terminal") {
				required = false
			}
			integer("difficulty", "Number of hexadecimal digits that answer will have. Defaults to 8") {
				required = false
				minValue = 4
				maxValue = 16
			}
			integer("unknowns", "Number of digits that are not shown for the target. Defaults to 4") {
				required = false
				minValue = 1
				maxValue = 8
			}
			integer("viruses", "Number of viruses there will be in a terminal. Defaults to 1") {
				required = false
				minValue = 0
				maxValue = 9
			}
			integer("attempts", "Number of attempts a hacker have before terminal locks down. Defaults to 2") {
				required = false
				minValue = 1
				maxValue = 9
			}
		}
		val terminateCommand = registerCommand("terminate", "Remove terminal with given name") {
			terminateCommandOptions(emptyList())
		}
		val hackCommand = registerCommand("hack", "Hack command") {
			hackCommandOptions(emptyList())
		}
		terminals.registerOnTerminalsChange {
			hackCommand.edit {
				hackCommandOptions(it)
			}
			revealCommand.edit {
				revealCommandOptions(it)
			}
			terminateCommand.edit {
				terminateCommandOptions(it)
			}
		}
		registerCommand("status", "Shows status of a combat") {
			boolean("live", "Creates a status that will update whenever damage is delt") {
				required = false
				default = false
			}
		}
	}

	private fun RootInputChatBuilder.attackCommandOptions(targets: List<String>) {
		string("target_name", "Name of the target") {
			required = true
			setAllowedChoices(targets)
		}
		string("damage_type", "Type of the damage this attack deals") {
			required = true
			setAllowedChoices(Combat.DamageType.entries.map { it.name })
		}
		string("attacker_name", "Name of the attacker") {
			required = false
		}
		integer("damage", "HP to deduct from target") {
			required = false
			minValue = 1
			maxValue = 100
		}
		integer("roll", "Number of dice to roll to get damage") {
			required = false
			minValue = 1
			maxValue = 10
		}
	}

	private fun RootInputChatBuilder.combatCommandOptions(targets: List<String>) {
		subCommand("add", "Adds a new target to the combat") {
			string("target_name", "Name of the target") {
				required = true
			}
			integer("target_health", "Initial and maximum HP of a target") {
				required = true
				maxValue = settings.maxHp.toLong()
				minValue = 1
			}
			integer("target_armor", "Initial armor of a target") {
				required = false
				minValue = 0
				maxValue = 20
			}
			boolean("target_hidden", "Whether target's HP and status should be hidden") {
				required = false
				default = false
			}
			boolean("target_friendly", "Whether target is considered friendly") {
				required = false
				default = false
			}
		}
		subCommand("edit", "Edits existing target") {
			string("target_name", "Name of the target") {
				required = true
				setAllowedChoices(targets)
			}
			integer("target_health", "Initial and maximum HP of a target") {
				required = false
				maxValue = settings.maxHp.toLong()
				minValue = 1
			}
			integer("target_armor", "Initial armor of a target") {
				required = false
				minValue = 0
				maxValue = 20
			}
			boolean("target_hidden", "Whether target's HP and status should be hidden") {
				required = false
			}
			boolean("target_friendly", "Whether target is considered friendly") {
				required = false
			}
		}
		subCommand("end", "Starts a combat")
		subCommand("start", "Starts a combat") {
			string("name", "Name of the combat") {
				required = true
				minLength = 3
				maxLength = 255
			}
			mentionable("users", "Users to ping when combat starts") {
				required = false
			}
			attachment("image", "Image to be used in status responses") {
				required = false
			}
		}
		subCommand("remove", "Removes existing target") {
			string("target_name", "Name of the target") {
				required = true
				setAllowedChoices(targets)
			}
		}
	}

	private fun RootInputChatBuilder.contraCommandOptions(targets: List<String>) {
		string("opponent", "Name of the opponent") {
			required = true
			setAllowedChoices(targets)
		}
	}

	private fun RootInputChatBuilder.duelCommandOptions(targets: List<String>) {
		string("opponent", "Name of the opponent") {
			required = true
			setAllowedChoices(targets)
		}
		integer("bonus", "Bonus to add to player roll") {
			required = false
			minValue = 1
			maxValue = 100
		}
	}

	private fun RootInputChatBuilder.hackCommandOptions(targets: List<String>) {
		string("terminal_name", "Name of the terminal to hack") {
			required = true
			setAllowedChoices(targets)
		}
		integer("answer", "Answer to use") {
			required = true
			repeat(10) {
				choice("0x00$it", it.toLong())
			}
		}
	}

	private fun RootInputChatBuilder.healCommandOptions(targets: List<String>) {
		string("target_name", "Name of the target") {
			required = true
			setAllowedChoices(targets)
		}
		integer("hp", "HP to deduct from target") {
			required = true
			minValue = 1
			maxValue = 100
		}
		string("healer_name", "Name of the attacker") {
			required = false
		}
	}

	private fun RootInputChatBuilder.revealCommandOptions(targets: List<String>) {
		string("terminal_name", "Name of the terminal to terminate") {
			required = true
			setAllowedChoices(targets)
		}
	}

	private fun RootInputChatBuilder.terminateCommandOptions(targets: List<String>) {
		string("terminal_name", "Name of the terminal to terminate") {
			required = true
			setAllowedChoices(targets)
		}
	}

	private suspend fun Kord.registerCommand(
		name: String,
		description: String,
		builder: ChatInputCreateBuilder.() -> Unit = {}
	): GuildChatInputCommand {
		logger.info(MarkerFactory.getMarker("Bot#registerCommand"), name)
		return createGuildChatInputCommand(guildId, name, description, builder)
	}

	private fun BaseChoiceBuilder<String>.setAllowedChoices(choices: List<String>) {
		this.choices = choices.map { Choice.StringChoice(it, Optional.Missing(), it) }.toMutableList()
	}
}