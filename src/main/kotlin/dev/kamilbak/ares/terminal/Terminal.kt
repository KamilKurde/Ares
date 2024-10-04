package dev.kamilbak.ares.terminal

import dev.kord.common.entity.Snowflake
import kotlin.random.Random
import kotlin.random.nextInt

data class Terminal(
	val hacker: Snowflake?,
	val attemptsRemaining: Int,
	val question: String,
	val answers: List<Answer>,
) {
	companion object {
		fun fromCommand(
			hacker: Snowflake?,
			difficulty: Int,
			unknowns: Int,
			viruses: Int,
			attemptsRemaining: Int,
		): Terminal {
			val correctAnswer: String

			@Suppress("RemoveExplicitTypeArguments")
			val answers = buildList<Answer> {
				val firstChar = Random.nextHex()

				fun String.isUnique() = this@buildList.none { (existing, _) -> existing == this }

				fun generateCode(difficulty: Int): String {
					val generated = buildString {
						append(firstChar)
						repeat(difficulty - 1) {
							append(Random.nextHex())
						}
					}

					return if (generated.isUnique()) generated else generateCode(difficulty)
				}

				correctAnswer = generateCode(difficulty)
				add(Answer(correctAnswer, Answer.Type.Correct))
				repeat(viruses) {
					var virusValue = correctAnswer
					do {
						virusValue = virusValue.toCharArray().apply {
							set(Random.nextInt(1..virusValue.lastIndex), Random.nextHex())
						}.concatToString()
					} while (virusValue.isUnique().not())
					add(Answer(virusValue, Answer.Type.Virus))
				}
				repeat(10 - size) {
					add(Answer(generateCode(difficulty), Answer.Type.Incorrect))
				}
			}.shuffled()

			return Terminal(hacker, attemptsRemaining, correctAnswer.obfuscate(unknowns), answers)
		}

		private fun String.obfuscate(chars: Int): String {
			val indicesToReplace = (1..lastIndex).shuffled().take(chars)

			return toCharArray().apply {
				indicesToReplace.forEach { index ->
					this[index] = '?'
				}
			}.concatToString()
		}

		private fun Random.nextHex() = nextInt(0..15).toString(16).uppercase().first()
	}

	data class Answer(val value: String, val type: Type) {
		enum class Type {
			Correct,
			Incorrect,
			Virus,
		}
	}
}