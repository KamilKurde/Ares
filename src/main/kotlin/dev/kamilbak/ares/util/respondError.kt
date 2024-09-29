package dev.kamilbak.ares.util

import dev.kamilbak.ares.logger
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.entity.interaction.ChatInputCommandInteraction
import org.slf4j.Marker


suspend fun ChatInputCommandInteraction.respondError(tag: Marker, description: String) {
	logger.error(tag, description)
	respondEphemeral {
		content = description
	}
}