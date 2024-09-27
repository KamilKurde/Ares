import kotlinx.serialization.Serializable

@Serializable
data class Target(
	val name: String,
	val currentHp: Int,
	val maxHp: Int,
	val isHidden: Boolean = false,
	val isFriendly: Boolean = false
)
