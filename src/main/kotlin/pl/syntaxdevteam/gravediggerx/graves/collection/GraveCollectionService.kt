package pl.syntaxdevteam.gravediggerx.graves.collection

import pl.syntaxdevteam.gravediggerx.GraveDiggerX
import pl.syntaxdevteam.gravediggerx.graves.Grave
import pl.syntaxdevteam.gravediggerx.graves.GraveIdentity
import pl.syntaxdevteam.gravediggerx.graves.registry.GraveRegistry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GraveCollectionService(
    private val plugin: GraveDiggerX,
    private val registry: GraveRegistry
) {
    private val locks = ConcurrentHashMap.newKeySet<String>()

    fun begin(grave: Grave, collectorId: UUID): CollectionTicket? {
        val lockKey = GraveIdentity.locationKey(grave.location.toBlockLocation())
        if (!locks.add(lockKey)) {
            plugin.runtimeMetrics.incrementCollectionClaimConflict()
            return null
        }
        if (!plugin.databaseHandler.tryAcquireCollectionClaim(grave)) {
            locks.remove(lockKey)
            plugin.runtimeMetrics.incrementCollectionClaimConflict()
            return null
        }
        val ttl = plugin.config.getLong("graves.collection.claim-ttl-ms", 15000L).coerceAtLeast(3000L)
        val tx = plugin.databaseHandler.beginCollectionTx(grave, collectorId, ttl)
        if (tx == null) {
            locks.remove(lockKey)
            plugin.databaseHandler.releaseCollectionClaim(grave)
            plugin.runtimeMetrics.incrementCollectionClaimConflict()
            return null
        }
        return CollectionTicket(tx.txId, tx.graveId, collectorId)
    }

    fun markCollecting(grave: Grave, ticket: CollectionTicket): Boolean =
        plugin.databaseHandler.transitionCollectionTx(
            grave.graveId, ticket.txId, CollectionState.CLAIMED, CollectionState.COLLECTING
        )

    fun markCollected(grave: Grave, ticket: CollectionTicket): Boolean {
        if (plugin.databaseHandler.markCollectedTx(grave.graveId, ticket.txId)) return true
        val rolledBack = plugin.databaseHandler.transitionCollectionTx(
            grave.graveId,
            ticket.txId,
            CollectionState.COLLECTING,
            CollectionState.FAILED_RECOVERABLE,
            "safe rollback: COLLECTED persist failed"
        )
        if (!rolledBack) plugin.runtimeMetrics.incrementCollectionTxTransitionFail()
        return false
    }

    fun markFailed(grave: Grave, ticket: CollectionTicket, error: String?) {
        val transitioned = plugin.databaseHandler.transitionCollectionTx(
            grave.graveId, ticket.txId, CollectionState.COLLECTING, CollectionState.FAILED, error
        )
        if (!transitioned) plugin.runtimeMetrics.incrementCollectionTxTransitionFail()
    }

    fun release(grave: Grave) {
        locks.remove(GraveIdentity.locationKey(grave.location.toBlockLocation()))
        plugin.databaseHandler.releaseCollectionClaim(grave)
    }

    fun releaseById(identifier: UUID): Boolean {
        val grave = registry.findById(identifier) ?: return false
        release(grave)
        return true
    }

    fun forget(location: org.bukkit.Location) {
        locks.remove(GraveIdentity.locationKey(location.toBlockLocation()))
    }
}
