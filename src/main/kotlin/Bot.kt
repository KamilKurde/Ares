import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.application.ChatInputCommandCommand
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.*
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.string

class Bot {
	private var commands = emptyList<ChatInputCommandCommand>()

	@OptIn(PrivilegedIntent::class)
	suspend fun initialize(kord: Kord) {
		kord.removeOldCommands()
		kord.setUpCommands()
		kord.on<MessageCreateEvent>(consumer = ::onMessageCreate)
		kord.login {
			intents = Intents(Intent.MessageContent, Intent.GuildMessages, Intent.GuildMessageReactions)
		}
	}

	suspend fun cleanUp(kord: Kord) {
		commands.forEach { command -> command.delete() }
		kord.logout()
	}

	private suspend fun onMessageCreate(event: MessageCreateEvent) {

	}

	private suspend fun Kord.removeOldCommands() {
		getGlobalApplicationCommands().collect{ command ->
			command.delete()
		}
		guilds.collect{ guild ->
			guild.getApplicationCommands().collect{ command ->
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

	private suspend fun Kord.registerCommand(name: String, description: String, builder: ChatInputCreateBuilder.() ->
	Unit) {
		val registeredCommand = when(val guildId = System.getenv("AresDiscordGuild").takeUnless { it.isNullOrBlank() }){
			null -> createGlobalChatInputCommand(name, description, builder)
			else -> createGuildChatInputCommand(Snowflake(guildId), name, description, builder)
		}
		commands += registeredCommand
	}
}