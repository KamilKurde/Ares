import dev.kord.core.Kord
import org.slf4j.LoggerFactory

val logger = LoggerFactory.getLogger("Ares")

suspend fun main(){
	val discordToken = System.getenv("AresDiscordToken").takeUnless { it.isNullOrBlank() } ?: return logger.error("Missing AresDiscordToken!")

	val kord = Kord(discordToken)

	val botInstance = Bot(kord)

	try {
		botInstance.initialize()
	} finally {
		botInstance.cleanUp()
	}
}