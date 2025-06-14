package com.aurelius.the_5gs.ar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurelius.the_5gs.ui.components.MyButton

@Composable
fun HomeScreen (
    onStartArSessionCallback: () -> Unit
){
    Column(
        modifier = Modifier
            .fillMaxSize(), // Make the column take the full screen
        verticalArrangement = Arrangement.Center, // Vertically center children
        horizontalAlignment = Alignment.CenterHorizontally // Horizontally center children
    ) {
        MyButton(
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 48.dp),
            text = "Start AR session",
            onClickCallBack = onStartArSessionCallback
        )
    }
}