package dev.kamilbak.ares.model

import dev.kamilbak.ares.logger
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.*
import org.slf4j.MarkerFactory
import java.io.File

/**
 * @property maxHp Maximum amount of HP a target can have
 * @property bossHpLevel Amount of HP a target should have to be considered a "boss" and be coloured differently
 * @property embedIcon Icon to use before combat name in status
 * @property emojis Emojis to use in text messages
 */
@Serializable
data class Settings(
	val maxHp: Int = 100,
	val bossHpLevel: Int = 100,
	val embedIcon: String? = null,
	val emojis: Emojis = Emojis()
)

/**
 * @property combatStart used in combat start response
 * @property attack used in response to attack command that delt damage but did not kill anyone
 * @property kill used in response to attack command that killed a target
 */
@Serializable
data class Emojis(
	val combatStart: String? = null,
	val attack: String? = null,
	val kill: String? = null,
)

val settings = try {
	Toml {
		ignoreUnknownKeys = true
	}.decodeFromReader<Settings>(TomlNativeReader(File("settings.ares").reader()))
} catch (e: Exception) {
	logger.error(MarkerFactory.getMarker("Settings"), "Could not read settings! $e")
	Settings()
}
