package dev.kamilbak.ares.util

@JvmInline
value class Text private constructor(private val raw: String) {
	companion object {
		private const val ESC = "\u001B["
		fun from(string: String, decoration: Decoration = Decoration.Normal, color: Color? = null): Text {
			if (string.isBlank()) return Text(string)
			return Text(buildString {
				append(ESC)
				append(
					when (decoration) {
						Decoration.Normal -> 0
						Decoration.Bold -> 1
						Decoration.Underline -> 4
					}
				)
				if (color != null) {
					append(';')
					append(
						when (color) {
							Color.Gray -> 30
							Color.Red -> 31
							Color.Green -> 32
							Color.Yellow -> 33
							Color.Blue -> 34
							Color.Pink -> 35
							Color.Cyan -> 36
							Color.White -> 37
						}
					)
				}
				append('m')
				append(string)
				append(ESC)
				append("0m")
			})
		}
	}

	override fun toString(): String = buildString {
		append("```ansi\n")
		append(raw)
		append("\n```\n")
	}

	operator fun plus(other: String) = Text(this.raw + other)
	operator fun plus(other: Text) = Text(this.raw + other.raw)

	enum class Decoration {
		Normal,
		Bold,
		Underline,
	}

	enum class Color {
		Gray,
		Red,
		Green,
		Yellow,
		Blue,
		Pink,
		Cyan,
		White,
	}
}

fun String.toText(
	decoration: Text.Decoration = Text.Decoration.Normal,
	color: Text.Color? = null
): Text = Text.from(this, decoration, color)

fun buildText(build: TextBuilder.() -> Unit): Text {
	val builder = object : TextBuilder {
		var accumulated = "".toText()
		override fun append(text: Text) {
			accumulated += text
		}

		override fun append(text: String) {
			accumulated += text
		}
	}

	builder.build()

	return builder.accumulated
}

interface TextBuilder {
	fun append(text: Text)
	fun appendLine(text: Text) = append(text + "\n")
	fun append(text: String)
	fun appendLine(text: String) = append(text + "\n")
}
