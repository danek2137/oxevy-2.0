package me.alpha432.oxevy.util.render.gl

import me.alpha432.oxevy.util.render.RenderUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33
import java.awt.Color

object ShaderRenderer {

    private var initialized = false
    private var rectProgram = -1
    private var arcProgram = -1

    private val float16Buf = FloatArray(16)

    fun init() {
        if (initialized) return
        rectProgram = ShaderManager.getOrCreateProgram("rect")
        arcProgram = ShaderManager.getOrCreateProgram("arc")
        initialized = true
    }

    fun ensureInit() {
        if (!initialized) init()
    }

    fun drawRect(guiGraphics: GuiGraphics, x: Float, y: Float, w: Float, h: Float, color: Int) {
        drawShaderRectFallback(guiGraphics, x, y, w, h, color, 0f, color, color, 0, 0f, 0)
    }

    fun drawRoundedRect(
        guiGraphics: GuiGraphics,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float, color: Int
    ) {
        RenderUtil.roundRect(guiGraphics, x, y, w, h, radius, color)
    }

    fun drawGradientRectH(
        guiGraphics: GuiGraphics,
        x: Float, y: Float, w: Float, h: Float,
        colorLeft: Int, colorRight: Int
    ) {
        RenderUtil.horizontalGradient(guiGraphics, x, y, x + w, y + h,
            Color(colorLeft, true), Color(colorRight, true))
    }

    fun drawGradientRectV(
        guiGraphics: GuiGraphics,
        x: Float, y: Float, w: Float, h: Float,
        colorTop: Int, colorBottom: Int
    ) {
        RenderUtil.verticalGradient(guiGraphics, x, y, x + w, y + h,
            Color(colorTop, true), Color(colorBottom, true))
    }

    private fun drawShaderRectFallback(
        guiGraphics: GuiGraphics,
        x: Float, y: Float, w: Float, h: Float,
        color: Int, radius: Float,
        color1: Int, color2: Int,
        gradientDir: Int,
        borderWidth: Float, borderColor: Int
    ) {
        if (radius > 0.5f) {
            RenderUtil.roundRect(guiGraphics, x, y, w, h, radius, color)
        } else {
            RenderUtil.rect(guiGraphics, x, y, x + w, y + h, color)
        }
    }

    fun drawRectShader(x: Float, y: Float, w: Float, h: Float, radius: Float,
                       color1: Int, color2: Int, color3: Int, color4: Int,
                       outlineColor: Int, thickness: Float, softness: Float) {
        ensureInit()
        if (rectProgram == -1) return
        drawShaderQuad(rectProgram, x, y, w, h) {
            ShaderManager.uniform4f(rectProgram, "uRect", x, y, w, h)
            ShaderManager.uniform1f(rectProgram, "uRadius", radius)
            ShaderManager.setColorUniform(rectProgram, "uColor1", color1)
            ShaderManager.setColorUniform(rectProgram, "uColor2", color2)
            ShaderManager.uniform1i(rectProgram, "uGradientDir", 0)
            ShaderManager.uniform1f(rectProgram, "uBorderWidth", thickness)
            ShaderManager.setColorUniform(rectProgram, "uBorderColor", outlineColor)
        }
    }

    fun drawArc(cx: Float, cy: Float, radius: Float, thickness: Float,
                startAngle: Float, endAngle: Float,
                color1: Int, color2: Int) {
        ensureInit()
        if (arcProgram == -1) return
        val size = (radius + thickness) * 2f
        val left = cx - size / 2f
        val top = cy - size / 2f

        // gl_FragCoord.y goes up (0=bottom), Java screen y goes down (0=top).
        // Convert uLocation to framebuffer bottom-left of the quad.
        val windowHeight = Minecraft.getInstance().window.height.toFloat()
        val flipY = windowHeight - top - size

        drawShaderQuad(arcProgram, left, top, size, size) {
            ShaderManager.uniform2f(arcProgram, "uLocation", left, flipY)
            ShaderManager.uniform2f(arcProgram, "uSize", size, size)
            ShaderManager.uniform1f(arcProgram, "uRadius", radius / size)
            ShaderManager.uniform1f(arcProgram, "uThickness", thickness / size)
            ShaderManager.uniform1f(arcProgram, "uStartAngle", startAngle)
            ShaderManager.uniform1f(arcProgram, "uEndAngle", endAngle)
            ShaderManager.setColorUniform(arcProgram, "uColor1", color1)
            ShaderManager.setColorUniform(arcProgram, "uColor2", color2)
        }
    }

    private fun drawShaderQuad(program: Int, x: Float, y: Float, w: Float, h: Float, setUniforms: () -> Unit) {
        GL33.glEnable(GL33.GL_BLEND)
        GL33.glBlendFunc(GL33.GL_SRC_ALPHA, GL33.GL_ONE_MINUS_SRC_ALPHA)

        QuadRenderer.bind()
        ShaderManager.useProgram(program)

        val model = Matrix4f().identity()
            .translate(x + w * 0.5f, y + h * 0.5f, 0f)
            .scale(w, h, 1f)
        model.get(float16Buf)
        ShaderManager.uniformMatrix4f(program, "uModelMatrix", float16Buf)

        val projection = orthoMatrix()
        ShaderManager.uniformMatrix4f(program, "uProjMatrix", projection)

        setUniforms()

        QuadRenderer.draw()

        ShaderManager.resetProgram()
        QuadRenderer.unbind()
    }

    private fun orthoMatrix(): FloatArray {
        val window = Minecraft.getInstance().window
        val w = window.width.toFloat()
        val h = window.height.toFloat()
        val m = Matrix4f().ortho(0f, w, h, 0f, 1000f, 3000f)
        val buf = FloatArray(16)
        m.get(buf)
        return buf
    }

    fun cleanup() {
        ShaderManager.deleteAll()
        QuadRenderer.cleanup()
        initialized = false
        rectProgram = -1
        arcProgram = -1
    }
}
