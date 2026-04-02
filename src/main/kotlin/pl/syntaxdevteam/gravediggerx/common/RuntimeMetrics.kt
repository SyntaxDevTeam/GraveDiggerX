package pl.syntaxdevteam.gravediggerx.common

import java.util.concurrent.atomic.AtomicLong

class RuntimeMetrics {

    private val collectionClaimConflictTotal = AtomicLong(0)
    private val collectionTxStuckCurrent = AtomicLong(0)
    private val collectionTxTransitionFailTotal = AtomicLong(0)
    private val storageIoErrorsTotal = AtomicLong(0)
    private val cleanupDurationMsTotal = AtomicLong(0)
    private val cleanupDurationSamples = AtomicLong(0)
    private val cleanupItemsProcessedTotal = AtomicLong(0)

    fun incrementCollectionClaimConflict() {
        collectionClaimConflictTotal.incrementAndGet()
    }

    fun setCollectionTxStuckCurrent(value: Long) {
        collectionTxStuckCurrent.set(value.coerceAtLeast(0L))
    }

    fun incrementCollectionTxTransitionFail() {
        collectionTxTransitionFailTotal.incrementAndGet()
    }

    fun incrementStorageIoError() {
        storageIoErrorsTotal.incrementAndGet()
    }

    fun recordCleanup(durationMs: Long, processedItems: Long) {
        cleanupDurationMsTotal.addAndGet(durationMs.coerceAtLeast(0L))
        cleanupDurationSamples.incrementAndGet()
        cleanupItemsProcessedTotal.addAndGet(processedItems.coerceAtLeast(0L))
    }

    fun snapshot(gravesActiveTotal: Long): Snapshot {
        val cleanupSamples = cleanupDurationSamples.get().coerceAtLeast(1)
        val totalDuration = cleanupDurationMsTotal.get()
        return Snapshot(
            gravesActiveTotal = gravesActiveTotal,
            collectionClaimConflictTotal = collectionClaimConflictTotal.get(),
            collectionTxStuckTotal = collectionTxStuckCurrent.get(),
            collectionTxTransitionFailTotal = collectionTxTransitionFailTotal.get(),
            storageIoErrorsTotal = storageIoErrorsTotal.get(),
            cleanupDurationMs = totalDuration,
            cleanupDurationAvgMs = totalDuration / cleanupSamples,
            cleanupItemsProcessedTotal = cleanupItemsProcessedTotal.get()
        )
    }

    data class Snapshot(
        val gravesActiveTotal: Long,
        val collectionClaimConflictTotal: Long,
        val collectionTxStuckTotal: Long,
        val collectionTxTransitionFailTotal: Long,
        val storageIoErrorsTotal: Long,
        val cleanupDurationMs: Long,
        val cleanupDurationAvgMs: Long,
        val cleanupItemsProcessedTotal: Long
    )
}
