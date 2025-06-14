package com.aurelius.the_5gs.ar

import com.google.ar.core.Camera
import com.google.ar.core.Frame

interface ArFrameListener {
    fun onNewArFrameAvailable(frame: Frame, camera: Camera)
    fun onArSessionError(exception: Exception)
    // You could add other AR-related listener interfaces here in the future
}