package pl.syntaxdevteam.gravediggerx.graves.collection

import java.util.UUID

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