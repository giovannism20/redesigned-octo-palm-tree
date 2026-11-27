package com.giovannimartins.rreader.pdf

import java.util.concurrent.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lets only one page at a time be drawn and recognized in the whole app, however many imports run, so the memory a
 * page picture and the recognizer take is spent once (ADR 0015). Imports take turns page by page.
 */
@Singleton
class OcrGate
    @Inject
    constructor() {
        private val permit = Semaphore(1, true)

        /**
         * Runs [block] while holding the gate. Waiting for it throws [InterruptedException] when the import is
         * cancelled.
         */
        fun <T> withPermit(block: () -> T): T {
            permit.acquire()
            try {
                return block()
            } finally {
                permit.release()
            }
        }
    }
