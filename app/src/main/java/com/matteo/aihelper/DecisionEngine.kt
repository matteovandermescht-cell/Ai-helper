package com.matteo.aihelper

/** States are advisory. No state automatically triggers Android input. */
enum class GameState {
    INACTIVE, LOBBY, SEARCHING, LOADING, RUNNING, DEFEATED, RESULTS, UNCLEAR, ERROR
}

data class DecisionSnapshot(
    val state: GameState,
    val candidate: GameState,
    val consecutiveObservations: Int,
    val suggestion: String
)

/**
 * Conservative, debounced state estimation from actual detector outputs.
 * A single player detection is never enough to infer a running round.
 * SEARCHING/LOADING remain unavailable until the model exposes evidence for them.
 */
class DecisionEngine(
    private val confirmationsRequired: Int = 3,
    private val staleAfterMs: Long = 1000L
) {
    private var stableState = GameState.INACTIVE
    private var candidate = GameState.UNCLEAR
    private var consecutive = 0
    private var lastObservationMs = 0L

    fun observe(detections: List<Detection>, timestampMs: Long): DecisionSnapshot {
        val next = inferCandidate(detections)
        if (next == candidate) consecutive++ else {
            candidate = next
            consecutive = 1
        }
        lastObservationMs = timestampMs
        if (consecutive >= confirmationsRequired) stableState = candidate
        return snapshot(timestampMs)
    }

    fun snapshot(nowMs: Long): DecisionSnapshot {
        if (lastObservationMs == 0L || nowMs - lastObservationMs > staleAfterMs) {
            return DecisionSnapshot(
                GameState.UNCLEAR, candidate, consecutive,
                "Trockenlauf pausiert: keine aktuelle Beobachtung; keine Eingabe ausgelöst."
            )
        }
        // Do not keep advertising the previous state while a transition is unconfirmed.
        val visibleState = if (consecutive >= confirmationsRequired && candidate == stableState) {
            stableState
        } else {
            GameState.UNCLEAR
        }
        val suggestion = when (visibleState) {
            GameState.INACTIVE -> "Aufnahme inaktiv."
            GameState.LOBBY -> "Start sichtbar: Start nur vorschlagen; nicht automatisch tippen."
            GameState.SEARCHING -> "Spielsuche: warten; Zustand nicht automatisch verändern."
            GameState.LOADING -> "Laden: warten; keine Menüklickschleife."
            GameState.RUNNING -> "Runde wahrscheinlich: Hindernisse, Reichweite und Munition fehlen; nur beobachten."
            GameState.DEFEATED -> "Niederlagenanzeige bestätigt: Ergebnisphase abwarten."
            GameState.RESULTS -> "Wiederholen sichtbar: erneutes Spiel nur vorschlagen."
            GameState.UNCLEAR -> "Zustand unklar: keine Aktion vorschlagen."
            GameState.ERROR -> "Technischer Fehler: Steuerung gesperrt."
        }
        return DecisionSnapshot(visibleState, candidate, consecutive, suggestion)
    }

    fun reset() {
        stableState = GameState.INACTIVE
        candidate = GameState.UNCLEAR
        consecutive = 0
        lastObservationMs = 0L
    }

    private fun inferCandidate(detections: List<Detection>): GameState {
        val labels = detections.filter { it.confidence >= 0.5f }.map { it.label.lowercase() }.toSet()
        return when {
            "defeated_indicator" in labels -> GameState.DEFEATED
            "play_again_button" in labels -> GameState.RESULTS
            "play_button" in labels -> GameState.LOBBY
            "player" in labels && "enemy" in labels -> GameState.RUNNING
            else -> GameState.UNCLEAR
        }
    }
}
