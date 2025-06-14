package com.aurelius.the_5gs.ar.ui.lifecycle

import android.opengl.GLSurfaceView
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import com.aurelius.the_5gs.ar.helpers.ARCoreSessionLifecycleHelper
import com.aurelius.the_5gs.media.VideoEncoderManager
import com.aurelius.the_5gs.media.VideoFileManager

@Composable
fun ManageArSessionLifecycle(
    isArActive: Boolean,
    onDisposeCallback: () -> Unit,
    effectCallback : () -> Unit,
) {
    DisposableEffect(isArActive) {
        effectCallback()

        onDispose {
            onDisposeCallback()
        }
    }
}
