package me.alpha432.oxevy.util.render.gl

import org.lwjgl.opengl.GL33
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

object ShaderManager {

    private val log = LoggerFactory.getLogger("ShaderManager")
    private val programs = ConcurrentHashMap<String, Int>()
    private var currentProgram = -1

    fun getOrCreateProgram(name: String): Int {
        return programs.getOrPut(name) { loadProgram(name) }
    }

    private fun loadProgram(name: String): Int {
        val vertSrc = loadShaderSource("assets/oxevy/shaders/$name.vert") ?: return -1
        val fragSrc = loadShaderSource("assets/oxevy/shaders/$name.frag") ?: return -1
        return createProgram(vertSrc, fragSrc)
    }

    private fun loadShaderSource(path: String): String? {
        return try {
            val stream = javaClass.classLoader.getResourceAsStream(path)
                ?: return null
            BufferedReader(InputStreamReader(stream)).use { it.readText() }
        } catch (e: Exception) {
            log.error("Failed to load shader: $path", e)
            null
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GL33.GL_VERTEX_SHADER, vertexSource) ?: return -1
        val fragmentShader = compileShader(GL33.GL_FRAGMENT_SHADER, fragmentSource) ?: return -1

        val program = GL33.glCreateProgram()
        GL33.glAttachShader(program, vertexShader)
        GL33.glAttachShader(program, fragmentShader)
        GL33.glLinkProgram(program)

        val status = GL33.glGetProgrami(program, GL33.GL_LINK_STATUS)
        if (status == GL33.GL_FALSE) {
            val logStr = GL33.glGetProgramInfoLog(program)
            log.error("Program link failed: $logStr")
            GL33.glDeleteProgram(program)
            GL33.glDeleteShader(vertexShader)
            GL33.glDeleteShader(fragmentShader)
            return -1
        }

        GL33.glDetachShader(program, vertexShader)
        GL33.glDetachShader(program, fragmentShader)
        GL33.glDeleteShader(vertexShader)
        GL33.glDeleteShader(fragmentShader)

        programs["__internal_$program"] = program
        return program
    }

    private fun compileShader(type: Int, source: String): Int? {
        val shader = GL33.glCreateShader(type)
        GL33.glShaderSource(shader, source)
        GL33.glCompileShader(shader)

        val status = GL33.glGetShaderi(shader, GL33.GL_COMPILE_STATUS)
        if (status == GL33.GL_FALSE) {
            val logStr = GL33.glGetShaderInfoLog(shader)
            val typeName = if (type == GL33.GL_VERTEX_SHADER) "vertex" else "fragment"
            log.error("$typeName shader compile failed: $logStr")
            GL33.glDeleteShader(shader)
            return null
        }

        return shader
    }

    fun useProgram(program: Int) {
        if (program != currentProgram) {
            GL33.glUseProgram(program)
            currentProgram = program
        }
    }

    fun resetProgram() {
        if (currentProgram != -1) {
            GL33.glUseProgram(0)
            currentProgram = -1
        }
    }

    fun uniform1f(program: Int, name: String, value: Float) {
        val loc = GL33.glGetUniformLocation(program, name)
        if (loc != -1) GL33.glUniform1f(loc, value)
    }

    fun uniform2f(program: Int, name: String, x: Float, y: Float) {
        val loc = GL33.glGetUniformLocation(program, name)
        if (loc != -1) GL33.glUniform2f(loc, x, y)
    }

    fun uniform4f(program: Int, name: String, x: Float, y: Float, z: Float, w: Float) {
        val loc = GL33.glGetUniformLocation(program, name)
        if (loc != -1) GL33.glUniform4f(loc, x, y, z, w)
    }

    fun uniform1i(program: Int, name: String, value: Int) {
        val loc = GL33.glGetUniformLocation(program, name)
        if (loc != -1) GL33.glUniform1i(loc, value)
    }

    fun uniformMatrix4f(program: Int, name: String, matrix: FloatArray) {
        val loc = GL33.glGetUniformLocation(program, name)
        if (loc != -1) GL33.glUniformMatrix4fv(loc, false, matrix)
    }

    fun deleteProgram(name: String) {
        programs.remove(name)?.let { GL33.glDeleteProgram(it) }
    }

    fun deleteAll() {
        programs.values.forEach { GL33.glDeleteProgram(it) }
        programs.clear()
    }

    private fun argbToRgba(color: Int): FloatArray {
        val a = ((color shr 24) and 0xFF) / 255f
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return floatArrayOf(r, g, b, a)
    }

    fun colorUniform(color: Int): FloatArray = argbToRgba(color)

    fun setColorUniform(program: Int, name: String, color: Int) {
        val rgba = argbToRgba(color)
        uniform4f(program, name, rgba[0], rgba[1], rgba[2], rgba[3])
    }
}
