package com.example.smsguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SIM change detection drives real SMS alerts, so a false positive has to be impossible for an
 * unchanged set of SIMs. These tests lock in the behaviour that a transient empty reading must not
 * be mistaken for every SIM being freshly inserted.
 */
class SimSnapshotTest {

    private fun sim(slot: Int, fingerprint: String, description: String = "Carrier $slot") =
        SimSnapshot(slot, fingerprint, description)

    @Test
    fun encodeAllDecodeAll_roundTrips() {
        val sims = listOf(
            sim(0, "aaaa", "Ethio telecom (636/01)"),
            sim(1, "bbbb", "SAFARICOM (636/02)")
        )
        assertEquals(sims, SimSnapshot.decodeAll(SimSnapshot.encodeAll(sims)))
    }

    @Test
    fun encodeAll_decodeAll_ignoresMalformedEntries() {
        assertTrue(SimSnapshot.decodeAll("garbage||1~bbbb~Carrier").size == 1)
    }

    @Test
    fun changed_isEmptyWhenSimsAreUnchanged() {
        val sims = listOf(sim(0, "aaaa"), sim(1, "bbbb"))
        assertTrue(SimSnapshot.changed(sims, sims).isEmpty())
    }

    @Test
    fun changed_detectsSwappedCard() {
        val previous = listOf(sim(0, "aaaa"), sim(1, "bbbb"))
        val current = listOf(sim(0, "aaaa"), sim(1, "cccc"))
        assertEquals(listOf(sim(1, "cccc")), SimSnapshot.changed(previous, current))
    }

    @Test
    fun changed_detectsInsertedCard() {
        val previous = listOf(sim(0, "aaaa"))
        val current = listOf(sim(0, "aaaa"), sim(1, "bbbb"))
        assertEquals(listOf(sim(1, "bbbb")), SimSnapshot.changed(previous, current))
    }

    @Test
    fun changed_ignoresPlainRemoval() {
        val previous = listOf(sim(0, "aaaa"), sim(1, "bbbb"))
        val current = listOf(sim(0, "aaaa"))
        assertTrue(SimSnapshot.changed(previous, current).isEmpty())
    }

    @Test
    fun changed_ignoresReinsertedSameCard() {
        // A card removed and re-inserted keeps its fingerprint, so it must not alert.
        val baseline = listOf(sim(0, "aaaa"))
        assertTrue(SimSnapshot.changed(baseline, baseline).isEmpty())
    }
}
