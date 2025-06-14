package com.aurelius.the_5gs.ar.rendering

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer


class GLRenderer(
    val width: Int,
    val height: Int
) {
    companion object{
        const val TAG = "GlRenderer"
        private const val VERTEX_SHADER_SOURCE = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """

        private const val FRAGMENT_SHADER_SOURCE = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTextureSampler;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTextureSampler, vTexCoord);
            }
        """

        // Full quad vertices in NDC
        private val QUAD_VERTICES = floatArrayOf(
            -1.0f, -1.0f,  // Bottom Left
            1.0f, -1.0f,  // Bottom Right
            -1.0f,  1.0f,  // Top Left
            1.0f,  1.0f   // Top Right
        )
    }

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    private var glProgram: Int = 0
    private var positionHandle: Int = -1
    private var texCoordHandle: Int = -1
    private var uTextureSamplerHandle: Int = -1

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texCoordBuffer: FloatBuffer // Updated per frame

    fun initEGL(surface: Surface, shareContext: EGLContext) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("eglGetDisplay failed: ${EGL14.eglGetError()}")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("eglInitialize failed: ${EGL14.eglGetError()}")
        }
        Log.i(TAG, "EGL version: ${version}.${version}")

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs.isEmpty()) {
            throw RuntimeException("eglChooseConfig failed or no suitable config found: ${EGL14.eglGetError()}")
        }
        val eglConfig = configs[0]

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, shareContext, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            val error = EGL14.eglGetError()
            val errorMsg = "eglCreateContext failed (error: 0x${Integer.toHexString(error)}). " +
                    "Attempted with shareContext: $shareContext"
            Log.e(TAG, errorMsg)
            throw RuntimeException(errorMsg)
        }
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("eglCreateContext failed: ${EGL14.eglGetError()}")
        }
        Log.i(TAG, "EGLContext created for VideoEncoder: $eglContext, sharing with: $shareContext")

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig, surface, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglCreateWindowSurface failed: ${EGL14.eglGetError()}")
        }

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed: ${EGL14.eglGetError()}")
        }
    }

    fun releaseEGL() {
        if (eglDisplay!= EGL14.EGL_NO_DISPLAY) {
            // Clear the current context - very important before destroying
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)

            if (eglSurface!= EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            if (eglContext!= EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }

    fun initGLES() {
        val vertexShaderHandle = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE)
        val fragmentShaderHandle = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE)

        glProgram = GLES20.glCreateProgram()
        if (glProgram == 0) {
            throw RuntimeException("glCreateProgram failed")
        }
        GLES20.glAttachShader(glProgram, vertexShaderHandle)
        GLES20.glAttachShader(glProgram, fragmentShaderHandle)
        GLES20.glLinkProgram(glProgram)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(glProgram, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(glProgram)
            GLES20.glDeleteProgram(glProgram)
            glProgram = 0
            GLES20.glDeleteShader(vertexShaderHandle)
            GLES20.glDeleteShader(fragmentShaderHandle)
            throw RuntimeException("Failed to link GLES program: $log")
        }
        // Shaders can be deleted after linking
        GLES20.glDeleteShader(vertexShaderHandle)
        GLES20.glDeleteShader(fragmentShaderHandle)


        positionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition")
        checkGlError("glGetAttribLocation aPosition")
        if (positionHandle == -1) throw RuntimeException("Could not get attrib location for aPosition")

        texCoordHandle = GLES20.glGetAttribLocation(glProgram, "aTexCoord")
        checkGlError("glGetAttribLocation aTexCoord")
        if (texCoordHandle == -1) throw RuntimeException("Could not get attrib location for aTexCoord")

        uTextureSamplerHandle = GLES20.glGetUniformLocation(glProgram, "uTextureSampler")
        checkGlError("glGetUniformLocation uTextureSampler")
        if (uTextureSamplerHandle == -1) throw RuntimeException("Could not get uniform location for uTextureSampler")

        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(QUAD_VERTICES).position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(8 * 4) // 4 vertices * 2 UVs * 4 bytes/float
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
    }

    fun releaseGLES() {
        if (glProgram!= 0) {
            GLES20.glDeleteProgram(glProgram)
            glProgram = 0
        }
        // Reset handles
        positionHandle = -1
        texCoordHandle = -1
        uTextureSamplerHandle = -1
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            throw RuntimeException("glCreateShader failed for type $type")
        }
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Failed to compile shader type $type: $log")
        }
        return shader
    }

    fun renderTextureToSurface(textureId: Int, transformedUvs: FloatArray, timestampNs: Long) {

        vertexBuffer.clear()
        vertexBuffer.put(QUAD_VERTICES).position(0)

        texCoordBuffer.clear()
//        Log.d(TAG, "renderTextureToSurface: Transformed UVs: ${transformedUvs.joinToString()}")
        texCoordBuffer.put(transformedUvs)
        texCoordBuffer.position(0)

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(glProgram)
        checkGlError("glUseProgram")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
//        Log.d(TAG, "renderTextureToSurface: Using textureId: $textureId")
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTextureSamplerHandle, 0) // Texture unit 0

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(positionHandle)

        texCoordBuffer.position(0)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)
        GLES20.glEnableVertexAttribArray(texCoordHandle)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0) // Unbind
        GLES20.glUseProgram(0) // Unbind program

        // Set presentation time
        val currentTimestamp = timestampNs
        Log.e("VideoEncoder", "Current Frame Timestamp : $timestampNs.")
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, currentTimestamp)

        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            Log.e(TAG, "eglSwapBuffers failed: ${EGL14.eglGetError()}")
            // This could be serious, might need to signal onError
        }
    }

    private fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error!= GLES20.GL_NO_ERROR) {
            val msg = "$op: glError 0x${Integer.toHexString(error)}"
            Log.e(TAG, msg)
            throw RuntimeException(msg)
        }
    }

}