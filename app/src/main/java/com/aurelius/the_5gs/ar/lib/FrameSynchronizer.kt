package com.aurelius.the_5gs.ar.lib // Or your chosen package, e.g., com.aurelius.the_5gs.processing

import android.util.Log
import com.aurelius.the_5gs.ar.ArPoseData // Your Kotlin data class for pose
import com.aurelius.the_5gs.ar.PendingVideo
import com.aurelius.the_5gs.network.builder.ArPacketFactory
import com.aurelius.the_5gs.proto.ArFramePacket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A helper class to synchronize AR pose data and encoded video data, which arrive asynchronously.
 *
 * This class acts as a "waiting room". It temporarily stores pose or video data until its
 * matching counterpart (with the same timestamp) arrives. Once a pair is found, it uses
 * ARPacketFactory to build a complete ARFramePacket and emits it via the onPacketReady callback.
 *
 * @param onPacketReady A callback lambda that is invoked when a synchronized packet is successfully created.
 */
class FrameSynchronizer(
    private val onPacketReady: (packet: ArFramePacket) -> Unit
) {
    companion object {
        private const val TAG = "FrameSynchronizer"
        // Optional: To prevent the maps from growing indefinitely if frames are dropped
        private const val MAX_PENDING_ITEMS = 100
    }


    // ConcurrentHashMaps are used for thread safety, as callbacks might arrive from different threads.
    private val pendingPoses = ConcurrentHashMap<Long, ArPoseData>()
    private val pendingVideos = ConcurrentHashMap<Long, PendingVideo>()
    // --- ADD STORAGE FOR SPS/PPS ---
    private var spsData: ByteArray? = null
    private var ppsData: ByteArray? = null

    // Optional: for logging/debugging purposes
    private val packetsCreated = AtomicInteger(0)

    /**
     * Adds AR pose data to the synchronizer.
     * It will immediately try to match it with any pending video data for the same timestamp.
     * If no match is found, the pose is stored and waits for its video frame.
     *
     * @param timestampNs The unique nanosecond timestamp from the ARCore Frame.
     * @param pose The ArPoseData extracted from the ARCore Frame.
     */
    fun addPoseData(timestampNs: Long, pose: ArPoseData) {
        // First, try to find a video frame that arrived earlier.
        val matchingVideo = pendingVideos.remove(timestampNs)

        if (matchingVideo != null) {
            // Match found! The video was waiting for this pose.
            // Build and emit the packet.
            createAndEmitPacket(timestampNs, pose, matchingVideo)
        } else {
            // No matching video yet. Store this pose to wait for the video.
            if (pendingPoses.size > MAX_PENDING_ITEMS) {
                Log.w(TAG, "Pending poses map is large, potential memory leak or dropped frames.")
                // Optional: clear the oldest entry to prevent memory issues
                // Sort by timestamp (oldest first), and remove the first few
                val entriesToRemove = pendingPoses.entries
                    .sortedBy { it.key }
                    .take(30) // or 1, or more if needed

                for ((ts, _) in entriesToRemove) {
                    pendingPoses.remove(ts)
                    Log.d(TAG, "Evicted unmatched pose with timestamp $ts")
                }
            }
            pendingPoses[timestampNs] = pose
        }
    }

    fun setSpsPpsData(sps: ByteArray, pps: ByteArray) {
        Log.i(TAG, "SPS/PPS data received by FrameSynchronizer.")
        this.spsData = sps
        this.ppsData = pps
    }

    /**
     * Adds encoded video data to the synchronizer.
     * It will immediately try to match it with any pending pose data for the same timestamp.
     * If no match is found, the video data is stored and waits for its pose.
     *
     * @param timestampNs The unique nanosecond timestamp from the original ARCore Frame.
     * @param encodedData The compressed video data from the encoder.
     * @param isKeyFrame A flag indicating if the frame is a keyframe.
     */
    fun addEncodedVideo(timestampNs: Long, encodedData: ByteArray, isKeyFrame: Boolean) {

        val finalFrameData = if (isKeyFrame) {
            if (spsData != null && ppsData != null) {
//                Log.d(TAG, "Prepending SPS/PPS to keyframe in Synchronizer.")
                spsData!! + ppsData!! + encodedData
            } else {
                Log.w(TAG, "Keyframe arrived but SPS/PPS data is not available in Synchronizer.")
                encodedData
            }
        } else {
            encodedData
        }
        // --- End of SPS/PPS logic ---

        val videoData = PendingVideo(finalFrameData, isKeyFrame)
        val matchingPose = pendingPoses.remove(timestampNs)

        // First, try to find a pose that arrived earlier.
        if (matchingPose != null) {
            // Match found! The pose was waiting for this video frame.
            // Build and emit the packet.
            createAndEmitPacket(timestampNs, matchingPose, videoData)
        } else {
            // No matching pose yet. Store this video to wait for the pose.
            if (pendingVideos.size > MAX_PENDING_ITEMS) {
                Log.w(TAG, "Pending videos map is large, potential memory leak or dropped frames.")
            }
            pendingVideos[timestampNs] = videoData
        }
    }

    /**
     * The private method where the final packet is built using the factory and then emitted.
     */
    private fun createAndEmitPacket(timestampNs: Long, pose: ArPoseData, video: PendingVideo) {
        // Delegate all building logic to the ARPacketFactory
        val completePacket = ArPacketFactory.createARFramePacket(
            timestampNs = timestampNs,
            poseData = pose,
            videoData = video.encodedData,
            isKeyFrame = video.isKeyFrame
        )

        // Use the callback to send the packet to its next destination (e.g., the GenericStreamManager)
        onPacketReady(completePacket)
        packetsCreated.incrementAndGet()
        // Log.d(TAG, "Packet #${packetsCreated.get()} for TS $timestampNs synchronized and emitted.")
    }

    /**
     * Clears all pending data. Should be called when the AR session stops to prevent memory leaks.
     */
    fun clear() {
        Log.i(TAG, "Clearing all pending poses and video frames. Total packets created: ${packetsCreated.get()}")
        pendingPoses.clear()
        pendingVideos.clear()
        packetsCreated.set(0)
    }
}