package pl.syntaxdevteam.gravediggerx.graves

import java.util.UUID

/**
 * Czysta logika stanu kolekcji przedmiotów z grobu, bez zależności od Bukkit/API storage.
 */
class CollectionTxStateMachine(
    private val persistence: Persistence,
    initialState: Collection<CollectionTx> = emptyList()
) {
    private val txById = LinkedHashMap<UUID, CollectionTx>()

    init {
        initialState.forEach { txById[it.txId] = it }
    }

    fun begin(
        graveId: UUID,
        collectorId: UUID,
        ttlMillis: Long,
        nowMillis: Long,
        txId: UUID = UUID.randomUUID()
    ): CollectionTx? {
        require(ttlMillis > 0) { "ttlMillis must be > 0" }

        val rollback = HashMap(txById)
        cleanupExpired(graveId, nowMillis)

        if (isGraveLocked(graveId)) {
            return null
        }

        val tx = CollectionTx(
            txId = txId,
            graveId = graveId,
            collectorId = collectorId,
            state = CollectionState.CLAIMED,
            version = 1,
            updatedAt = nowMillis,
            expiresAt = nowMillis + ttlMillis,
            lastError = null
        )

        txById[txId] = tx
        if (!persist()) {
            txById.clear()
            txById.putAll(rollback)
            return null
        }

        return tx
    }

    fun transition(
        graveId: UUID,
        txId: UUID,
        from: CollectionState,
        to: CollectionState,
        nowMillis: Long,
        error: String? = null
    ): Boolean {
        val current = txById[txId] ?: return false
        if (current.graveId != graveId || current.state != from) return false

        val rollback = current
        val updated = current.copy(
            state = to,
            version = current.version + 1,
            updatedAt = nowMillis,
            lastError = error
        )
        txById[txId] = updated

        if (!persist()) {
            txById[txId] = rollback
            return false
        }

        return true
    }

    fun clearGrave(graveId: UUID): Boolean {
        val rollback = HashMap(txById)
        txById.entries.removeIf { it.value.graveId == graveId }
        if (!persist()) {
            txById.clear()
            txById.putAll(rollback)
            return false
        }
        return true
    }

    fun get(txId: UUID): CollectionTx? = txById[txId]

    fun all(): List<CollectionTx> = txById.values.toList()

    fun cleanupExpired(graveId: UUID, nowMillis: Long) {
        txById.entries.removeIf {
            it.value.graveId == graveId &&
                it.value.state != CollectionState.COLLECTED &&
                it.value.expiresAt < nowMillis
        }
    }

    private fun isGraveLocked(graveId: UUID): Boolean {
        return txById.values.any {
            it.graveId == graveId && (it.state == CollectionState.CLAIMED || it.state == CollectionState.COLLECTING || it.state == CollectionState.COLLECTED)
        }
    }

    private fun persist(): Boolean = persistence.save(txById.values.toList())

    fun interface Persistence {
        fun save(snapshot: List<CollectionTx>): Boolean
    }
}
