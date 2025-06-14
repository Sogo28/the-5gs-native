package com.aurelius.the_5gs.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment


@Composable
fun MyButton(
    text: String,
    onClickCallBack: () -> Unit,
    modifier: Modifier
) {
    Button(
        modifier = modifier,
        onClick = {
            onClickCallBack()
        }
    ) {
        Text(text)
    }
}