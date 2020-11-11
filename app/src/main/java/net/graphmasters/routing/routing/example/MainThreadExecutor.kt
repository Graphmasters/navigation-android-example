package net.graphmasters.routing.routing.example

import android.os.Handler
import net.graphmasters.core.Executor
import net.graphmasters.core.units.Duration

class MainThreadExecutor(private val handler: Handler) : Executor {

    override fun execute(block: () -> Unit) {
        handler.post(block)
    }

    override fun executeDelayed(delay: Duration, block: () -> Unit): Executor.Future {
        return object : Executor.Future {
            override fun cancel() {
            }
        }
    }

    override fun runOnUiThread(block: () -> Unit) {
        this.execute(block)
    }

    override fun schedule(updateRate: Duration, block: () -> Unit): Executor.Future {
        return object : Executor.Future {
            override fun cancel() {
            }
        }
    }
}