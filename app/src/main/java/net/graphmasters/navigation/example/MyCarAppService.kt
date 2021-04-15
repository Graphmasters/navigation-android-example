package net.graphmasters.navigation.example

import android.content.Intent
import android.graphics.Rect
import android.os.IBinder
import com.google.android.libraries.car.app.*
import com.google.android.libraries.car.app.model.*


class MyCarAppService : CarAppService(), SurfaceListener {
    override fun onCreateScreen(intent: Intent): Screen {
        return HelloWorldScreen(this.carContext)
    }

    override fun onBind(intent: Intent?): IBinder? {
        carContext.getCarService(AppManager::class.java).setSurfaceListener(this)
        return super.onBind(intent)


    }

    class HelloWorldScreen(carContext: CarContext) : Screen(carContext) {
        override fun getTemplate(): Template {
            val pane = Pane.Builder()
                .addRow(
                    Row.builder()
                        .setTitle("Hello world!")
                        .build()
                )
                .build()
            return PaneTemplate.builder(pane)
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        TODO("Not yet implemented")
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        TODO("Not yet implemented")
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        TODO("Not yet implemented")
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        TODO("Not yet implemented")
    }
}