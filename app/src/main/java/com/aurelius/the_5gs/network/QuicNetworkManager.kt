package com.aurelius.the_5gs.network

import android.content.Context
import android.util.Log
import com.aurelius.the_5gs.proto.ArDataStreamerGrpc
import com.aurelius.the_5gs.proto.ClientToServerMessage
import com.aurelius.the_5gs.proto.ServerToClientMessage
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.cronet.CronetChannelBuilder
import io.grpc.stub.StreamObserver
import org.chromium.net.CronetEngine
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object QuicNetworkManager {

    private const val TAG = "QuicNetworkManager_gRPC"

    private var cronetEngine: CronetEngine? = null
    private var grpcChannel: ManagedChannel? = null
    // Executor for gRPC stub calls and Cronet's internal operations.
    // A single thread executor might be okay for Cronet, but gRPC might benefit from more.
    // For simplicity, we can start with one. Consider a cached thread pool for gRPC if needed.
    private val sharedExecutor: Executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    )

    fun initialize(context: Context) {
        if (cronetEngine == null) {
            val appContext = context.applicationContext
            val cronetEngineBuilder = CronetEngine.Builder(appContext)
            cronetEngineBuilder.enableQuic(true) // Enable QUIC for the engine
            cronetEngineBuilder.enableHttp2(true) // Also enable HTTP/2 as gRPC often uses it
            try {
                cronetEngine = cronetEngineBuilder.build()
                Log.i(TAG, "CronetEngine Initialized. Version: ${cronetEngine?.versionString}")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to initialize CronetEngine: UnsatisfiedLinkError. Check native libraries.", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CronetEngine", e)
            }
        } else {
            Log.d(TAG, "CronetEngine already initialized.")
        }
    }

    fun isInitialized(): Boolean = cronetEngine != null

    private fun getOrCreateChannel(serverHost: String, serverPort: Int): ManagedChannel? {
        if (grpcChannel == null || grpcChannel!!.isShutdown || grpcChannel!!.isTerminated) {
            if (cronetEngine == null) {
                Log.e(TAG, "CronetEngine not initialized. Cannot create gRPC channel.")
                return null
            }
            Log.d(TAG, "Creating new gRPC ManagedChannel to $serverHost:$serverPort using Cronet.")
            try {
                // Use CronetChannelBuilder to make gRPC use Cronet (and thus QUIC/HTTP3)
                grpcChannel = CronetChannelBuilder
                    .forAddress(serverHost, serverPort, cronetEngine) // Pass the CronetEngine
                    .executor(sharedExecutor) // Executor for gRPC stub calls
                    .build()
                Log.i(TAG, "gRPC ManagedChannel created for $serverHost:$serverPort")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create gRPC ManagedChannel to $serverHost:$serverPort", e)
                grpcChannel = null
            }
        }
        return grpcChannel
    }

    /**
     * Initiates a client-streaming RPC to send ArPose data.
     *
     * @param serverHost The hostname or IP of the gRPC server.
     * @param serverPort The port of the gRPC server.
     * @param responseObserver A StreamObserver to handle the server's single response and errors.
     * @return A StreamObserver<ArPose> that the client can use to send a stream of ArPose messages.
     * Returns null if the channel could not be established or if Cronet is not initialized.
     */

    fun startStreaming(
        serverHost: String,
        serverPort: Int,
        serverResponseObserver : StreamObserver<ServerToClientMessage>
    ): StreamObserver<ClientToServerMessage>?{
        if (!isInitialized()) {
            Log.e(TAG, "Cronet not initialized. Cannot start video stream.")
            serverResponseObserver.onError(Status.UNAVAILABLE.withDescription("Cronet engine not initialized.").asRuntimeException())
            return null
        }

        val channel = getOrCreateChannel(serverHost, serverPort)
        if (channel == null) {
            Log.e(TAG, "Cannot start video stream: gRPC channel is null.")
            serverResponseObserver.onError(Status.UNAVAILABLE.withDescription("gRPC channel could not be established for data stream.").asRuntimeException())
            return null
        }

        val asyncStub = ArDataStreamerGrpc.newStub(channel)
        Log.d(TAG, "Starting bidirectional ArDataStreamer.StreamArData...")
        try {
            // For bidirectional streaming, the method call itself returns the request observer
            return asyncStub.streamArData(serverResponseObserver)
        } catch (e: Exception) {
            Log.e(TAG, "Exception when calling asyncStub.streamArData", e)
            serverResponseObserver.onError(Status.INTERNAL.withDescription("Failed to initiate video stream: ${e.message}").withCause(e).asRuntimeException())
            return null
        }
    }

    fun shutdown() {
        Log.d(TAG, "Attempting to shutdown gRPC channel.")
        val channelToShutdown = grpcChannel
        grpcChannel = null // Nullify immediately to prevent reuse during shutdown
        try {
            channelToShutdown?.shutdown()?.awaitTermination(5, TimeUnit.SECONDS)
            Log.i(TAG, "gRPC channel shutdown complete.")
        } catch (e: InterruptedException) {
            Log.w(TAG, "gRPC channel shutdown interrupted.", e)
            Thread.currentThread().interrupt()
            // Force shutdown if interrupted
            channelToShutdown?.shutdownNow()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down gRPC channel", e)
            channelToShutdown?.shutdownNow()
        }

        // CronetEngine shutdown is more global. Only do this if the entire app is closing
        // and no other part of the app might need Cronet.
        // cronetEngine?.shutdown()
        // cronetEngine = null
        // Log.i(TAG, "CronetEngine shutdown.")
    }
}