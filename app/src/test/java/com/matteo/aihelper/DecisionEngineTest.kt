package com.matteo.aihelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionEngineTest {
    @Test
    fun playerAloneDoesNotMeanRoundIsRunning() {
        val engine = DecisionEngine()
        var snapshot = engine.observe(listOf(Detection("player", 0.99f, 1f, 1f, 2f, 2f)), 100L)
        snapshot = engine.observe(listOf(Detection("player", 0.99f, 1f, 1f, 2f, 2f)), 300L)
        snapshot = engine.observe(listOf(Detection("player", 0.99f, 1f, 1f, 2f, 2f)), 500L)
        assertEquals(GameState.UNCLEAR, snapshot.state)
        assertTrue(snapshot.suggestion.contains("keine Aktion"))
    }

    @Test
    fun lobbyRequiresThreeConsistentObservations() {
        val engine = DecisionEngine()
        val lobby = listOf(Detection("play_button", 0.9f, 1f, 1f, 2f, 2f))
        assertEquals(GameState.INACTIVE, engine.observe(lobby, 100L).state)
        assertEquals(GameState.INACTIVE, engine.observe(lobby, 300L).state)
        assertEquals(GameState.LOBBY, engine.observe(lobby, 500L).state)
    }

    @Test
    fun exitButtonAloneDoesNotMeanDefeated() {
        val engine = DecisionEngine()
        val exit = listOf(Detection("exit_button", 0.95f, 1f, 1f, 2f, 2f))
        engine.observe(exit, 100L)
        engine.observe(exit, 300L)
        val snapshot = engine.observe(exit, 500L)
        assertEquals(GameState.UNCLEAR, snapshot.state)
    }

    @Test
    fun staleObservationBlocksSuggestion() {
        val engine = DecisionEngine()
        val lobby = listOf(Detection("play_button", 0.9f, 1f, 1f, 2f, 2f))
        engine.observe(lobby, 100L)
        engine.observe(lobby, 300L)
        engine.observe(lobby, 500L)
        val stale = engine.snapshot(2000L)
        assertEquals(GameState.UNCLEAR, stale.state)
        assertTrue(stale.suggestion.contains("veraltet") || stale.suggestion.contains("keine aktuelle"))
    }
}
