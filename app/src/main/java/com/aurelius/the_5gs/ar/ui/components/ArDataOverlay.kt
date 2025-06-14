package com.aurelius.the_5gs.ar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.TrackingState
import com.aurelius.the_5gs.ar.ArFrameData

@Composable
fun BoxScope.ArDataOverlay(
    currentArFrameData: ArFrameData?,
    streamStatus : String,
) {
    currentArFrameData?.let { data ->
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(8.dp)
        ) {
            Text(
                "Tracking: ${data.trackingState?.name ?: "UNKNOWN"}",
                color = when (data.trackingState) {
                    TrackingState.TRACKING -> Color.Green
                    TrackingState.PAUSED -> Color.Yellow
                    else -> Color.Red
                },
                fontSize = 10.sp
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text("Stream Status: $streamStatus", color = Color.Magenta, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))

        }
    }
}
