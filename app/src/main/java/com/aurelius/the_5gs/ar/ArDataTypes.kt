package com.aurelius.the_5gs.ar

import android.media.Image
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.TrackingState


// ArPoseData and ArFrameData classes remain the same as previously defined
data class ArPoseData(
    val translation: FloatArray = FloatArray(3),
    val rotation: FloatArray = FloatArray(4)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ArPoseData
        if (!translation.contentEquals(other.translation)) return false
        if (!rotation.contentEquals(other.rotation)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = translation.contentHashCode()
        result = 31 * result + rotation.contentHashCode()
        return result
    }
}

data class ArFrameData(
    val timestamp: Long,
    val transform: FloatArray?,
    val pose: ArPoseData,
    val cameraImage: Image? = null,
    val cameraIntrinsics: CameraIntrinsics? = null,
    val trackingState: TrackingState? = null,
    val cameraTextureId: Int?,
    // Transformed UV coordinates for the camera texture quad (typically 8 floats: u0,v0, u1,v1, u2,v2, u3,v3)
    // These are the coordinates AFTER ARCore transforms them for correct display.
    val transformedUvCoords: FloatArray?,
    val cameraTextureWidth: Int?,
    val cameraTextureHeight: Int?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ArFrameData

        if (timestamp != other.timestamp) return false
        if (transform != null) {
            if (other.transform == null) return false
            if (!transform.contentEquals(other.transform)) return false
        } else if (other.transform != null) return false
        if (pose != other.pose) return false
        if (cameraImage != other.cameraImage) return false
        if (cameraIntrinsics != other.cameraIntrinsics) return false
        if (trackingState != other.trackingState) return false
        if (cameraTextureId != other.cameraTextureId) return false
        if (transformedUvCoords != null) {
            if (other.transformedUvCoords == null) return false
            if (!transformedUvCoords.contentEquals(other.transformedUvCoords)) return false
        } else if (other.transformedUvCoords != null) return false
        if (cameraTextureWidth != other.cameraTextureWidth) return false
        if (cameraTextureHeight != other.cameraTextureHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + (transform?.contentHashCode() ?: 0)
        result = 31 * result + pose.hashCode()
        result = 31 * result + (cameraImage?.hashCode() ?: 0)
        result = 31 * result + (cameraIntrinsics?.hashCode() ?: 0)
        result = 31 * result + (trackingState?.hashCode() ?: 0)
        result = 31 * result + (cameraTextureId ?: 0)
        result = 31 * result + (transformedUvCoords?.contentHashCode() ?: 0)
        result = 31 * result + (cameraTextureWidth ?: 0)
        result = 31 * result + (cameraTextureHeight ?: 0)
        return result
    }
}

data class PendingVideo(val encodedData: ByteArray, val isKeyFrame: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PendingVideo

        if (!encodedData.contentEquals(other.encodedData)) return false
        if (isKeyFrame != other.isKeyFrame) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encodedData.contentHashCode()
        result = 31 * result + isKeyFrame.hashCode()
        return result
    }
}

