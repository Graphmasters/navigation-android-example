package net.graphmasters.routing.routing.example.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Vibrator
import net.graphmasters.core.units.Duration

object SystemUtils {
    @SuppressLint("MissingPermission")
    fun vibrate(context: Context, duration: Duration) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(duration.milliseconds())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}