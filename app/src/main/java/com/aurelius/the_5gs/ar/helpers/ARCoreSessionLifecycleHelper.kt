package com.aurelius.the_5gs.ar.helpers

/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.aurelius.the_5gs.helpers.CameraPermissionHelper
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException

/**
 * Manages an ARCore Session using the Android Lifecycle API. Before starting a Session, this class
 * requests installation of Google Play Services for AR if it's not installed or not up to date and
 * asks the user for required permissions if necessary.
 */
class ARCoreSessionLifecycleHelper(
    val activity: Activity,
    val features: Set<Session.Feature> = setOf()
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "ARCoreSessionHelper"
    }

    private var isSessionPaused: Boolean = false

    val isPaused: Boolean
        get() = isSessionPaused

    var installRequested = false
    var session: Session? = null
        private set

    /**
     * Creating a session may fail. In this case, session will remain null, and this function will be
     * called with an exception.
     *
     * See
     * [the `Session` constructor](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#Session(android.content.Context)
     * ) for more details.
     */
    var exceptionCallback: ((Exception) -> Unit)? = null

    /**
     * Before `Session.resume()` is called, a session must be configured. Use
     * [`Session.configure`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#configure-config)
     * or
     * [`setCameraConfig`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#setCameraConfig-cameraConfig)
     */
    var beforeSessionResume: ((Session) -> Unit)? = null

    /**
     * Attempts to create a session. If Google Play Services for AR is not installed or not up to
     * date, request installation.
     *
     * @return null when the session cannot be created due to a lack of the CAMERA permission or when
     * Google Play Services for AR is not installed or up to date, or when session creation fails for
     * any reason. In the case of a failure, [exceptionCallback] is invoked with the failure
     * exception.
     */
    private fun tryCreateSession(): Session? {
        // Vérifie la permission CAMERA
        if (!CameraPermissionHelper.hasCameraPermission(activity)) {
            CameraPermissionHelper.requestCameraPermission(activity)
            return null
        }

        return try {
            // Demande d'installation si nécessaire
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return null
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                    // OK, on peut créer la session
                }
            }

            val newSession = Session(activity, features)
            session = newSession
            newSession
        } catch (e: Exception) {
            (exceptionCallback ?: ::handleArCoreException).invoke(e)
            null
        }
    }

    private fun configureSession() {
        val currentSession = session ?: return
        val config = Config(currentSession).apply {
            lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            focusMode = Config.FocusMode.AUTO
            depthMode = Config.DepthMode.DISABLED
        }
        Log.d(TAG, "Configuring ARCore session.")
        currentSession.configure(config)
    }

    fun attachTo(lifecycle: Lifecycle) {
        lifecycle.addObserver(this)
        Log.d(TAG, "ARCoreSessionLifecycleHelper attached to lifecycle.")
    }

    fun detachFrom(lifecycle: Lifecycle) {
        lifecycle.removeObserver(this)
        Log.d(TAG, "ARCoreSessionLifecycleHelper detached from lifecycle.")
    }

    private fun handleArCoreException(exception: Exception) {
        Log.e(TAG, "ARCore exception: ${exception.localizedMessage}", exception)
        // Tu peux aussi afficher un Toast, dialog, etc. ici si besoin
    }

    override fun onResume(owner: LifecycleOwner) {
        val session = this.session ?: tryCreateSession() ?: return
        try {
            (beforeSessionResume ?: { configureSession() }).invoke(session)
            session.resume()
            this.session = session
            isSessionPaused = false
        } catch (e: CameraNotAvailableException) {
            (exceptionCallback ?: ::handleArCoreException).invoke(e)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        session?.pause()
        isSessionPaused = true
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // Explicitly close the ARCore session to release native resources.
        // Review the API reference for important considerations before calling close() in apps with
        // more complicated lifecycle requirements:
        // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
        session?.close()
        session = null
    }

    fun destroySession() {
        Log.d(TAG, "ARCoreSessionLifecycleHelper destroySession: Fermeture et nullification de la session.")
        session?.close()
        session = null
    }




}