package com.aurelius.the_5gs.ar.rendering

import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES30
import android.util.Log
import com.aurelius.the_5gs.ar.ArFrameData
import com.aurelius.the_5gs.ar.ArFrameListener
import com.aurelius.the_5gs.ar.ArPoseData
import com.aurelius.the_5gs.ar.helpers.DisplayRotationHelper
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import java.io.IOException


class AppRenderer(
    private val sessionProvider: () -> Session?,
    private val displayRotationHelper: DisplayRotationHelper,
//    private val onFrameUpdate: (ArFrameData) -> Unit,
    private val onEglContextReady: (eglContext: EGLContext) -> Unit,
    private var frameListener: ArFrameListener?,
) : SampleRender.Renderer {

    private val TAG = "AppRenderer"

    private lateinit var SRender: SampleRender
    private lateinit var BRenderer: BackgroundRenderer

    override fun onSurfaceCreated(render: SampleRender) {
        this.SRender = render
        // Récupérez le contexte EGL actuel (celui du GLSurfaceView)
        val currentEglContext = EGL14.eglGetCurrentContext()
        if (currentEglContext == null || currentEglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "ERREUR CRITIQUE: Impossible d'obtenir le contexte EGL actuel dans AppRenderer.onSurfaceCreated.")
            // Sans contexte valide ici, le partage échouera.
            // Vous pourriez appeler un callback d'erreur ou lancer une exception.
        } else {
            Log.i(TAG, "Contexte EGL de AppRenderer (GLSurfaceView) capturé : $currentEglContext")
            onEglContextReady(currentEglContext) // Notifiez que le contexte est disponible
        }

        GLES30.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        this.BRenderer =
            BackgroundRenderer(render)
        try {
            BRenderer.setUseDepthVisualization(render, false)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load shaders for BackgroundRenderer", e)
        }
        Log.d(TAG, "AppRenderer.onSurfaceCreated complete.")
    }

    override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        displayRotationHelper.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(render: SampleRender) {
        val session = sessionProvider() ?: return
        displayRotationHelper.updateSessionIfNeeded(session)

        var currentCameraTextureId: Int?
//        var currentTransformedUvs: FloatArray? = null
//        var currentTextureWidth: Int?
//        var currentTextureHeight: Int?

        try {
            val frame = session.update()
            val camera = frame.camera
            BRenderer.updateDisplayGeometry(frame)
            session.setCameraTextureName(BRenderer.cameraColorTexture.textureId)
            frameListener?.onNewArFrameAvailable(frame, camera)
//
//            val uvsFromBackgroundRenderer = FloatArray(8)
//            val bb = BRenderer.cameraTexCoords // Ceci est le FloatBuffer de BackgroundRenderer
//            bb.position(0) // Rembobiner avant de lire
//
//            if (bb.remaining() == 8) {
//                bb.get(uvsFromBackgroundRenderer)
//                currentTransformedUvs = uvsFromBackgroundRenderer
//
//            } else {
//                Log.e(TAG, "La taille des UVs de BackgroundRenderer est incorrecte: ${bb.remaining()}. Retour aux anciennes UVs (qui sont problématiques).")
//            }
//
//            val arCoreTextureIntrinsics = camera.textureIntrinsics
//            val imageDims = arCoreTextureIntrinsics.imageDimensions
//            currentTextureWidth = imageDims[0]
//            currentTextureHeight = imageDims[1]
//
//            val displayOrientedPoseArray = FloatArray(16)
//            camera.displayOrientedPose.toMatrix(displayOrientedPoseArray, 0)
//            val devicePose = camera.pose
//
//            val arPose = ArPoseData(
//                translation = devicePose.translation.clone(),
//                rotation = devicePose.rotationQuaternion.clone()
//            )
//
//            val acquiredImage: android.media.Image? = null
//            val cpuImageIntrinsics = camera.imageIntrinsics
//
//            val frameData = ArFrameData(
//                timestamp = frame.timestamp,
//                transform = displayOrientedPoseArray,
//                pose = arPose,
//                cameraImage = acquiredImage,
//                cameraIntrinsics = cpuImageIntrinsics,
//                trackingState = camera.trackingState,
//                cameraTextureId = currentCameraTextureId,
//                transformedUvCoords = currentTransformedUvs, // This now holds the 8 UV floats
//                cameraTextureWidth = currentTextureWidth,
//                cameraTextureHeight = currentTextureHeight
//            )
//
//
//            onFrameUpdate(frameData)

            // --- End Data Extraction ---

            BRenderer.drawBackground(render) // Renders to main display

            if (camera.trackingState == TrackingState.PAUSED) {
                return
            }
            // ... (other rendering for display) ...

        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "CameraNotAvailableException in onDrawFrame", e)
//            onFrameUpdate(ArFrameData(System.nanoTime(), null, ArPoseData(), null, null, TrackingState.PAUSED, null, null, null, null))
        } catch (t: Throwable) {
            Log.e(TAG, "Exception on GL thread in AppRenderer.onDrawFrame", t)
//            onFrameUpdate(ArFrameData(System.nanoTime(), null, ArPoseData(), null, null, TrackingState.PAUSED, null, null, null, null))
        }
    }


}
