import dev.kord.core.Kord
import dev.kord.core.behavior.reply
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.*

class Bot(private val kord: Kord) {
	@OptIn(PrivilegedIntent::class)
	suspend fun initialize(){
		kord.on<MessageCreateEvent>(consumer = ::onMessageCreate)
		kord.login {
			intents = Intents(Intent.MessageContent, Intent.GuildMessages, Intent.GuildMessageReactions)
		}
	}

	suspend fun cleanUp(){
		kord.logout()
	}

	private suspend fun onMessageCreate(event: MessageCreateEvent){

	}
}