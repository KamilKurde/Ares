import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.*
import org.slf4j.MarkerFactory
import java.io.File

@Serializable
data class Settings(
	val combatStartEmoji: String? = null,
)

val settings = try {
	Toml {
		ignoreUnknownKeys = true
	}.decodeFromReader<Settings>(TomlNativeReader(File("settings.ares").reader()))
} catch (e: Exception) {
	logger.error(MarkerFactory.getMarker("Settings"), "Could not read settings! $e")
	Settings()
}
