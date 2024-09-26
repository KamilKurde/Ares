import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.embed
import formatted.Text
import formatted.format
import org.slf4j.MarkerFactory

class Combat {
	private val tag = MarkerFactory.getMarker("Combat")
	private val targets: MutableMap<String, Target> = HashMap(10)

	suspend fun add(interaction: ChatInputCommandInteraction) {
		val targetName = interaction.command.strings["target_name"]
		val targetHp = interaction.command.integers["target_health"]?.toInt()
		if (targetName == null) {
			interaction.respondEphemeral {
				content = "target_name not found"
			}
			return
		}
		if (targetHp == null) {
			interaction.respondEphemeral {
				content = "target_health not found"
			}
			return
		}
		targets += Target(targetName, targetHp, targetHp)
		interaction.respondEphemeral {
			content = "targets added: ${targets[targetName]}".also { logger.info(tag, it) }
		}
	}

	suspend fun status(interaction: ChatInputCommandInteraction) {
		interaction.respondPublic {
			embed {
				targets.forEach { _, target ->
					field {
						name = target.name

						val status = if (target.currentHp > 0) {
							"ONLINE".format(color = Text.Color.Green)
						} else {
							"OFFLINE".format(color = Text.Color.Red)
						}
						value = ("status[".format() + status + "]").toString()
					}
				}
			}
		}
	}

	suspend fun start(interaction: ChatInputCommandInteraction) {
		interaction.respondPublic {

		}
	}

	private operator fun MutableMap<String, Target>.plusAssign(target: Target) = plusAssign(target.name to target)
}