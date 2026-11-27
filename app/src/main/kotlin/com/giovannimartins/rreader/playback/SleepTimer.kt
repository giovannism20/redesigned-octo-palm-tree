package com.giovannimartins.rreader.playback

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** When the sleep timer stops listening. */
sealed interface SleepTimerSetting {
    /** After [duration] more of listening. */
    data class After(
        val duration: Duration,
    ) : SleepTimerSetting

    /** At the end of the chapter being listened to. */
    data object EndOfChapter : SleepTimerSetting
}

/** What the sleep timer is doing, as the player shows it. */
sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Listening stops after [remaining] more of it, rounded up to whole seconds. */
    data class After(
        val remaining: Duration,
    ) : SleepTimerState

    /** Listening stops at the end of the chapter being listened to. */
    data object EndOfChapter : SleepTimerState
}

/**
 * Stops listening after a while or at the end of the chapter, fading the volume out over the last [fade] so
 * the listener is not woken by an abrupt stop. It lives in the player, inside the playback service, because
 * Android 17 only lets a foreground service or a visible activity change the volume of audio (ADR 0014).
 *
 * The time of [SleepTimerSetting.After] runs only while audio plays ([Host.isPlaying]): pauses, phone calls and
 * waits for rendered audio do not use it up. For [SleepTimerSetting.EndOfChapter] the fade follows the audio
 * left in the chapter ([Host.chapterAudioLeftMillis]) and the player reports the end with [onChapterEnded].
 * When the timer ends it asks the [Host] to pause and only then restores the full volume. Confined to the
 * player's [looper]; [clock] schedules its ticks, so tests can drive it with a fake clock.
 */
@OptIn(UnstableApi::class)
internal class SleepTimer(
    private val clock: Clock,
    looper: Looper,
    private val host: Host,
    private val fade: Duration = DEFAULT_FADE,
) {
    /** The player as the timer needs it. Called on the player's thread. */
    interface Host {
        /** Whether audio is playing now: the time of [SleepTimerSetting.After] runs only then. */
        val isPlaying: Boolean

        /** Real time until the chapter's audio ends at the current speed; null while that is unknown. */
        fun chapterAudioLeftMillis(): Long?

        /** Sets the internal player's volume, from 0 to 1. */
        fun setVolume(volume: Float)

        /** Pauses listening because the timer ended; the position must be saved. */
        fun sleep()

        fun onStateChanged(state: SleepTimerState)
    }

    private val handler = clock.createHandler(looper, null)
    private val tick = Runnable { update() }
    private var setting: SleepTimerSetting? = null

    /** Listening time left for [SleepTimerSetting.After], as of [countingSince] while it counts. */
    private var leftMillis = 0L
    private var countingSince: Long? = null
    private var volume = FULL_VOLUME

    /** Whether the timer waits for the end of the chapter. */
    val isEndOfChapter: Boolean get() = setting == SleepTimerSetting.EndOfChapter

    /** Starts the timer, replacing any running one. */
    fun start(setting: SleepTimerSetting) {
        this.setting = setting
        leftMillis = (setting as? SleepTimerSetting.After)?.duration?.inWholeMilliseconds ?: 0
        countingSince = null
        onPlayingChanged()
    }

    /** Turns the timer off and restores the full volume. */
    fun cancel() {
        val wasOn = setting != null
        stop()
        applyVolume(FULL_VOLUME)
        if (wasOn) host.onStateChanged(SleepTimerState.Off)
    }

    /** The player started or stopped playing audio. */
    fun onPlayingChanged() {
        if (setting == null) return
        val now = clock.elapsedRealtime()
        val since = countingSince
        when {
            host.isPlaying && since == null -> {
                countingSince = now
            }

            !host.isPlaying && since != null -> {
                leftMillis -= now - since
                countingSince = null
            }
        }
        update()
    }

    /** The listener reached the end of a chapter: ends the timer there when it waits for it. */
    fun onChapterEnded() {
        if (isEndOfChapter) finish()
    }

    /** Stops ticking; the player is going away. */
    fun release() = stop()

    private fun update() {
        handler.removeCallbacks(tick)
        val setting = setting ?: return
        val left =
            when (setting) {
                is SleepTimerSetting.After -> listeningLeftMillis()
                SleepTimerSetting.EndOfChapter -> host.chapterAudioLeftMillis()
            }
        if (setting is SleepTimerSetting.After && left != null && left <= 0) {
            finish()
        } else {
            applyVolume(
                left?.let { (it.toFloat() / fade.inWholeMilliseconds).coerceIn(0f, FULL_VOLUME) } ?: FULL_VOLUME,
            )
            host.onStateChanged(stateOf(setting, left))
            if (countingSince != null) handler.postDelayed(tick, nextTickMillis(setting, left))
        }
    }

    /** Pauses, then restores the volume for the next listening and turns the timer off. */
    private fun finish() {
        stop()
        host.sleep()
        applyVolume(FULL_VOLUME)
        host.onStateChanged(SleepTimerState.Off)
    }

    private fun stop() {
        handler.removeCallbacks(tick)
        setting = null
        countingSince = null
    }

    private fun listeningLeftMillis(): Long = leftMillis - (countingSince?.let { clock.elapsedRealtime() - it } ?: 0)

    /**
     * The next check: during the fade every [FADE_STEP] (and exactly when a countdown ends); otherwise when the
     * shown countdown changes (each whole second) or the fade starts, whichever comes first, and every [POLL]
     * while the end of the chapter is unknown or further away.
     */
    private fun nextTickMillis(
        setting: SleepTimerSetting,
        left: Long?,
    ): Long {
        val fadeMillis = fade.inWholeMilliseconds
        val step = FADE_STEP.inWholeMilliseconds
        val countdown = setting is SleepTimerSetting.After
        return when {
            left == null -> POLL.inWholeMilliseconds
            left <= fadeMillis -> if (countdown) minOf(step, left) else step
            countdown -> minOf(left - fadeMillis, millisToNextSecond(left))
            else -> minOf(left - fadeMillis, POLL.inWholeMilliseconds)
        }
    }

    private fun applyVolume(target: Float) {
        if (target != volume) {
            volume = target
            host.setVolume(target)
        }
    }

    companion object {
        /** How long the volume takes to fade out before listening stops. */
        val DEFAULT_FADE = 10.seconds

        /** Volume steps during the fade: a hundred over ten seconds, too small to hear. */
        private val FADE_STEP = 100.milliseconds

        /** How often the end of the chapter is checked while it is unknown or far away. */
        private val POLL = 1.seconds

        private const val FULL_VOLUME = 1f
        private const val MILLIS_PER_SECOND = 1_000L

        private fun stateOf(
            setting: SleepTimerSetting,
            left: Long?,
        ): SleepTimerState =
            when (setting) {
                is SleepTimerSetting.After -> {
                    val seconds = ((left ?: 0) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
                    SleepTimerState.After(seconds.coerceAtLeast(0).seconds)
                }

                SleepTimerSetting.EndOfChapter -> {
                    SleepTimerState.EndOfChapter
                }
            }

        /** Time until [left] milliseconds cross the next whole second down, when the countdown changes. */
        private fun millisToNextSecond(left: Long): Long =
            (left % MILLIS_PER_SECOND).takeIf { it > 0 } ?: MILLIS_PER_SECOND
    }
}
