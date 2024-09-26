import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.*
import org.slf4j.MarkerFactory
import java.io.File

@Serializable
data class Settings(
	val maxHp: Int = 100,
	val bossHpLevel: Int = 100,
	val emojis: Emojis = Emojis()
)

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
