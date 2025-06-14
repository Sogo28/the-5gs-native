package com.aurelius.the_5gs.network

import android.util.Log
import com.aurelius.the_5gs.network.builder.ArPacketFactory
import com.aurelius.the_5gs.proto.ArFramePacket
import com.aurelius.the_5gs.proto.CameraIntrinsics
import com.aurelius.the_5gs.proto.ClientToServerMessage
import com.aurelius.the_5gs.proto.ServerToClientMessage
import io.grpc.Status
import io.grpc.stub.StreamObserver

class GrpcStreamManager(
    private val serverHost: String,
    private val serverPort: Int,
    private val onServerResponse: (ServerToClientMessage) -> Unit,
    private val onStreamError: (Throwable) -> Unit,
    private val onStreamCompleted: () -> Unit
){
    companion object{
        private const val TAG = "DataStreamManager"
        private const val INTRINSICS_ACK_MESSAGE = "Intrinsics received"
    }

    @Volatile
    private var requestObserver: StreamObserver<ClientToServerMessage>? = null
    var streamStatus = "Video Stream Idle"
    var intrinsicsConfirmedByServer = false
    var isIntrinsicsSent = false
    /**
     * Starts the bidirectional stream to the server.
     * This must be called before any data can be sent.
     */

    private val serverResponseObserver = object : StreamObserver<ServerToClientMessage> {
        override fun onNext(response: ServerToClientMessage) {
            // Pass the server's message up to the listener (e.g., MainActivity)
            if (response.statusMessage.startsWith(INTRINSICS_ACK_MESSAGE, ignoreCase = true)) {
                if (!intrinsicsConfirmedByServer) {
                    intrinsicsConfirmedByServer = true
                    Log.i(TAG, "Camera Intrinsics ACK confirmed by server.")
                    streamStatus = "Intrinsics confirmed"
                }
            }
            onServerResponse(response)
        }

        override fun onError(t: Throwable) {
            Log.e(TAG, "Stream error from server: ${Status.fromThrowable(t)}", t)
            streamStatus = "Error !"
            requestObserver = null // The stream is broken, nullify the observer
            onStreamError(t)
        }

        override fun onCompleted() {
            Log.i(TAG, "Server has completed its side of the stream.")
            // Server is done sending, but the client can still send messages.
            streamStatus = "Completed"
            onStreamCompleted()
        }
    }

    fun startStreaming() {
        if (requestObserver != null) {
            Log.w(TAG, "Stream already active. Closing before restarting.")
            completeStream()
        }

        if (!QuicNetworkManager.isInitialized()) {
            val error = IllegalStateException("QuicNetworkManager is not initialized.")
            Log.e(TAG, "Cannot start stream", error)
            streamStatus = "Error: Cronet N/A"
            onStreamError(error)
            return
        }

        Log.i(TAG, "Attempting to start bidirectional gRPC stream to $serverHost:$serverPort")

        try {
            requestObserver = QuicNetworkManager.startStreaming(serverHost, serverPort, serverResponseObserver)
            streamStatus = "Started"
        } catch (e: Exception){
            Log.e(TAG, "Failed to obtain request observer.")
            streamStatus = "Error !"
            onStreamError(e)
            requestObserver = null
        }

    }

    /**
     * Sends the initial CameraIntrinsics to the server.
     * Should be called once after startStream().
     */
    fun sendIntrinsics(intrinsics: CameraIntrinsics) {
        if (!isStreamActive()) {
            Log.w(TAG, "Cannot send intrinsics, stream is not active.")
            return
        }

        if(isIntrinsicsSent){
            Log.i(TAG, "Intrinsics already sent.")
            return
        }

        try {
            streamStatus = "Sending Intrinsics..."
            // Use the factory to build the Protobuf message
            val request = ArPacketFactory.createIntrinsicsRequest(intrinsics)
            requestObserver?.onNext(request)
            streamStatus = "Awaiting intrinsics server confirmation..."
            isIntrinsicsSent = true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending intrinsics", e)
            onStreamError(e)
        }
    }

    /**
     * Sends a synchronized ARFramePacket to the server.
     * This packet is expected to be created by the FrameSynchronizer.
     *
     * @param packet The complete ARFramePacket to send.
     */
    fun sendPacket(packet: ArFramePacket) {
        if (!isStreamActive()) {
            // Log this warning sparingly to avoid spamming the logs for every frame
            return
        }
        try {
            // Use the factory to build the Protobuf message
            val request = ArPacketFactory.createClientToServerPacket(packet)
            requestObserver?.onNext(request)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending ARFramePacket for TS: ${packet.timestampsNs}", e)
            onStreamError(e)
        }
    }

    /**
     * Gracefully closes the client's side of the stream.
     * This notifies the server that the client is done sending data.
     */
    fun completeStream() {
        if (!isStreamActive()) {
            Log.d(TAG, "completeStream called but no active stream to close.")
            return
        }
        Log.i(TAG, "Completing client stream...")
        try {
            requestObserver?.onCompleted()
            streamStatus = "Completed"
            intrinsicsConfirmedByServer = false
            isIntrinsicsSent = false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Exception completing stream (already closed/cancelled?): ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Generic exception while completing stream", e)
        } finally {
            requestObserver = null
        }
    }

    /**
     * Checks if the stream is currently active and ready to send data.
     */
    private fun isStreamActive(): Boolean {
        return requestObserver != null
    }
}