package dev.kamilbak.ares

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import kotlinx.coroutines.runBlocking
import org.slf4j.*

val logger: Logger = LoggerFactory.getLogger("Ares")

suspend fun main() {
	val tag = MarkerFactory.getMarker("MainKt")
	val discordToken = System.getenv("AresDiscordToken").takeUnless { it.isNullOrBlank() } ?: return logger.error(
		tag,
		"Missing AresDiscordToken!"
	)
	val guildId = System.getenv("AresDiscordGuild").takeUnless { it.isNullOrBlank() } ?: return logger.error(
		tag,
		"Missing AresDiscordGuild!"
	)

	logger.info(tag, "Tokens acquired, creating Kord instance")
	val kord = Kord(discordToken)

	logger.info(tag, "Kord instance acquired, creating Bot instance")
	val botInstance = Bot(Snowflake(guildId))

	Runtime.getRuntime().addShutdownHook(Thread {
		runBlocking {
			logger.info(tag, "Cleaning up")
			botInstance.cleanUp(kord)
		}
	})

	botInstance.initialize(kord)
}