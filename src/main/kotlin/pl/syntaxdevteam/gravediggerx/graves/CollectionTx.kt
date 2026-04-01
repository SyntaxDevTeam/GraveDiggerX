package pl.syntaxdevteam.gravediggerx.graves

import java.util.UUID

enum class CollectionState {
    ACTIVE,
    CLAIMED,
    COLLECTING,
    COLLECTED,
    FAILED
}

data class CollectionTx(
    val txId: UUID,
    val graveId: UUID,
    val collectorId: UUID,
    val state: CollectionState,
    val version: Int,
    val updatedAt: Long,
    val expiresAt: Long,
    val lastError: String? = null
)

data class CollectionTicket(
    val txId: UUID,
    val graveId: UUID,
    val collectorId: UUID
)
