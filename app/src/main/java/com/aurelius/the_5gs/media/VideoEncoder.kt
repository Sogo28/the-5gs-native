package com.aurelius.the_5gs.media // Replace with your actual package name

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import android.view.Surface
import com.aurelius.the_5gs.ar.rendering.GLRenderer
import com.google.protobuf.ByteString
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Encodes video from an ARCore GPU texture using MediaCodec and OpenGL ES.
 *
 * This class sets up a MediaCodec for H.264 encoding with a Surface input,
 * manages its own EGL context and GLES resources on a dedicated rendering thread,
 * accepts ARCore's camera texture ID and related information, renders the texture
 * onto the MediaCodec's input Surface, and dequeues encoded data on a separate thread.
 */
class VideoEncoder(
    val width: Int,
    val height: Int,
    val bitRate: Int,
    val frameRate: Int,
    val iFrameInterval: Int,
    private val onFormatInfoReady: (sps: ByteArray, pps: ByteArray) -> Unit,
    val onEncodedFrame: (data: ByteString, isKeyFrame: Boolean, originalArCoreTimestampNs: Long) -> Unit,
    val onError: (message: String) -> Unit
) {

    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE_H264 = "video/avc"
        private const val TIMEOUT_USEC = 20000L // 20 milliseconds
        private const val RENDER_THREAD_JOIN_TIMEOUT_MS = 3000L
        private const val ENCODING_THREAD_JOIN_TIMEOUT_MS = 3000L
    }

    private var glRenderer : GLRenderer = GLRenderer(width, height)

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY

    private var renderHandlerThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private var encodingThread: Thread? = null

    @Volatile
    var isRunning: Boolean = false
        private set
    private var isEncoderSetup: Boolean = false
    private var formatInfoSent = false

    private val submittedArCoreTimestampNsQueue = ConcurrentLinkedQueue<Long>()

    fun start(sharedEglContext : EGLContext) {
        if (isRunning) {
            Log.w(TAG, "Encoder is already running.")
            return
        }

        Log.i(TAG, "Starting encoder for ${width}x$height @ $bitRate bps, $frameRate fps, I-frame interval: $iFrameInterval s")
        Log.i(TAG, "Starting encoder with shared EGLContext: $sharedEglContext")

        if (sharedEglContext == EGL14.EGL_NO_CONTEXT) {
            Log.w(TAG, "Warning: Starting VideoEncoder with EGL14.EGL_NO_CONTEXT or null shared context. Texture sharing might not work.")
            return
        }

        submittedArCoreTimestampNsQueue.clear()

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE_H264, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)


            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE_H264)
            Log.d(TAG, "Configuring MediaCodec with format: $format")
            mediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = mediaCodec!!.createInputSurface()
            mediaCodec!!.start()

            renderHandlerThread = HandlerThread("VideoEncoderRenderThread", Process.THREAD_PRIORITY_URGENT_DISPLAY)
            renderHandlerThread!!.start()
            renderHandler = Handler(renderHandlerThread!!.looper)

            val localInputSurface = inputSurface
                ?: throw IOException("MediaCodec input surface was null after creation.")

            val eglInitLatch = CountDownLatch(1)
            // Initialize EGL and GLES on the render thread
            // Use a CountDownLatch or similar if start() needs to be synchronous with EGL/GLES setup
            renderHandler?.post {
                try {
                    glRenderer.initEGL(localInputSurface, sharedEglContext)
                    glRenderer.initGLES()
                    isEncoderSetup = true
                    Log.i(TAG, "EGL and GLES initialized successfully.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing EGL/GLES", e)
                    onError("EGL/GLES initialization failed: ${e.message}")
                    // Attempt to clean up if initialization fails mid-way
                    releaseInternalResources(true) // Pass true if called from render thread
                    isRunning = false // Ensure loops don't continue
                    isEncoderSetup = false
                }
                finally {
                    eglInitLatch.countDown() // Unblock the calling thread
                }
            }

            // Block until EGL is initialized or timeout
            if (!eglInitLatch.await(3, TimeUnit.SECONDS)) {
                Log.e(TAG, "Timeout waiting for EGL to initialize.")
                onError("Timeout initializing EGL")
                return
            }

            encodingThread = Thread(this::drainEncoder, "VideoEncoderDrainThread")
            encodingThread!!.start()

            isRunning = true
            Log.i(TAG, "Encoder started successfully.")

        } catch (e: Exception) { // Catches IOException from createEncoderByType, IllegalStateException from configure/start, etc.
            Log.e(TAG, "Failed to start MediaCodec video encoder", e)
            onError("Failed to start video encoder: ${e.message}")
            releaseResources() // Clean up any partially initialized resources
            return
        }
    }

    fun stop() {
        if (!isRunning &&!isEncoderSetup) { // if it never fully started or already stopped
            Log.i(TAG, "Encoder not running or not set up, stop called.")
            // Ensure threads are definitely not lingering if isRunning is false but threads might exist
            gracefullyStopThreads()
            releaseInternalResources(false) // Attempt cleanup if necessary
            return
        }

        Log.i(TAG, "Stopping encoder...")
        isRunning = false // Signal loops to stop

        val eosLatch = CountDownLatch(1)
        // Signal EOS to MediaCodec on the render thread to ensure it's done in order
        // This is more robust if there are pending rendering tasks.
        renderHandler?.post {
            if (mediaCodec!= null && isEncoderSetup) {
                try {
                    mediaCodec?.signalEndOfInputStream()
                    Log.i(TAG, "Signaled EOS to MediaCodec.")
                } catch (e: IllegalStateException) {
                    Log.e(TAG, "Failed to signal EOS, codec might not be in a valid state.", e)
                } finally {
                    eosLatch.countDown()
                }
            }
        }

        try {
            if (!eosLatch.await(2, TimeUnit.SECONDS)) {
                Log.w(TAG, "Timeout waiting for EOS to be posted.")
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for EOS signal.", e)
        }

        encodingThread?.join()
        gracefullyStopThreads()
        releaseResources() // Full resource release

        Log.i(TAG, "Encoder stopped.")
    }

    private fun gracefullyStopThreads() {
        // Stop and join render thread
        renderHandlerThread?.quitSafely()
        try {
            renderHandlerThread?.join(RENDER_THREAD_JOIN_TIMEOUT_MS)
            if (renderHandlerThread?.isAlive == true) {
                Log.w(TAG, "Render thread did not terminate in time.")
            }
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted while joining render thread.", e)
            Thread.currentThread().interrupt()
        }
        renderHandler = null
        renderHandlerThread = null

        // Stop and join encoding thread
        encodingThread?.interrupt() // Interrupt to break dequeueOutputBuffer potentially
        try {
            encodingThread?.join(ENCODING_THREAD_JOIN_TIMEOUT_MS)
            if (encodingThread?.isAlive == true) {
                Log.w(TAG, "Encoding thread did not terminate in time.")
            }
        } catch (e: InterruptedException) {
            Log.w(TAG, "Interrupted while joining encoding thread.", e)
            Thread.currentThread().interrupt()
        }
        encodingThread = null
    }

    /**
     * Releases all resources. Should be called when the encoder is no longer needed.
     * This method attempts to release MediaCodec resources first, then EGL/GLES.
     * EGL/GLES release should ideally happen on the render thread if it's still active.
     * If threads are already stopped, it performs cleanup directly.
     */
    private fun releaseResources() {
        Log.i(TAG, "Releasing all encoder resources.")

        // If render thread is still around (e.g. error during stop), post EGL/GLES release to it.
        // Otherwise, if threads are confirmed dead, release directly (carefully).
        val wasRenderThreadActive = renderHandlerThread?.isAlive?: false
        if (wasRenderThreadActive && renderHandler!= null) {
            renderHandler?.post {
               glRenderer.releaseEGL()
               glRenderer.releaseGLES()
            }
            // Wait for this posted task to complete before releasing MediaCodec
            // This can be complex. Simpler: ensure threads are joined first in stop().
        }

        // Gracefully stop threads if not already done (e.g. if releaseResources is called directly)
        if (isRunning || renderHandlerThread?.isAlive == true || encodingThread?.isAlive == true) {
            Log.w(TAG, "Resources released while threads potentially active. Attempting graceful stop first.")
            stop() // This will call releaseResources again, but isRunning will be false.
            // To avoid re-entrancy issues, use a flag or structure this carefully.
            // For now, assume stop() handles this or this is a final cleanup.
            return // stop() will call releaseResources again after thread shutdown
        }

        // At this point, threads should be stopped.
        releaseInternalResources(false) // false as it's likely not on render thread now
    }

    /**
     * Internal resource release logic.
     * @param onRenderThreadHint A hint if this is being called from the render thread,
     *                           for EGL/GLES cleanup.
     */
    private fun releaseInternalResources(onRenderThreadHint: Boolean) {
        Log.d(TAG, "Executing internal resource release. OnRenderThreadHint: $onRenderThreadHint")
        try {
            mediaCodec?.stop()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaCodec.stop() failed during release", e)
        }
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e(TAG, "MediaCodec.release() failed during release", e)
        }
        mediaCodec = null

        inputSurface?.release()
        inputSurface = null

        if (onRenderThreadHint) { // If called from render thread (e.g. during its own error handling)
            glRenderer.releaseEGL()
            glRenderer.releaseGLES()
        } else {
            // If not on render thread, EGL/GLES cleanup is tricky if context was bound.
            // Best effort: if display is valid, try to make context non-current and release.
            // This assumes the render thread is already gone or never fully started.
            if (eglDisplay!= EGL14.EGL_NO_DISPLAY) {
                Log.w(TAG, "Releasing EGL/GLES resources outside render thread. This might be problematic if context was active.")
                // Attempt to make context non-current on the current thread, though it might not be the EGL thread.
                // This is a last-ditch effort. Proper cleanup happens via Handler post or on thread exit.
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
//                releaseEGL() // releaseEGL itself checks eglDisplay
//                releaseGLES() // releaseGLES itself checks glProgram
                glRenderer.releaseEGL()
                glRenderer.releaseGLES()
            }
        }
        isEncoderSetup = false
    }

    fun queueFrame(textureId: Int, transformedUvs: FloatArray, timestampNs: Long, cameraTextureWidth : Int, cameraTextureHeight: Int) {

        if (!isRunning || !isEncoderSetup || renderHandler == null) {
            Log.w(TAG, "Encoder not running, not setup, or handler is null. Dropping frame.")
            return
        }

        if (transformedUvs.size!= 8) {
            onError("Transformed UVs array must contain 8 floats.")
            Log.e(TAG, "Transformed UVs array must contain 8 floats. Received: ${transformedUvs.size}")
            return
        }

        if (cameraTextureWidth <= 0 || cameraTextureHeight <= 0) {
            Log.w(TAG, "Invalid camera texture dimensions: ${cameraTextureWidth}x$cameraTextureHeight. Dropping frame.")
            return
        }

        val uvsCopy = transformedUvs.copyOf()
        renderHandler?.post {
            if (!isRunning) return@post // Check isRunning again inside the handler task
            try {
                glRenderer.renderTextureToSurface(textureId, uvsCopy, timestampNs)
                submittedArCoreTimestampNsQueue.offer(timestampNs)
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering texture to surface", e)
                onError("Runtime error during frame rendering: ${e.message}")
                // Consider stopping the encoder or specific error recovery
            }
        }
    }

    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        var eosReached = false

        while (isRunning || !eosReached) {
            if (mediaCodec == null || !isEncoderSetup) { // Codec might have been released due to an error
                if (isRunning) try { Thread.sleep(10) } catch (e: InterruptedException) {
                    Log.i(TAG, "Drain encoder interrupted during sleep, exiting.")
                    break // Exit if interrupted
                }
                continue
            }

            try {
                val outputBufferId = mediaCodec!!.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)

                when (outputBufferId) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No output available yet
                    }

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val actualOutputFormat  = mediaCodec!!.outputFormat
                        Log.i(TAG, "Encoder output format changed: $actualOutputFormat")
                        if(!formatInfoSent){
                            val spsBuffer = actualOutputFormat.getByteBuffer("csd-0") // SPS
                            val ppsBuffer = actualOutputFormat.getByteBuffer("csd-1") // PPS

                            if(spsBuffer != null && ppsBuffer != null){
                                // --- CHANGE THESE LINES ---
                                val spsByteArray = ByteArray(spsBuffer.remaining())
                                spsBuffer.duplicate().get(spsByteArray)

                                val ppsByteArray = ByteArray(ppsBuffer.remaining())
                                ppsBuffer.duplicate().get(ppsByteArray)

                                // Call the dedicated callback for SPS/PPS
                                onFormatInfoReady(spsByteArray, ppsByteArray)
                                formatInfoSent = true // Mark that SPS/PPS has been sent
                                Log.i(TAG, "SPS (size ${spsByteArray.size}) and PPS (size ${ppsByteArray.size}) sent via onFormatInfoReady.")
                            } else {
                                Log.w(TAG, "SPS or PPS buffer not found in output format upon change.")
                            }
                        } else {
                            Log.d(TAG, "Format info (SPS/PPS) already sent via onFormatInfoReady.")
                        }
                    }

                    else -> {
                        if (outputBufferId >= 0) { // Encoded data is available
                            val encodedBuffer = mediaCodec!!.getOutputBuffer(outputBufferId)
                            if (encodedBuffer == null) {
                                Log.e(TAG, "getOutputBuffer returned null for buffer ID: $outputBufferId")
                                // Release the buffer even if it's null to avoid issues
                                try { mediaCodec?.releaseOutputBuffer(outputBufferId, false) }
                                catch (e: IllegalStateException) { Log.e(TAG, "Error releasing null output buffer", e)}
                                continue
                            }

                            val isConfigData = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (isConfigData) {
                                if (!formatInfoSent && bufferInfo.size > 0) {
                                    Log.i(TAG, "Got BUFFER_FLAG_CODEC_CONFIG in an output buffer. Size: ${bufferInfo.size}")
                                    encodedBuffer.position(bufferInfo.offset)
                                    encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    val configDataArray = ByteArray(bufferInfo.size)
                                    encodedBuffer.get(configDataArray)
                                    Log.w(TAG, "Received CSD via BUFFER_FLAG_CODEC_CONFIG. Consider parsing if INFO_OUTPUT_FORMAT_CHANGED didn't provide csd-0/csd-1.")
                                }
                                // After logging/processing, release the buffer. Don't send it via onEncodedFrame as frame data.
                                mediaCodec!!.releaseOutputBuffer(outputBufferId, false)

                            } else if (bufferInfo.size != 0) {
                                // This is actual encoded video frame data
                                encodedBuffer.position(bufferInfo.offset)
                                encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                val data = ByteArray(bufferInfo.size)
                                encodedBuffer.get(data)
                                val byteString = ByteString.copyFrom(data)
                                val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                                val originalNs = submittedArCoreTimestampNsQueue.poll() // Récupérer le TS original

                                if (originalNs != null) {
//                                    Log.d(TAG, "Appel de onEncodedFrame pour données vidéo réelles. Taille: ${data.size}, Clé: $isKeyFrame, PTS_enc: ${bufferInfo.presentationTimeUs}, PTS_orig_ns: $originalNs")
                                    onEncodedFrame(byteString, isKeyFrame, originalNs)
                                } else if (isRunning) { // Seulement un avertissement si on s'attendait à des trames
                                    Log.w(TAG, "Trame encodée (PTS_us ${bufferInfo.presentationTimeUs}) mais pas de timestamp ARCore original correspondant dans la file. Ignorée.")
                                }

                                mediaCodec!!.releaseOutputBuffer(outputBufferId, false)

                            } else {
                                // Buffer size is 0, but not config data. Just release.
                                mediaCodec!!.releaseOutputBuffer(outputBufferId, false)
                            }

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(TAG, "Output EOS reached in drainEncoder.")
                                isRunning = false // Stop the loop
                                eosReached = true
                            }
                        }

                        else {
                            Log.w(TAG, "Unexpected outputBufferId: $outputBufferId")
                            // This case should ideally not be hit if the when is exhaustive for valid negative values.
                        }
                    }
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "IllegalStateException in drainEncoder. Codec might have been released or in wrong state.", e)
                onError("MediaCodec error in drainEncoder: ${e.message}")
                isRunning = false // Stop the loop on critical error
                Thread.currentThread().interrupt()
            }  catch (e: Exception) {
                if (isRunning) {
                    Log.e(TAG, "Unhandled exception in drainEncoder", e)
                    onError("Unhandled exception in drainEncoder: ${e.message}")
                }
                isRunning = false // Stop on other critical errors
            }
        }
        Log.i(TAG, "Drain encoder loop finished. EOsS processed : $eosReached")
    }

}