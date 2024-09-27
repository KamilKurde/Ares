import dev.kord.common.entity.Choice
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.application.ApplicationCommand
import dev.kord.core.entity.interaction.SubCommand
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.*
import kotlinx.coroutines.runBlocking
import org.slf4j.MarkerFactory
import java.io.File

class Bot(private val guildId: Snowflake) {
	private var combat: Combat = Combat()

	suspend fun initialize(kord: Kord) {
		val tag = MarkerFactory.getMarker("Bot#initialize")
		logger.info(tag, "Removing old commands")
		kord.removeOldCommands()
		logger.info(tag, "Setting up command hook")
		kord.on<ChatInputCommandInteractionCreateEvent>(consumer = ::onCommand)
		logger.info(tag, "Setting up new commands commands")
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
		getGlobalApplicationCommands().collect(ApplicationCommand::delete)
		getGuildApplicationCommands(guildId).collect(ApplicationCommand::delete)
	}

	private suspend fun onCommand(command: ChatInputCommandInteractionCreateEvent) {
		val invoker = command.interaction.command
		when {
			invoker.rootName == "attack" -> combat.attack(command.interaction)
			invoker.rootName == "status" -> combat.status(command.interaction)
			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "add" ->
				combat.add(command.interaction)

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "edit" ->
				combat.edit(command.interaction)

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "end" -> {
				File(".combat.ares").delete()
				combat = Combat()
				command.kord.removeOldCommands()
				command.kord.setUpCommands()
				command.interaction.respondPublic {
					content = "**COMBAT ENDED_**"
				}
			}

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "start" ->
				combat.start(command.interaction)

			invoker.rootName == "combat" && invoker is SubCommand && invoker.name == "remove" ->
				combat.remove(command.interaction)
		}
	}

	private suspend fun Kord.setUpCommands() {
		val attackCommand = registerCommand("attack", "Damages target by given HP") {
			attackCommandOptions(emptyList())
		}
		val combatCommand = registerCommand("combat", "Controls status of a combat") {
			combatCommandOptions(emptyList())
		}
		combat.registerOnTargetChange {
			attackCommand.edit {
				attackCommandOptions(it)
			}
			combatCommand.edit {
				combatCommandOptions(it)
			}
		}
		registerCommand("status", "Shows status of a combat")
	}

	private fun RootInputChatBuilder.attackCommandOptions(targets: List<String>) {
		string("target_name", "Name of the target") {
			required = true
			setAllowedChoices(targets)
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
			boolean("target_hidden", "Whether target's HP and status should be hidden") {
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
				required = true
				maxValue = settings.maxHp.toLong()
				minValue = 1
			}
		}
		subCommand("end", "Starts a combat")
		subCommand("start", "Starts a combat") {
			mentionable("users", "Users to ping when combat starts") {
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

	private suspend fun Kord.registerCommand(
		name: String,
		description: String,
		builder: ChatInputCreateBuilder.() -> Unit = {}
	) = createGuildChatInputCommand(guildId, name, description, builder)

	private fun BaseChoiceBuilder<String>.setAllowedChoices(choices: List<String>) {
		this.choices = choices.map { Choice.StringChoice(it, Optional.Missing(), it) }.toMutableList()
	}
}