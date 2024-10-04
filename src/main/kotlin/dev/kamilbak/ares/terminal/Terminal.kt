package dev.kamilbak.ares.terminal

import dev.kord.common.entity.Snowflake
import kotlin.random.Random
import kotlin.random.nextInt

data class Terminal(
	val hacker: Snowflake?,
	val attemptsRemaining: Int,
	val question: String,
	val answers: List<Pair<String, AnswerType>>,
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
			val answers = buildList<Pair<String, AnswerType>> {
				val firstChar = Random.nextHex()
				fun generateCode(difficulty: Int): String {
					val generated = buildString {
						append(firstChar)
						repeat(difficulty - 1) {
							append(Random.nextHex())
						}
					}

					val isUnique = none { (existing, _) -> existing == generated }

					return if (isUnique) generated else generateCode(difficulty)
				}

				correctAnswer = generateCode(difficulty)
				add(correctAnswer to AnswerType.Correct)
				repeat(viruses) {
					add(generateCode(difficulty) to AnswerType.Virus)
				}
				repeat(10 - size) {
					add(generateCode(difficulty) to AnswerType.Incorrect)
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

	enum class AnswerType {
		Correct,
		Incorrect,
		Virus,
	}
}