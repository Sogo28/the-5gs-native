package com.aurelius.the_5gs.ar.ui.components


import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.aurelius.the_5gs.proto.Landmark

// Les connexions standard entre les landmarks de la main, fournies par MediaPipe
val HAND_CONNECTIONS = setOf(
    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), // Pouce
    Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), // Index
    Pair(5, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), // Majeur
    Pair(9, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), // Annulaire
    Pair(13, 17), Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20) // Auriculaire et paume
)

@Composable
fun HandSkeletonOverlay(
    landmarks: List<Landmark>,
    modifier: Modifier = Modifier
) {
    // On ne dessine que s'il y a des landmarks à afficher
    if (landmarks.isEmpty()) return

    Canvas(modifier = modifier) {
        // 'size' est la taille du Canvas (largeur et hauteur de l'écran)
        val canvasWidth = size.width
        val canvasHeight = size.height

        // --- Étape 1 : Dessiner les lignes de connexion (le squelette) ---
        HAND_CONNECTIONS.forEach { connection ->
            val startLandmark = landmarks.getOrNull(connection.first)
            val endLandmark = landmarks.getOrNull(connection.second)

            if (startLandmark != null && endLandmark != null) {
                drawLine(
                    color = Color.White,
                    start = Offset(startLandmark.x * canvasWidth, startLandmark.y * canvasHeight),
                    end = Offset(endLandmark.x * canvasWidth, endLandmark.y * canvasHeight),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round // Pour des lignes aux bouts arrondis
                )
            }
        }

        // --- Étape 2 : Dessiner les points sur chaque landmark ---
        landmarks.forEach { landmark ->
            drawCircle(
                color = Color.Cyan,
                radius = 8f,
                center = Offset(landmark.x * canvasWidth, landmark.y * canvasHeight)
            )
        }
    }
}