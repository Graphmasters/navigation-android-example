package net.graphmasters.routing.routing.example

import android.os.Handler
import net.graphmasters.core.Executor
import net.graphmasters.core.units.Duration
import net.graphmasters.routing.NavigationSdk
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AndroidExecutorProvider : NavigationSdk.ExecutorProvider {
    private val handler = Handler()

    override fun create(): Executor = AndroidExecutor(handler)

    private class AndroidExecutor(private val handler: Handler) : Executor {

        private val scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

        override fun execute(block: () -> Unit) {
            this.scheduledExecutorService.execute {
                block.invoke()
            }
        }

        override fun executeDelayed(delay: Duration, block: () -> Unit): Executor.Future {
            val schedule = this.scheduledExecutorService.schedule(
                { block.invoke() }, delay.milliseconds(), TimeUnit.MILLISECONDS
            )

            return object : Executor.Future {
                override fun cancel() {
                    schedule.cancel(true)
                }
            }
        }

        override fun runOnUiThread(block: () -> Unit) {
            handler.post {
                block.invoke()
            }
        }

        override fun schedule(updateRate: Duration, block: () -> Unit): Executor.Future {
            val schedule = this.scheduledExecutorService.scheduleAtFixedRate(
                { block.invoke() }, 0, updateRate.milliseconds(), TimeUnit.MILLISECONDS
            )

            return object : Executor.Future {
                override fun cancel() {
                    schedule.cancel(true)
                }
            }
        }
    }
}