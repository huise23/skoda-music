package com.skodamusic.app.core.concurrent

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

class AppBackgroundExecutor(ioThreads: Int = 3) {
    private val ioThreadCounter = AtomicInteger(0)
    private val ioExecutor: ExecutorService = Executors.newFixedThreadPool(
        ioThreads,
        ThreadFactory { runnable ->
            Thread(runnable, "skoda-io-${ioThreadCounter.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
    )

    fun execute(task: () -> Unit) {
        ioExecutor.execute(task)
    }

    fun shutdown() {
        ioExecutor.shutdownNow()
    }
}
