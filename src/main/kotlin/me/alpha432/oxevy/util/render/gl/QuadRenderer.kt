package me.alpha432.oxevy.util.render.gl

import org.lwjgl.opengl.GL33
import java.nio.ByteBuffer
import java.nio.ByteOrder

object QuadRenderer {

    private var vao = -1
    private var vbo = -1
    private var initialized = false

    fun ensureInit() {
        if (initialized) return
        initialized = true

        vao = GL33.glGenVertexArrays()
        vbo = GL33.glGenBuffers()

        GL33.glBindVertexArray(vao)
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vbo)

        val data = floatArrayOf(
            -0.5f, -0.5f, 0f, 1f, 1f, 1f, 1f,
             0.5f, -0.5f, 0f, 1f, 1f, 1f, 1f,
             0.5f,  0.5f, 0f, 1f, 1f, 1f, 1f,
            -0.5f,  0.5f, 0f, 1f, 1f, 1f, 1f
        )

        val buf = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(data).flip()

        GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buf, GL33.GL_STATIC_DRAW)

        GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 28, 0)
        GL33.glEnableVertexAttribArray(0)

        GL33.glVertexAttribPointer(1, 4, GL33.GL_FLOAT, false, 28, 12)
        GL33.glEnableVertexAttribArray(1)

        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, 0)
        GL33.glBindVertexArray(0)
    }

    fun bind() {
        ensureInit()
        GL33.glBindVertexArray(vao)
    }

    fun unbind() {
        GL33.glBindVertexArray(0)
    }

    fun draw() {
        GL33.glDrawArrays(GL33.GL_TRIANGLE_FAN, 0, 4)
    }

    fun cleanup() {
        if (vao != -1) {
            GL33.glDeleteVertexArrays(vao)
            vao = -1
        }
        if (vbo != -1) {
            GL33.glDeleteBuffers(vbo)
            vbo = -1
        }
        initialized = false
    }
}
