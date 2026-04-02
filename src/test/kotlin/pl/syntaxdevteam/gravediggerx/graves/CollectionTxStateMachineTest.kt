package pl.syntaxdevteam.gravediggerx.graves

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import pl.syntaxdevteam.gravediggerx.graves.collection.CollectionState
import pl.syntaxdevteam.gravediggerx.graves.collection.CollectionTx
import pl.syntaxdevteam.gravediggerx.graves.collection.CollectionTxStateMachine
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Suppress("RedundantSamConstructor")
class CollectionTxStateMachineTest {

    private val graveA = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
    private val graveB = UUID.fromString("00000000-0000-0000-0000-0000000000bb")
    private val collectorA = UUID.fromString("10000000-0000-0000-0000-0000000000aa")
    private val collectorB = UUID.fromString("20000000-0000-0000-0000-0000000000bb")
    private val txA = UUID.fromString("30000000-0000-0000-0000-0000000000aa")
    private val txB = UUID.fromString("40000000-0000-0000-0000-0000000000bb")

    @Test
    fun `begin creates CLAIMED transaction`() {
        val machine = newMachine()
        val tx = machine.begin(graveA, collectorA, ttlMillis = 5_000, nowMillis = 1000L, txId = txA)

        assertNotNull(tx)
        assertEquals(CollectionState.CLAIMED, tx.state)
        assertEquals(1, tx.version)
        assertEquals(6_000L, tx.expiresAt)
    }

    @Test
    fun `begin rejects second claim for same grave while CLAIMED`() {
        val machine = newMachine()
        assertNotNull(machine.begin(graveA, collectorA, 5_000, 1000L, txA))

        val duplicate = machine.begin(graveA, collectorB, 5_000, 1001L, txB)
        assertNull(duplicate)
    }

    @Test
    fun `begin allows parallel claims for different graves`() {
        val machine = newMachine()
        val first = machine.begin(graveA, collectorA, 5_000, 1000L, txA)
        val second = machine.begin(graveB, collectorB, 5_000, 1000L, txB)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(2, machine.all().size)
    }

    @Test
    fun `transition CLAIMED to COLLECTING succeeds`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)

        val changed = machine.transition(graveA, txA, CollectionState.CLAIMED, CollectionState.COLLECTING, 1200L)

        assertTrue(changed)
        assertEquals(CollectionState.COLLECTING, machine.get(txA)?.state)
        assertEquals(2, machine.get(txA)?.version)
    }

    @Test
    fun `transition COLLECTING to COLLECTED succeeds`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)
        machine.transition(graveA, txA, CollectionState.CLAIMED, CollectionState.COLLECTING, 1100L)

        val changed = machine.transition(graveA, txA, CollectionState.COLLECTING, CollectionState.COLLECTED, 1200L)

        assertTrue(changed)
        assertEquals(CollectionState.COLLECTED, machine.get(txA)?.state)
    }

    @Test
    fun `transition COLLECTING to FAILED stores error`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)
        machine.transition(graveA, txA, CollectionState.CLAIMED, CollectionState.COLLECTING, 1100L)

        val changed = machine.transition(graveA, txA, CollectionState.COLLECTING, CollectionState.FAILED, 1200L, "inventory full")

        assertTrue(changed)
        val tx = machine.get(txA)
        assertEquals(CollectionState.FAILED, tx?.state)
        assertEquals("inventory full", tx?.lastError)
    }

    @Test
    fun `transition fails when from state is invalid`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)

        val changed = machine.transition(graveA, txA, CollectionState.COLLECTING, CollectionState.COLLECTED, 1200L)

        assertFalse(changed)
        assertEquals(CollectionState.CLAIMED, machine.get(txA)?.state)
    }

    @Test
    fun `transition fails when tx belongs to different grave`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)

        val changed = machine.transition(graveB, txA, CollectionState.CLAIMED, CollectionState.COLLECTING, 1200L)

        assertFalse(changed)
    }

    @Test
    fun `collected transaction enforces exactly once and blocks new begin`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)
        machine.transition(graveA, txA, CollectionState.CLAIMED, CollectionState.COLLECTING, 1200L)
        machine.transition(graveA, txA, CollectionState.COLLECTING, CollectionState.COLLECTED, 1300L)

        val duplicateTrigger = machine.begin(graveA, collectorB, 5_000, 1400L, txB)

        assertNull(duplicateTrigger)
    }

    @Test
    fun `expired CLAIMED transaction is cleaned and allows new begin`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, ttlMillis = 100, nowMillis = 1000L, txId = txA)

        val recovered = machine.begin(graveA, collectorB, ttlMillis = 5_000, nowMillis = 1200L, txId = txB)

        assertNotNull(recovered)
        assertNull(machine.get(txA))
    }

    @Test
    fun `expired COLLECTING transaction is cleaned and allows recovery`() {
        val initial = CollectionTx(
            txId = txA,
            graveId = graveA,
            collectorId = collectorA,
            state = CollectionState.COLLECTING,
            version = 2,
            updatedAt = 1000L,
            expiresAt = 1050L
        )
        val machine = newMachine(initial)

        val recovered = machine.begin(graveA, collectorB, ttlMillis = 5_000, nowMillis = 1200L, txId = txB)

        assertNotNull(recovered)
        assertNull(machine.get(txA))
        assertEquals(CollectionState.CLAIMED, machine.get(txB)?.state)
    }

    @Test
    fun `expired COLLECTED transaction is NOT cleaned`() {
        val initial = CollectionTx(
            txId = txA,
            graveId = graveA,
            collectorId = collectorA,
            state = CollectionState.COLLECTED,
            version = 3,
            updatedAt = 1000L,
            expiresAt = 1050L
        )
        val machine = newMachine(initial)

        val newBegin = machine.begin(graveA, collectorB, ttlMillis = 5_000, nowMillis = 1200L, txId = txB)

        assertNull(newBegin)
        assertEquals(CollectionState.COLLECTED, machine.get(txA)?.state)
    }

    @Test
    fun `clearGrave removes all transactions for grave`() {
        val machine = newMachine()
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)
        machine.begin(graveB, collectorB, 5_000, 1000L, txB)

        val cleared = machine.clearGrave(graveA)

        assertTrue(cleared)
        assertNull(machine.get(txA))
        assertNotNull(machine.get(txB))
    }

    @Test
    fun `begin rolls back when persistence save fails`() {
        val machine = CollectionTxStateMachine(
            persistence = CollectionTxStateMachine.Persistence {
                false
            }
        )

        val tx1 = machine.begin(graveA, collectorA, 5_000, 1000L, txA)
        val tx2 = machine.begin(graveB, collectorB, 5_000, 1000L, txB)

        assertNull(tx1)
        assertNull(tx2)
        assertTrue(machine.all().isEmpty())
    }

    @Test
    fun `transition rolls back when persistence save fails`() {
        val saves = AtomicInteger(0)
        val machine = CollectionTxStateMachine(
            persistence = CollectionTxStateMachine.Persistence { saves.incrementAndGet() == 1 }
        )
        machine.begin(graveA, collectorA, 5_000, 1000L, txA)

        val changed = machine.transition(graveA, txA, CollectionState.CLAIMED, CollectionState.COLLECTING, 1100L)

        assertFalse(changed)
        assertEquals(CollectionState.CLAIMED, machine.get(txA)?.state)
    }

    @Test
    fun `multi grave parallel begin does not collide`() {
        val machine = newMachine()
        val pool = Executors.newFixedThreadPool(4)
        val latch = CountDownLatch(2)

        pool.submit {
            machine.begin(graveA, collectorA, 5_000, 1000L, txA)
            latch.countDown()
        }
        pool.submit {
            machine.begin(graveB, collectorB, 5_000, 1000L, txB)
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertEquals(2, machine.all().size)
        pool.shutdownNow()
    }

    @Test
    fun `double trigger collect only one path can pass full state flow`() {
        val machine = newMachine()
        val primary = machine.begin(graveA, collectorA, 5_000, 1000L, txA)
        val secondary = machine.begin(graveA, collectorB, 5_000, 1000L, txB)

        assertNotNull(primary)
        assertNull(secondary)

        assertTrue(machine.transition(graveA, txA, CollectionState.CLAIMED, CollectionState.COLLECTING, 1100L))
        assertTrue(machine.transition(graveA, txA, CollectionState.COLLECTING, CollectionState.COLLECTED, 1200L))
        assertFalse(machine.transition(graveA, txA, CollectionState.COLLECTING, CollectionState.COLLECTED, 1300L))
    }

    @Test
    fun `recovery crash path keeps COLLECTING before ttl timeout`() {
        val initial = CollectionTx(
            txId = txA,
            graveId = graveA,
            collectorId = collectorA,
            state = CollectionState.COLLECTING,
            version = 2,
            updatedAt = 1000L,
            expiresAt = 5000L
        )
        val machine = newMachine(initial)

        val beginBeforeTtl = machine.begin(graveA, collectorB, ttlMillis = 2000L, nowMillis = 2000L, txId = txB)

        assertNull(beginBeforeTtl)
        assertEquals(CollectionState.COLLECTING, machine.get(txA)?.state)
    }

    private fun newMachine(vararg initial: CollectionTx): CollectionTxStateMachine {
        return CollectionTxStateMachine(
            persistence = CollectionTxStateMachine.Persistence { true },
            initialState = initial.toList()
        )
    }
}
