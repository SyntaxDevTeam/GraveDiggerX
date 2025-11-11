package pl.syntaxdevteam.gravediggerx.database

/**
 * Lightweight representation of a grave row inside the SQL database.
 *
 * We persist the most commonly queried fields in dedicated columns so that
 * engines such as MySQL or PostgreSQL can be filtered/indexed efficiently,
 * while the full serialized grave payload (items, armor, metadata) is kept
 * in the [payload] column to avoid losing information.
 */
data class GraveRecord(
    val id: Int? = null,
    val graveKey: String,
    val ownerId: String,
    val ownerName: String,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val createdAt: Long,
    val storedXp: Int,
    val payload: String
)
