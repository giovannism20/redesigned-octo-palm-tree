package com.giovannimartins.rreader.playback

import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

/**
 * Loads the requested book off the player's thread and remembers the request, so a failed load (or a book
 * without a voice) can be retried when the player is prepared again. Futures complete through [completion],
 * the player's thread, so a load cancelled by a newer request never completes in the middle of handling it.
 */
internal class BookLoader(
    private val scope: CoroutineScope,
    private val content: PlaybackContent,
    private val completion: Executor,
) {
    private var job: Job? = null

    /** The last book requested, or null when none is. */
    var request: BookRequest? = null
        private set

    val isLoading: Boolean get() = job?.isActive == true

    /**
     * Loads [request] and passes the book, or null when it is unavailable, to [onLoaded] on the player's thread.
     * The returned future completes after [onLoaded] ran, or when the load is cancelled.
     */
    fun load(
        request: BookRequest,
        onLoaded: (PlaybackBook?) -> Unit,
    ): ListenableFuture<*> {
        cancel()
        this.request = request
        val done = SettableFuture.create<Unit>()
        job =
            scope
                .launch {
                    val load = content.load(request.bookId)
                    onLoaded((load as? BookLoad.Loaded)?.book)
                }.apply { invokeOnCompletion { completion.execute { done.set(Unit) } } }
        return done
    }

    /** Cancels a load in progress. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Cancels any load and forgets the request. */
    fun forget() {
        cancel()
        request = null
    }
}
