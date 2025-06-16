package com.aurelius.the_5gs.ar.lib

import android.util.Log
import com.aurelius.the_5gs.ar.ArFrameData
import com.aurelius.the_5gs.ar.ArPoseData
import com.google.ar.core.Camera
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ArFrameAnalyzer {
    private val inputVerticesBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(
                -1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f
            ))
            position(0)
        }

    private val outputUvBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    companion object{
        const val TAG = "ArFrameAnalyzer"
    }

    fun extractArFrameData(frame: Frame, camera: Camera): ArFrameData? {
//        Log.i(TAG, "Extracting data from the frame...")
        val trackingState = camera.trackingState

        if(trackingState != TrackingState.TRACKING) {
//            Log.w(TAG, "Camera not tracking yet !")
            return null
        }

        val currentCameraTextureId: Int = frame.cameraTextureName
        val currentTransformedUvs: FloatArray?
        val currentTextureWidth: Int?
        val currentTextureHeight: Int?
        val arPose: ArPoseData
        val displayOrientedPoseArray: FloatArray
        val outputUvCoordsArray = FloatArray(8)

        // 1. Transformed UVs (using frame.transformCoordinates2d)
        outputUvBuffer.rewind()

        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            inputVerticesBuffer, // Use FloatBuffer
            Coordinates2d.TEXTURE_NORMALIZED,
            outputUvBuffer // Use FloatBuffer
        )
        outputUvBuffer.rewind()
        outputUvBuffer.get(outputUvCoordsArray) // Copy result back to array if needed
        currentTransformedUvs = outputUvCoordsArray

        // 2. Texture Dimensions
        val arCoreTextureIntrinsics = camera.textureIntrinsics
        val imageDims = arCoreTextureIntrinsics.imageDimensions
        currentTextureWidth = imageDims[0]
        currentTextureHeight = imageDims[1]

        // 3. Pose Data
        val tempPoseArray = FloatArray(16)
        camera.displayOrientedPose.toMatrix(tempPoseArray, 0)
        displayOrientedPoseArray = tempPoseArray

        val devicePose = camera.pose
        arPose = ArPoseData(
            translation = devicePose.translation.clone(),
            rotation = devicePose.rotationQuaternion.clone()
        )

        // 4. CPU Image Intrinsics
        val cpuImageIntrinsics: CameraIntrinsics? = camera.imageIntrinsics

        // Construct ArFrameData
        val frameData = ArFrameData(
            timestamp = frame.timestamp,
            transform = displayOrientedPoseArray,
            pose = arPose,
            cameraImage = null, // You weren't acquiring CPU image
            cameraIntrinsics = cpuImageIntrinsics,
            trackingState = trackingState, // Should be TRACKING here
            cameraTextureId = currentCameraTextureId,
            transformedUvCoords = currentTransformedUvs,
            cameraTextureWidth = currentTextureWidth,
            cameraTextureHeight = currentTextureHeight
        )

        // Close the image after extraction
        frameData.cameraImage?.close()


        return frameData
    }
}