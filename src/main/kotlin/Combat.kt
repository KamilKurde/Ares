import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.PublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import formatted.Text
import formatted.format
import org.slf4j.MarkerFactory

class Combat {
	private val tag = MarkerFactory.getMarker("Combat")
	private val targets: MutableMap<String, Target> = HashMap(10)
	private var isStarted: Boolean = false

	suspend fun add(interaction: ChatInputCommandInteraction) {
		val targetName = interaction.command.strings["target_name"]
		val targetHp = interaction.command.integers["target_health"]?.toInt()
		val targetHidden = interaction.command.booleans["target_hidden"] ?: false
		if (targetName == null) {
			interaction.respondEphemeral {
				content = "target_name not found"
			}
			return
		}
		if (targetName in targets) {
			interaction.respondEphemeral {
				content = "target_name $targetName already exists"
			}
			return
		}

		if (targetHp == null) {
			interaction.respondEphemeral {
				content = "target_health not found"
			}
			return
		}
		targets += Target(targetName, targetHp, targetHp, targetHidden)
		interaction.respondEphemeral {
			content = "targets added: ${targets[targetName]}".also { logger.info(tag, it) }
		}
	}

	suspend fun attack(interaction: ChatInputCommandInteraction) {
		val targetName = interaction.command.strings["target_name"]
		if (targetName == null) {
			interaction.respondEphemeral {
				content = "target_name not found"
			}
			return
		}
		val hp = interaction.command.integers["hp"]?.toInt()
		if (hp == null) {
			interaction.respondEphemeral {
				content = "hp not found"
			}
			return
		}

		val modified = targets.compute(targetName) { _, target ->
			target?.copy(currentHp = (target.currentHp - hp).coerceAtLeast(0))
		}

		if (modified != null) {
			interaction.respondPublic {
				content = buildString {
					append("<@${interaction.user.id.value}> delt $hp damage to **$targetName** ")
					if (modified.currentHp != 0) {
						append("hp[${modified.currentHp}/${modified.maxHp}] remaining ${settings.emojis.attack}")
					} else {
						append("target eliminated ${settings.emojis.kill}")
					}
				}
			}
		} else {
			interaction.respondEphemeral {
				content = "target $targetName not found"
			}
		}
	}

	suspend fun edit(interaction: ChatInputCommandInteraction) {
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

		val modified = targets.compute(targetName) { _, target ->
			target?.copy(currentHp = targetHp, maxHp = targetHp)
		}

		interaction.respondEphemeral {
			content = if (modified != null) "targets modified: $modified" else "target $targetName not found"
		}
	}

	suspend fun status(
		interaction: ChatInputCommandInteraction,
		originalResponse: PublicMessageInteractionResponseBehavior? = null
	) {
		val statusEmbed: MessageBuilder.() -> Unit = {
			embed {
				targets.forEach { (_, target) ->
					field {
						inline = true
						name = target.name

						val status = when {
							target.isHidden -> "UNKNOWN".format(color = Text.Color.Yellow)
							target.currentHp > 0 -> "ONLINE_".format(color = Text.Color.Green)
							else -> "OFFLINE".format(color = Text.Color.Red)
						}

						fun Int.toStat() = toString().padStart(3, '0')
						val hpStats = when {
							target.isHidden -> "???/???".format(color = Text.Color.Yellow)
							else -> "${target.currentHp.toStat()}/${target.maxHp.toStat()}".format(color = if (target.currentHp > 0) Text.Color.Green else Text.Color.Red)
						}
						value = ("status[".format() + status + "]\nhp    [" + hpStats + "]").toString()
					}
				}
			}
		}
		originalResponse?.edit(statusEmbed) ?: interaction.respondPublic(statusEmbed)
	}

	suspend fun start(interaction: ChatInputCommandInteraction) {
		if (isStarted) {
			interaction.respondEphemeral {
				content = "combat already started"
			}
			return
		}
		isStarted = true
		val usersToPing = interaction.command.mentionables["users"]?.id
		val response = interaction.respondPublic {
			content = buildString {
				usersToPing?.let {
					append("<@${it.value}> ")
				}
				settings.emojis.combatStart?.let {
					append("$it ")
				}
				append("**COMBAT STARTED_**")
			}
		}

		if (targets.isNotEmpty()) {
			status(interaction, response)
		}
	}

	suspend fun remove(interaction: ChatInputCommandInteraction) {
		val targetName = interaction.command.strings["target_name"]

		if (targetName == null) {
			interaction.respondEphemeral {
				content = "target_name not found"
			}
			return
		}

		val wasRemoved = targets.remove(targetName) != null

		interaction.respondEphemeral {
			content = if (wasRemoved) "target removed: $targetName" else "target $targetName not found"
		}
	}

	private operator fun MutableMap<String, Target>.plusAssign(target: Target) = plusAssign(target.name to target)
}