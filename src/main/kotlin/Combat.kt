import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.PublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import formatted.Text
import formatted.format
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.*
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import java.io.File
import kotlin.random.Random
import kotlin.random.nextInt

class Combat {
	private val tag = MarkerFactory.getMarker("Combat")
	private val targets: MutableMap<String, Target> = try {
		Toml {
			ignoreUnknownKeys = true
		}.decodeFromReader<MutableMap<String, Target>>(TomlNativeReader(File(".combat.ares").reader()))
	} catch (e: Exception) {
		logger.info(tag, "Could not read previous combat! $e")
		mutableMapOf()
	}
	private var onTargetsChange: suspend () -> Unit = {}
	private var isStarted: Boolean = false

	suspend fun add(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#add")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")
		val targetHp = interaction.command.integers["target_health"]?.toInt() ?: return interaction.respondError(
			tag,
			"target_health not found"
		)
		val targetHidden = interaction.command.booleans["target_hidden"] ?: false

		if (targetName in targets) return interaction.respondError(tag, "target_name $targetName already exists")

		targets += Target(targetName, targetHp, targetHp, targetHidden)
		onTargetsChange()
		interaction.respondEphemeral {
			content = "targets added: ${targets[targetName]}".also { logger.info(tag, it) }
		}
	}

	suspend fun attack(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#attack")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")
		targets[targetName]?.let {
			if (it.currentHp <= 0) return interaction.respondError(
				tag,
				"target_name must point to a living target"
			)
		}
		val damage = interaction.command.integers["damage"]?.toInt()
		val dice = interaction.command.integers["roll"]?.toInt()

		if (damage == null && dice == null) return interaction.respondError(tag, "Neither damage nor roll was found")
		if (damage != null && dice != null) return interaction.respondError(
			tag,
			"Only damage or roll can be specified at the same time"
		)

		val rolled = dice?.let(::roll)
		val delt = damage ?: rolled?.sum() ?: throw IllegalStateException("Space particles detected")

		val modified = targets.compute(targetName) { _, target ->
			target?.copy(currentHp = (target.currentHp - delt).coerceAtLeast(0))
		}

		if (modified != null) {
			interaction.respondPublic {
				content = buildString {
					append("<@${interaction.user.id.value}> delt")
					val wasCritical = (rolled?.count { it == 6 } ?: 0) >= 2
					if (wasCritical) {
						append(" critical")
					}
					if (rolled != null) {
						append(rolled.joinToString(separator = "+", prefix = " (", postfix = ")"))
					}
					append(" $delt damage to **$targetName** ")
					if (modified.currentHp != 0) {
						val currentHpText = modified.currentHp.toString().takeUnless { modified.isHidden } ?: "???"
						val maxHpText = modified.maxHp.toString().takeUnless { modified.isHidden } ?: "???"
						append("hp[$currentHpText/${maxHpText}] remaining ${settings.emojis.attack}")
					} else {
						append("target eliminated ${settings.emojis.kill}")
					}
				}
			}
		} else {
			interaction.respondError(tag, "target $targetName not found")
		}
	}

	suspend fun edit(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#edit")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")
		val targetHp = interaction.command.integers["target_health"]?.toInt() ?: return interaction.respondError(
			tag,
			"target_health not found"
		)

		val modified = targets.compute(targetName) { _, target ->
			target?.copy(currentHp = targetHp, maxHp = targetHp)
		}

		interaction.respondEphemeral {
			content = if (modified != null) "targets modified: $modified" else "target $targetName not found"
		}
	}

	fun save() {
		val serialized = Toml.encodeToString(targets)
		File(".combat.ares").writeText(serialized)
	}

	suspend fun status(
		interaction: ChatInputCommandInteraction,
		originalResponse: PublicMessageInteractionResponseBehavior? = null
	) {
		if (targets.isEmpty()) {
			interaction.respondEphemeral {
				content = "There are no targets"
			}
			return
		}
		val statusEmbed: MessageBuilder.() -> Unit = {
			embed {
				targets.forEach { (_, target) ->
					field {
						inline = true
						name = target.name
						val aliveColor = if (target.maxHp <= settings.bossHpLevel) Text.Color.Green else Text.Color.Pink

						val status = when {
							target.isHidden -> "UNKNOWN".format(color = Text.Color.Yellow)
							target.currentHp > 0 -> "ONLINE_".format(color = aliveColor)
							else -> "OFFLINE".format(color = Text.Color.Red)
						}
						val hpStats = when {
							target.isHidden -> "???/???".format(color = Text.Color.Yellow)
							else -> "${target.currentHp.toStat()}/${target.maxHp.toStat()}".format(color = if (target.currentHp > 0) aliveColor else Text.Color.Red)
						}
						value = ("status[".format() + status + "]\nhp    [" + hpStats + "]").toString()
					}
				}
			}
		}
		originalResponse?.edit(statusEmbed) ?: interaction.respondPublic(statusEmbed)
	}

	suspend fun start(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#start")

		if (isStarted) return interaction.respondError(tag, "combat already started")

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
		val tag = MarkerFactory.getMarker("Combat#remove")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")

		val wasRemoved = targets.remove(targetName) != null

		onTargetsChange()
		interaction.respondEphemeral {
			content = if (wasRemoved) "target removed: $targetName" else "target $targetName not found"
		}
	}

	suspend fun registerOnTargetChange(register: suspend (targetNames: List<String>) -> Unit) {
		onTargetsChange = {
			register(targets.keys.sorted())
		}
		onTargetsChange()
	}

	private operator fun MutableMap<String, Target>.plusAssign(target: Target) = plusAssign(target.name to target)

	private suspend fun ChatInputCommandInteraction.respondError(tag: Marker, description: String) {
		logger.error(tag, description)
		respondEphemeral {
			content = description
		}
	}

	private fun roll(dice: Int) = List(dice) { Random.nextInt(1..6) }

	private fun Int.toStat() = toString().padStart(3, '0')
}