import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.gateway.*
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.runBlocking
import org.slf4j.MarkerFactory

class Bot {
	@OptIn(PrivilegedIntent::class)
	suspend fun initialize(kord: Kord) {
		val tag = MarkerFactory.getMarker("Bot#initialize")
		logger.info(tag, "Removing old commands")
		kord.removeOldCommands()
		logger.info(tag, "Setting up new commands commands")
		kord.setUpCommands()
		logger.info(tag, "Logging in")
		kord.login {
			intents = Intents(Intent.MessageContent, Intent.GuildMessages, Intent.GuildMessageReactions)
		}
	}

	fun cleanUp(kord: Kord) = runBlocking {
		val tag = MarkerFactory.getMarker("Bot#cleanUp")
		logger.info(tag, "Logging out")
		kord.logout()
	}

	private suspend fun Kord.removeOldCommands() {
		getGlobalApplicationCommands().collect { command ->
			command.delete()
		}
		guilds.collect { guild ->
			guild.getApplicationCommands().collect { command ->
				command.delete()
			}
		}
	}

	private suspend fun Kord.setUpCommands() {
		registerCommand("combat", "Shows combat status for a combat of given name") {
			string("combat_name", "Name of combat to show status for") {
				required = true
				minLength = 3
				maxLength = 20
			}
		}
	}

	private suspend fun Kord.registerCommand(
		name: String,
		description: String,
		builder: ChatInputCreateBuilder.() -> Unit
	) {
		when (val guildId = System.getenv("AresDiscordGuild").takeUnless { it.isNullOrBlank() }) {
			null -> createGlobalChatInputCommand(name, description, builder)
			else -> createGuildChatInputCommand(Snowflake(guildId), name, description, builder)
		}
	}
}