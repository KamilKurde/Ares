package dev.kamilbak.ares

import dev.kamilbak.ares.model.Target
import dev.kamilbak.ares.model.settings
import dev.kamilbak.ares.util.*
import dev.kord.common.Color
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.PublicMessageInteractionResponseBehavior
import dev.kord.core.behavior.interaction.response.edit
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import dev.kord.rest.builder.message.MessageBuilder
import dev.kord.rest.builder.message.embed
import kotlinx.serialization.encodeToString
import net.peanuuutz.tomlkt.*
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
	private var footerImage: String? = null
	private var name: String = ""
	private var isStarted: Boolean = false

	suspend fun add(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#add")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")
		val targetHp = interaction.command.integers["target_health"]?.toInt() ?: return interaction.respondError(
			tag,
			"target_health not found"
		)
		val targetArmor = interaction.command.integers["target_armor"]?.toInt() ?: 0
		val targetHidden = interaction.command.booleans["target_hidden"] ?: false
		val targetFriendly = interaction.command.booleans["target_friendly"] ?: false

		if (targetName in targets) return interaction.respondError(tag, "target_name $targetName already exists")

		targets[targetName] = Target(targetHp, targetHp, targetArmor, targetArmor, targetHidden, targetFriendly)
		onTargetsChange()
		interaction.respondEphemeral {
			content = "targets added: ${targets[targetName]}".also { logger.info(tag, it) }
		}
	}

	suspend fun attack(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#attack")

		if (isStarted.not()) return interaction.respondError(tag, "You can't partake in a combat that did not start")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")
		val target = targets[targetName]?.takeUnless { it.currentHp <= 0 } ?: return interaction.respondError(
			tag,
			"target_name must point to a living target"
		)
		val damage = interaction.command.integers["damage"]?.toInt()
		val dice = interaction.command.integers["roll"]?.toInt()

		if (damage == null && dice == null) return interaction.respondError(tag, "Neither damage nor roll was found")
		if (damage != null && dice != null) return interaction.respondError(
			tag,
			"Only damage or roll can be specified at the same time"
		)

		val rolled = dice?.let(::roll)
		val delt = damage ?: rolled?.sum() ?: throw IllegalStateException("Space particles detected")
		val actuallyDelt = delt - target.currentArmor

		val modified = target.copy(
			currentHp = (target.currentHp - actuallyDelt.coerceAtLeast(0)).coerceAtLeast(0),
			currentArmor = if (target.currentArmor != 0 && actuallyDelt >= 0)
				target.currentArmor - 1
			else
				target.currentArmor
		).also { targets[targetName] = it }

		interaction.respondPublic {
			content = buildString {
				append("<@${interaction.user.id.value}> ")
				append(
					when {
						actuallyDelt > 0 -> "delt"
						actuallyDelt == 0 -> "blocked incoming"
						else -> "neglected incoming"
					}
				)

				val wasCritical = (rolled?.count { it == 6 } ?: 0) >= 2
				if (wasCritical) {
					append(" critical")
				}
				if (rolled != null) {
					append(rolled.joinToString(separator = "+", prefix = " ||(", postfix = ")||"))
				}

				when {
					actuallyDelt > 0 -> {
						append(" **$actuallyDelt** damage")
						if (actuallyDelt != delt) {
							append(" reduced by ${target.currentArmor} armor")
						}
						append(" to **$targetName** ")
						if (modified.currentHp != 0) {
							val currentHpText = modified.currentHp.toString().takeUnless { modified.isHidden } ?: "???"
							val maxHpText = modified.maxHp.toString().takeUnless { modified.isHidden } ?: "???"
							append("hp[$currentHpText/${maxHpText}]")
							if (modified.currentArmor != 0) {
								append(" ap[${modified.currentArmor}/${modified.maxArmor}]")
							}
							append(" remaining ${settings.emojis.attack.orEmpty()}")
						} else {
							append("target flatlined ${settings.emojis.kill.orEmpty()}")
						}
					}

					actuallyDelt < 0 -> append(" **$delt** damage by the ${target.currentArmor} ap")
					else -> append(" **$delt** damage by the ${target.currentArmor} ap, the ap is reduced by 1")
				}
			}
		}
	}

	suspend fun edit(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#edit")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")
		val targetHp = interaction.command.integers["target_health"]?.toInt()
		val targetArmor = interaction.command.integers["target_armor"]?.toInt()
		val targetHidden = interaction.command.booleans["target_hidden"]
		val targetFriendly = interaction.command.booleans["target_friendly"]

		if (targetHp == null && targetArmor == null && targetHidden == null && targetFriendly == null)
			return interaction.respondError(
				tag,
				"Either target_health, target_armor, target_hidden or target_friendly has to be specified"
			)

		val modified = targets.compute(targetName) { _, target ->
			target?.copy(
				currentHp = targetHp ?: target.currentHp,
				maxHp = targetHp ?: target.maxHp,
				currentArmor = targetArmor ?: target.currentArmor,
				maxArmor = targetArmor ?: target.maxArmor,
				isHidden = targetHidden ?: target.isHidden,
				isFriendly = targetFriendly ?: target.isFriendly
			)
		}

		interaction.respondEphemeral {
			content = if (modified != null) "targets modified: $modified" else "target $targetName not found"
		}
	}

	suspend fun end(interaction: ChatInputCommandInteraction) {
		targets.clear()
		footerImage = null
		name = ""
		isStarted = false
		File(".combat.ares").delete()
		onTargetsChange()
		interaction.respondPublic {
			content = "**COMBAT ENDED_**"
		}
	}

	suspend fun heal(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#heal")

		if (isStarted.not()) return interaction.respondError(tag, "You can't partake in a combat that did not start")

		val targetName =
			interaction.command.strings["target_name"] ?: return interaction.respondError(tag, "target_name not found")
		val target = targets[targetName] ?: return interaction.respondError(
			tag,
			"target_name must point to an actual target"
		)
		val hp = interaction.command.integers["hp"]?.toInt() ?: return interaction.respondError(tag, "hp not found")

		val modified = target.copy(currentHp = (target.currentHp + hp).coerceAtMost(target.maxHp))
		targets[targetName] = modified

		interaction.respondPublic {
			content = buildString {
				append(
					"<@${interaction.user.id.value}> healed ${modified.currentHp - target.currentHp} " +
							"health of **$targetName** hp[${modified.currentHp}/${modified.maxHp}] remaining"
				)
			}
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
				color = Color(255, 0, 0)
				author {
					icon = settings.embedIcon
					name = this@Combat.name
				}
				targets.forEach { (targetName, target) ->
					field {
						inline = true
						name = targetName
						val aliveColor = when {
							target.isFriendly -> Text.Color.Blue
							target.maxHp >= settings.bossHpLevel -> Text.Color.Pink
							else -> Text.Color.Green
						}

						val status = when {
							target.isHidden -> "UNKNOWN".toText(color = Text.Color.Yellow)
							target.currentHp > 0 -> "ONLINE_".toText(color = aliveColor)
							else -> "OFFLINE".toText(color = Text.Color.Red)
						}
						val hpStats = when {
							target.isHidden -> "???/???".toText(color = Text.Color.Yellow)
							else -> "${target.currentHp.toStat()}/${target.maxHp.toStat()}".toText(color = if (target.currentHp > 0) aliveColor else Text.Color.Red)
						}
						val apStats = when {
							target.isHidden -> "???/???".toText(color = Text.Color.Yellow)
							else -> "${target.currentArmor.toStat()}/${target.maxArmor.toStat()}".toText(color = if (target.currentHp > 0) aliveColor else Text.Color.Red)
						}
						value =
							("st [".toText() + status + "]\nhp [" + hpStats + "]\nap [" + apStats + "]").toString()
					}
				}

				image = footerImage
			}
		}
		originalResponse?.edit(statusEmbed) ?: interaction.respondPublic(statusEmbed)
	}

	suspend fun start(interaction: ChatInputCommandInteraction) {
		val tag = MarkerFactory.getMarker("Combat#start")

		if (isStarted) return interaction.respondError(tag, "combat already started")

		isStarted = true
		footerImage = interaction.command.attachments.values.firstOrNull { it.isImage }?.url
		name = interaction.command.strings["name"] ?: return interaction.respondError(tag, "name not found")

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

	private fun roll(dice: Int) = List(dice) { Random.nextInt(1..6) }

	private fun Int.toStat() = toString().padStart(3, '0')
}