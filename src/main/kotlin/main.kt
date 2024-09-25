import dev.kord.core.Kord
import org.slf4j.*

val logger: Logger = LoggerFactory.getLogger("Ares")

suspend fun main(){
	val tag = MarkerFactory.getMarker("MainKt")
	val discordToken = System.getenv("AresDiscordToken").takeUnless { it.isNullOrBlank() } ?: return logger.error(
		tag,
		"Missing AresDiscordToken!"
	)

	logger.info(tag, "Token acquired, creating Kord instance")
	val kord = Kord(discordToken)

	logger.info(tag, "Kord instance acquired, creating Bot instance")
	val botInstance = Bot()

	try {
		botInstance.initialize(kord)
	} finally {
		logger.info(tag, "Cleaning up")
		botInstance.cleanUp(kord)
	}
}