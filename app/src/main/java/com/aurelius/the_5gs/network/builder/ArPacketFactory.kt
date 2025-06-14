package com.aurelius.the_5gs.network.builder
import com.aurelius.the_5gs.ar.ArPoseData
import com.aurelius.the_5gs.proto.ArFramePacket
import com.aurelius.the_5gs.proto.ArPose
import com.aurelius.the_5gs.proto.ClientToServerMessage
import com.aurelius.the_5gs.proto.VideoFrame
import com.google.ar.core.CameraIntrinsics as ArCoreIntrinsics
import com.aurelius.the_5gs.proto.CameraIntrinsics
import com.google.protobuf.ByteString

object ArPacketFactory {

    /**
     * Creates the main ARFramePacket by combining pose and video data.
     * This is the central builder method.
     */
    fun createARFramePacket(
        timestampNs: Long,
        poseData: ArPoseData,
        videoData: ByteArray,
        isKeyFrame: Boolean
    ): ArFramePacket {
        val protoVideoFrame = createVideoFrameProto(videoData, isKeyFrame)
        val protoPose = createPoseProto(poseData)

        return ArFramePacket.newBuilder()
            .setTimestampsNs(timestampNs)
            .setPose(protoPose)
            .setVideoFrame(protoVideoFrame)
            .build()
    }

    /**
     * Wraps a completed ARFramePacket into the final ClientToServerMessage.
     */
    fun createClientToServerPacket(packet: ArFramePacket): ClientToServerMessage {
        return ClientToServerMessage.newBuilder()
            .setArFramePacket(packet)
            .build()
    }

    /**
     * Wraps CameraIntrinsics into the final ClientToServerMessage for the initial send.
     */
    fun createIntrinsicsRequest(intrinsics: CameraIntrinsics): ClientToServerMessage {
        return ClientToServerMessage.newBuilder()
            .setCameraIntrinsics(intrinsics)
            .build()
    }

    fun createIntrinsicsProto(intrinsics: ArCoreIntrinsics): CameraIntrinsics{
        return CameraIntrinsics.newBuilder()
            .setFocalLengthX(intrinsics.focalLength[0])
            .setFocalLengthY(intrinsics.focalLength[1])
            .setPrincipalPointX(intrinsics.principalPoint[0])
            .setPrincipalPointY(intrinsics.principalPoint[1])
            .build()
    }

    // --- Private helper methods for building sub-messages ---

    private fun createVideoFrameProto(data: ByteArray, isKeyFrame: Boolean): VideoFrame {
        return VideoFrame.newBuilder()
            .setEncodedFrameData(ByteString.copyFrom(data))
            .setIsKeyFrame(isKeyFrame)
            .build()
    }

    private fun createPoseProto(pose: ArPoseData): ArPose {
        return ArPose.newBuilder()
            .addAllTranslation(pose.translation.toList())
            .addAllRotation(pose.rotation.toList())
            .build()
    }
}