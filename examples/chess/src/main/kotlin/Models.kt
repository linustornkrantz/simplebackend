import com.prettybyte.simplebackend.lib.ModelProperties
import kotlinx.serialization.Serializable

@Serializable
data class GameProperties(
    val pieces: List<String>,
    val whitePlayerId: String,
    val blackPlayerId: String,
) : ModelProperties()

@Serializable
data class UserProperties(
    val userIdentityId: String,
    val firstName: String,
    val lastName: String,
    val victories: Int,
    val defeats: Int,
    val draws: Int,
) : ModelProperties()
