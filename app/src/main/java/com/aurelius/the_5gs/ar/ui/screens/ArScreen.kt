package com.aurelius.the_5gs.ar.ui.screens

import android.opengl.EGLContext
import android.opengl.GLSurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aurelius.the_5gs.ar.ArFrameData
import com.aurelius.the_5gs.ar.rendering.AppRenderer
import com.aurelius.the_5gs.ar.ui.components.ArDataOverlay
import com.aurelius.the_5gs.ar.ui.components.ArView
import com.aurelius.the_5gs.ui.components.MyButton

@Composable
fun ArScreen(
    onSurfaceViewCreatedCallback: (surfaceView : GLSurfaceView)->Unit,
    onStopArSessionCallback: () -> Unit,
    currentArFrameData: ArFrameData?,
    appRenderer: AppRenderer,
    streamStatus : String
) {
    Box(Modifier.fillMaxSize()) {
        ArView(
            modifier = Modifier.fillMaxSize(),
            assetManager = LocalContext.current.assets,
            onSurfaceViewCreated = onSurfaceViewCreatedCallback,
            appRenderer = appRenderer,
        )

        ArDataOverlay(
            currentArFrameData = currentArFrameData,
            streamStatus = streamStatus
        )

        MyButton(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
            text = "Stop AR session",
            onClickCallBack = onStopArSessionCallback
        )
    }
}
