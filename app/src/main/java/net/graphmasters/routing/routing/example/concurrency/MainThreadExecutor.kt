package net.graphmasters.routing.routing.example.concurrency

import android.os.Handler
import java.util.concurrent.Executor

class MainThreadExecutor(private val handler: Handler) : Executor {
    override fun execute(command: Runnable) {
        this.handler.post(command)
    }
}