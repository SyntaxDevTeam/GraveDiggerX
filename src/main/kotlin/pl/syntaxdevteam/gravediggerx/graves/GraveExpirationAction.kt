package pl.syntaxdevteam.gravediggerx.graves

enum class GraveExpirationAction {
    DROP_ITEMS,
    BECOME_PUBLIC,
    DISAPPEAR;

    companion object {
        fun fromString(value: String): GraveExpirationAction {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                DISAPPEAR
            }
        }
    }
}