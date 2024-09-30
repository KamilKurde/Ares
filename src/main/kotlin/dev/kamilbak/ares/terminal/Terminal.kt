package dev.kamilbak.ares.terminal

import dev.kord.common.entity.Snowflake

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
				fun generateCode(difficulty: Int): String {
					val generated = buildString {
						repeat(difficulty) {
							append((0..15).random().toString(16).uppercase())
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
			val indicesToReplace = (0..lastIndex).shuffled().take(chars)

			return toCharArray().apply {
				indicesToReplace.forEach { index ->
					this[index] = '?'
				}
			}.concatToString()
		}
	}

	enum class AnswerType {
		Correct,
		Incorrect,
		Virus,
	}
}