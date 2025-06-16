package com.aurelius.the_5gs.media

import android.opengl.EGLContext
import android.util.Log

class VideoEncoderManager(
    private val sharedEglContext: EGLContext?,
    private val onEncodedFrameCallback: (encodedData: ByteArray, isKeyFrame: Boolean, originalArCoreTimestampNs: Long) -> Unit,
    private val onFormatInfoCallback: (sps: ByteArray, pps: ByteArray) -> Unit,
    private val onErrorCallback: (String) -> Unit
) {
    private var videoEncoder: VideoEncoder? = null
    private var isVideoEncoderInitialized = false
    var lastQueuedVideoFrameToEncoderTimestamp = 0L
    private val warmUpDurationNs = 500_000_000L //
    private var encoderWarmUpTimestamp: Long = -1L

    fun initialize(width: Int, height: Int) {
        if (isVideoEncoderInitialized) return
        if (sharedEglContext == null) return
        // --- DÉBUT DE LA LOGIQUE DE CORRECTION DU FORMAT D'ASPECT ---

        // 1. Obtenir le format d’aspect original depuis le helper d’affichage.
        // Cela représente la forme de la vue que l’utilisateur est en train de voir.
        val originalAspectRatio = if (width > 0) {
            height.toFloat() / width.toFloat()
        } else {
            16.0f / 9.0f // Une valeur par défaut raisonnable si la vue n’est pas encore prête
        }

        // 2. Définir la largeur cible pour l’encodage. 640 est un excellent choix pour les performances.
        val targetWidth = 640

        // 3. Calculer la hauteur correspondante pour conserver le format d’aspect.
        val targetHeight = (targetWidth * originalAspectRatio).toInt()

        // 4. IMPORTANT : Aligner la hauteur pour qu’elle soit un multiple de 16 (ou au moins de 2).
        // Les encodeurs vidéo sont des composants matériels avec souvent des contraintes strictes
        // sur les dimensions d’entrée. Cela évite des erreurs d’encodage ou des distorsions supplémentaires.
        val alignedHeight = targetHeight - (targetHeight % 16)

        videoEncoder = VideoEncoder(
            width = targetWidth,
            height = alignedHeight,
            bitRate = 4 * 1024 * 1024,
            frameRate = 15,
            iFrameInterval = 1,
            onFormatInfoReady = { sps, pps ->
                onFormatInfoCallback(sps, pps)
            },
            onEncodedFrame = { data, isKeyFrame, arCorepts ->
                onEncodedFrameCallback(data.toByteArray(), isKeyFrame, arCorepts)
            },
            onError = { error ->
                onErrorCallback(error)
                stop()
            }
        )

        videoEncoder?.start(sharedEglContext)

        if(videoEncoder?.isRunning == true){
            isVideoEncoderInitialized = true
        }
    }

    fun queueFrame(
        textureId: Int,
        uvs: FloatArray,
        timestampNs: Long,
        width: Int,
        height: Int
    ) {
        if (encoderWarmUpTimestamp == -1L) {
            encoderWarmUpTimestamp = timestampNs + warmUpDurationNs
            Log.d("VideoEncoderManager", "Warm-up started. Will start encoding after: $encoderWarmUpTimestamp")
            return
        }

        if (timestampNs < encoderWarmUpTimestamp) {
            Log.d("VideoEncoderManager", "Skipping frame during warm-up: $timestampNs < $encoderWarmUpTimestamp")
            return
        }

        // Enforce increasing timestamps (MediaCodec requires this!)
        if (timestampNs <= lastQueuedVideoFrameToEncoderTimestamp) {
//            Log.w("VideoEncoderManager", "Dropping frame due to non-increasing timestamp: $timestampNs <= $lastQueuedVideoFrameToEncoderTimestamp")
            return
        }

        videoEncoder?.queueFrame(textureId, uvs, timestampNs, width, height)
        lastQueuedVideoFrameToEncoderTimestamp = timestampNs
    }

    fun stop() {
        Log.d("VideoEncoderManager", "Stopping Video Encoder")
        videoEncoder?.stop()
        videoEncoder = null
        isVideoEncoderInitialized = false
    }

    fun isInitialized(): Boolean = isVideoEncoderInitialized

}
