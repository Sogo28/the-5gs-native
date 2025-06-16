package com.aurelius.the_5gs

import android.Manifest
import android.media.Image
import android.opengl.EGLContext
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.aurelius.the_5gs.ar.ArFrameData
import com.aurelius.the_5gs.ar.ArFrameListener
import com.aurelius.the_5gs.ar.helpers.ARCoreSessionLifecycleHelper
import com.aurelius.the_5gs.ar.helpers.DisplayRotationHelper
import com.aurelius.the_5gs.ar.lib.ArFrameAnalyzer
import com.aurelius.the_5gs.ar.lib.FrameSynchronizer
import com.aurelius.the_5gs.ar.rendering.AppRenderer
import com.aurelius.the_5gs.ar.ui.lifecycle.ManageArSessionLifecycle
import com.aurelius.the_5gs.ar.ui.screens.ArScreen
import com.aurelius.the_5gs.ar.ui.screens.HomeScreen
import com.aurelius.the_5gs.common.Config
import com.google.ar.core.Config as ArCoreConfig
import com.aurelius.the_5gs.helpers.CameraPermissionHelper
import com.aurelius.the_5gs.media.VideoEncoderManager
import com.aurelius.the_5gs.network.GrpcStreamManager
import com.aurelius.the_5gs.network.QuicNetworkManager
import com.aurelius.the_5gs.network.builder.ArPacketFactory
import com.aurelius.the_5gs.proto.Landmark
import com.aurelius.the_5gs.proto.ServerToClientMessage
import com.aurelius.the_5gs.ui.theme.The5gsTheme
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import java.nio.ByteOrder
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), ArFrameListener {

    companion object{
        const val TAG = "MainActivity"
    }
    // Helpers and managers
    private lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private lateinit var streamManager : GrpcStreamManager
    private var videoEncoderManager: VideoEncoderManager? = null
    private lateinit var frameSynchronizer: FrameSynchronizer
    private var arFrameAnalyzer: ArFrameAnalyzer = ArFrameAnalyzer()
    private lateinit var appRendererInstance: AppRenderer
    private var glSurfaceView: GLSurfaceView? = null
    private var lastRecognizedGesture by mutableStateOf("")
    private var handLandmarks by mutableStateOf<List<Landmark>>(emptyList())


    // Ar variables
    private var sharedEglContextForEncoder: EGLContext? = null
    private var currentArFrameData by mutableStateOf<ArFrameData?>(null)
    private var isArActive by mutableStateOf(false)
    // Permissions
    private var isPermissionGranted by mutableStateOf(false)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        isPermissionGranted = CameraPermissionHelper.hasCameraPermission(this)
        if (!isPermissionGranted) {
            Toast.makeText(this, "Camera permission needed for AR", Toast.LENGTH_LONG).show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        init()
        setContent {
            The5gsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (isPermissionGranted) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (!isArActive) {
                                HomeScreen {
                                    isArActive = true
                                }
                            } else {
                                val rememberedAppRenderer = remember {
                                    Log.d(TAG, "Creating AppRenderer instance for ArScreen.")
                                    AppRenderer(
                                        sessionProvider = { arCoreSessionHelper.session },
                                        displayRotationHelper = displayRotationHelper,
                                        frameListener = this@MainActivity,
                                        onEglContextReady = { eglContext ->
                                            Log.i(TAG, "EGL Context Ready via AppRenderer: $eglContext")
                                            this@MainActivity.sharedEglContextForEncoder = eglContext
                                        }
                                        // BackgroundRenderer is created inside AppRenderer's onSurfaceCreated
                                    ).also {
                                        Log.d(TAG, "AppRenderer instance stored in MainActivity.")
                                        this@MainActivity.appRendererInstance = it
                                    }
                                }
                                ManageArSessionLifecycle(
                                    isArActive = isArActive,
                                    effectCallback = { startArEffect() },
                                    onDisposeCallback = { disposeArEffect() }
                                )
                                ArScreen(
                                    currentArFrameData = currentArFrameData,
                                    streamStatus = streamManager.streamStatus,
                                    appRenderer = appRendererInstance,
                                    onSurfaceViewCreatedCallback = { surfaceView ->
                                        glSurfaceView = surfaceView
                                        if(isArActive) glSurfaceView?.onResume()
                                    },
                                    onStopArSessionCallback = {
                                        stopArSession()
                                    },
                                    handLandmarks = handLandmarks,
                                    translationResult = lastRecognizedGesture
                                )
                            }
                        }
                    } else {
                        LaunchedEffect(Unit) {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        displayRotationHelper.onResume()
        if (isArActive) {
            glSurfaceView?.onResume()
            streamManager.startStreaming()
//            poseStreamManager.start()
//            videoStreamManager.startStream()
        }
        Log.d("MainActivityAR", "MainActivity onResume. isArActive: $isArActive")
    }
    override fun onPause() {
        super.onPause()
        displayRotationHelper.onPause()
        if (isArActive) {
            glSurfaceView?.onPause()
        }
        // Don't close stream on pause, ManageArSessionAndGrpcStreamLifecycle handles it when isArActive changes.
        Log.d("MainActivityAR", "MainActivity onPause. isArActive: $isArActive")
    }
    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivityAR", "MainActivity onDestroy: Closing gRPC stream and shutting down QuicNetworkManager.")
//        poseStreamManager.close()
        videoEncoderManager?.stop()
        streamManager.completeStream()
        QuicNetworkManager.shutdown() // Shutdown gRPC channel
    }
    override fun onNewArFrameAvailable(frame: Frame, camera: Camera) {
        if(!isArActive) return
        val trackingState = camera.trackingState
        if(trackingState != TrackingState.TRACKING) return
        val arFrameData = arFrameAnalyzer.extractArFrameData(frame, camera) ?: return
        currentArFrameData = arFrameData
        handleArFrame(arFrameData)
    }
    override fun onArSessionError(exception: Exception) {
        // This runs on the GL thread.
        Log.e("MainActivity", "AR Session Error from AppRenderer: ${exception.message}", exception)
        runOnUiThread {
            // Update UI or state based on the error
            Toast.makeText(this, "AR Error: ${exception.message}", Toast.LENGTH_LONG).show()
            // isArActive = false // Example
        }
    }
    private fun init(){
        QuicNetworkManager.initialize(this)
        displayRotationHelper = DisplayRotationHelper(this)
        arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
        arCoreSessionHelper.beforeSessionResume = {
            session ->
            Log.d(TAG, "beforeSessionResume : Configuration de la session ARCore...")
            val config = session.config

            if(session.isDepthModeSupported((ArCoreConfig.DepthMode.AUTOMATIC))){
                config.depthMode = ArCoreConfig.DepthMode.AUTOMATIC
                Log.i(TAG, "DepthMode configuré sur AUTOMATIC")
            }
            else{
                config.depthMode = ArCoreConfig.DepthMode.DISABLED
                Log.w(TAG, "Le mode de profondeur AUTOMATIC n'est pas supporté. Il est désactivé.")
            }

            session.configure(config)
        }
        isPermissionGranted = CameraPermissionHelper.hasCameraPermission(this)

        streamManager = GrpcStreamManager(
            serverHost = Config.GRPC_SERVER_HOST,
            serverPort = Config.GRPC_SERVER_PORT,
            onServerResponse = {
                response ->
                onServerResponseReceived(response)
            },
            onStreamError = {},
            onStreamCompleted = {}
        )

        frameSynchronizer = FrameSynchronizer(
            onPacketReady = { packet ->
                streamManager.sendPacket(packet)
            }
        )
    }
    private fun startArEffect(){
        if (isArActive) {
            Log.d("MainActivityAR", "AR is Active: Managing ARCore and gRPC stream.")
            arCoreSessionHelper.attachTo(lifecycle)
            streamManager.startStreaming()
        }
    }
    private fun handleArFrame(frameData: ArFrameData) {

        initVideoEncoderOnce()
        sendIntrinsicsOnce(frameData)

        val ts = frameData.timestamp
        if ( ts < videoEncoderManager?.lastQueuedVideoFrameToEncoderTimestamp!!) {
            Log.e(TAG, "Frame timeStamp smaller than last queued video frame to encoder. Skipping frame.")
            return
        }

        val texId = frameData.cameraTextureId ?: return
        val uvs = frameData.transformedUvCoords ?: return
        val surfaceWidth = frameData.cameraTextureWidth ?: return
        val surfaceHeight = frameData.cameraTextureHeight ?: return

        frameSynchronizer.addPoseData(frameData.timestamp, frameData.pose)
        videoEncoderManager?.queueFrame(
            textureId = texId,
            uvs = uvs,
            timestampNs = ts,
            width = surfaceWidth,
            height = surfaceHeight
        )
    }
    private fun onServerResponseReceived(response: ServerToClientMessage) {
        // --- PARTIE 1 : Gérer la réception du nom du geste ---
        runOnUiThread{
            if (response.hasTranslationResult()) {
                val newGesture = response.translationResult
                // Mettre à jour notre variable d'état avec le nouveau geste
                lastRecognizedGesture = newGesture
            }

            if (response.hasHandLandmarksResult()) {
                // Le thread réseau fait une seule chose : ajouter les landmarks à la queue.
                // C'est une opération très rapide et non-bloquante.
                handLandmarks = response.handLandmarksResult.landmarksList
                // Si la liste de landmarks est vide, on peut aussi effacer le texte du geste
                if (response.handLandmarksResult.landmarksList.isEmpty()) {
                    lastRecognizedGesture = ""
                }
            }
        }
    }
    private fun stopArSession(){
        Log.w("MainActivityAR", "Stop Ar button clicked. Changing isArctive to false.")
        isArActive = false // This triggers onDispose in ManageArSessionAndGrpcStreamLifecycle
    }
    private fun disposeArEffect(){
        Log.w("LifeCycleManager", "Stop Ar button clicked / App Diposed. Triggering dispose effect.")
        currentArFrameData = null
        streamManager.completeStream()
        videoEncoderManager?.stop()
        videoEncoderManager = null
        frameSynchronizer.clear()
        arCoreSessionHelper.session?.pause()
        displayRotationHelper.onPause()
        arCoreSessionHelper.detachFrom(lifecycle)
        handLandmarks = emptyList()
    }
    private fun initVideoEncoderOnce() {

        if(videoEncoderManager != null) return

        if(sharedEglContextForEncoder == null){
            Log.e("MainActivity AR : Video Encoder init" ,"No egl context created yet. ")
            return
        }

        val outPutWidth = displayRotationHelper.viewportWidth
        val outPutHeight = displayRotationHelper.viewportHeight


//        val outPutWidth = 640
//        val outPutHeight = 480
        videoEncoderManager = VideoEncoderManager(
            sharedEglContext = sharedEglContextForEncoder,
            onEncodedFrameCallback = { data, isKey, arCorePts ->
                frameSynchronizer.addEncodedVideo(arCorePts, data, isKey)
            },
            onFormatInfoCallback = { sps, pps ->
                frameSynchronizer.setSpsPpsData(sps, pps)
            },
            onErrorCallback = { error ->
                Log.e("MainActivity", "Encoder Error: $error")
                isArActive = false
            }
        )

        videoEncoderManager?.initialize(outPutWidth, outPutHeight)
    }
    private fun sendIntrinsicsOnce(frameData: ArFrameData){
        val intrinsics = frameData.cameraIntrinsics ?: return
        if (!streamManager.isIntrinsicsSent && !streamManager.intrinsicsConfirmedByServer){
            Log.i(TAG, "Trying to send video intrinsics")
            val protoIntrinsics = ArPacketFactory.createIntrinsicsProto(intrinsics = intrinsics)
            streamManager.sendIntrinsics(protoIntrinsics)
            return
        }
    }

}