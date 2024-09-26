import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.application.ApplicationCommand
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.subCommand
import kotlinx.coroutines.runBlocking
import org.slf4j.MarkerFactory

class Bot(private val guildId: Snowflake) {
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
		logger.info(tag, "Removing commands")
		kord.removeOldCommands()
	}

	private suspend fun Kord.removeOldCommands() {
		getGlobalApplicationCommands().collect(ApplicationCommand::delete)
		getGuildApplicationCommands(guildId).collect(ApplicationCommand::delete)
	}

	private suspend fun onCommand(command: ChatInputCommandInteractionCreateEvent) {
	}

	private suspend fun Kord.setUpCommands() {
		registerCommand("combat", "Controls status of a combat") {
			subCommand("start", "Starts a combat")
			subCommand("end", "Starts a combat")
		}
		registerCommand("status", "Shows status of a combat") {

		}
	}

	private suspend fun Kord.registerCommand(
		name: String,
		description: String,
		builder: ChatInputCreateBuilder.() -> Unit
	) {
		createGuildChatInputCommand(guildId, name, description, builder)
	}
}