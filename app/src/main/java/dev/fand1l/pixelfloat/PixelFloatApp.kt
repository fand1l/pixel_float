package dev.fand1l.pixelfloat

import android.app.Application

class PixelFloatApp : Application() {

    lateinit var graph: PixelFloatGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = PixelFloatGraph(this)
        graph.start()
    }
}
