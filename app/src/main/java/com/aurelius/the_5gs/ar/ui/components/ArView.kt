package com.aurelius.the_5gs.ar.ui.components


import android.content.res.AssetManager
import android.opengl.EGLContext
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aurelius.the_5gs.ar.ArFrameData
import com.aurelius.the_5gs.ar.helpers.DisplayRotationHelper
import com.aurelius.the_5gs.ar.rendering.AppRenderer
import com.google.ar.core.Session
import com.aurelius.the_5gs.ar.rendering.SampleRender as JavaSampleRender

@Composable
fun ArView(
    modifier: Modifier = Modifier,
    appRenderer: AppRenderer,
    assetManager: AssetManager,
    onSurfaceViewCreated: (GLSurfaceView) -> Unit = {},
//    sessionProvider: () -> Session?,
//    displayRotationHelper: DisplayRotationHelper,
//    onFrameUpdate: (ArFrameData) -> Unit,
//    onEglContextReady: (eglContext: EGLContext) -> Unit
) {

//    // Remember your AppRenderer (which implements SampleRender.Renderer)
//    val appRenderer = remember {
//        AppRenderer(sessionProvider, displayRotationHelper, onFrameUpdate, onEglContextReady)
//    }

    AndroidView(
        factory = { ctx ->
            GLSurfaceView(ctx).apply {

                JavaSampleRender(
                    this, // Pass the GLSurfaceView instance
                    appRenderer, // Use the passed-in AppRenderer
                    assetManager
                )

                onSurfaceViewCreated(this)
            }
        },
        modifier = modifier.fillMaxSize()
    )
}