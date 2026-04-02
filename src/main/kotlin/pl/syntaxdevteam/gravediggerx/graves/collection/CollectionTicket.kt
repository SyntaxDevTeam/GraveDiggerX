package pl.syntaxdevteam.gravediggerx.graves.collection

import java.util.UUID

data class CollectionTicket(
    val txId: UUID,
    val graveId: UUID,
    val collectorId: UUID
)