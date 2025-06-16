package com.aurelius.the_5gs.ar.rendering

import android.opengl.Matrix
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Une classe propre pour rendre les trois axes (X, Y, Z) d'un repère 3D,
 * en utilisant les classes d'abstraction de `hello_ar_kotlin`.
 * Cette version crée un Mesh distinct pour chaque axe afin de pouvoir les colorer séparément.
 */
class AxisRenderer {

    private var shader: Shader? = null

    // Un Mesh dédié pour chaque axe
    private var meshX: Mesh? = null
    private var meshY: Mesh? = null
    private var meshZ: Mesh? = null

    /**
     * Crée les ressources OpenGL. Doit être appelé sur le thread GL.
     */
    fun createOnGlThread(render: SampleRender) {
        try {
            shader = Shader.createFromAssets(
                render,
                "shaders/axis.vert",
                "shaders/axis.frag",
                null
            ).setDepthTest(true)
                .setDepthWrite(true)

        } catch (e: IOException) {
            throw RuntimeException("Failed to create axis shader", e)
        }

        // Créer un VertexBuffer et un Mesh pour chaque axe
        meshX = createAxisMesh(render, floatArrayOf(0.0f, 0.0f, 0.0f, 0.1f, 0.0f, 0.0f)) // Ligne X
        meshY = createAxisMesh(render, floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.1f, 0.0f)) // Ligne Y
        meshZ = createAxisMesh(render, floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1f)) // Ligne Z
    }

    /**
     * Une fonction d'aide pour créer un Mesh contenant une seule ligne.
     */
    private fun createAxisMesh(render: SampleRender, vertices: FloatArray): Mesh {
        val vertexBufferData = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
        val vertexBuffer = VertexBuffer(render, 3, vertexBufferData)
        return Mesh(render, Mesh.PrimitiveMode.LINES, null, arrayOf(vertexBuffer))
    }

    /**
     * Dessine les trois axes à la pose spécifiée.
     */
    fun draw(render: SampleRender, viewMatrix: FloatArray, projectionMatrix: FloatArray, modelMatrix: FloatArray) {
        val currentShader = shader ?: return

        // Calculer la matrice Model-View-Projection (MVP)
        val modelViewMatrix = FloatArray(16)
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        val mvpMatrix = FloatArray(16)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        // Passer la matrice MVP au shader (elle sera utilisée pour les trois dessins)
        currentShader.setMat4("u_MvpMatrix", mvpMatrix)

        // 1. Dessiner l'axe X en ROUGE
        currentShader.setVec4("u_Color", floatArrayOf(1.0f, 0.0f, 0.0f, 1.0f))
        render.draw(meshX, currentShader) // Utilise le mesh de l'axe X

        // 2. Dessiner l'axe Y en VERT
        currentShader.setVec4("u_Color", floatArrayOf(0.0f, 1.0f, 0.0f, 1.0f))
        render.draw(meshY, currentShader) // Utilise le mesh de l'axe Y

        // 3. Dessiner l'axe Z en BLEU
        currentShader.setVec4("u_Color", floatArrayOf(0.0f, 0.0f, 1.0f, 1.0f))
        render.draw(meshZ, currentShader) // Utilise le mesh de l'axe Z
    }
}