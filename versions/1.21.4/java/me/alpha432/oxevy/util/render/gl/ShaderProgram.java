package me.alpha432.oxevy.util.render.gl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL33;

public class ShaderProgram {

    private final int program;
    private boolean valid;

    public ShaderProgram(String vertexPath, String fragmentPath) {
        int vtx = compileShader(GL33.GL_VERTEX_SHADER, loadSource(vertexPath));
        if (vtx == -1) { program = -1; valid = false; return; }
        int frag = compileShader(GL33.GL_FRAGMENT_SHADER, loadSource(fragmentPath));
        if (frag == -1) { GL33.glDeleteShader(vtx); program = -1; valid = false; return; }

        program = GL33.glCreateProgram();
        GL33.glAttachShader(program, vtx);
        GL33.glAttachShader(program, frag);
        GL33.glLinkProgram(program);

        int linked = GL33.glGetProgrami(program, GL33.GL_LINK_STATUS);
        if (linked == GL33.GL_FALSE) {
            String log = GL33.glGetProgramInfoLog(program);
            System.err.println("Shader program link error: " + log);
            GL33.glDeleteProgram(program);
            GL33.glDeleteShader(vtx);
            GL33.glDeleteShader(frag);
            valid = false;
            return;
        }

        GL33.glDeleteShader(vtx);
        GL33.glDeleteShader(frag);
        valid = true;
    }

    public void use() {
        if (valid) GL33.glUseProgram(program);
    }

    public int getProgram() {
        return program;
    }

    public boolean isValid() {
        return valid;
    }

    public void setMatrix4f(String name, Matrix4f mat) {
        int loc = GL33.glGetUniformLocation(program, name);
        if (loc == -1) return;
        float[] buf = new float[16];
        mat.get(buf);
        GL33.glUniformMatrix4fv(loc, false, buf);
    }

    public void setFloat(String name, float value) {
        int loc = GL33.glGetUniformLocation(program, name);
        if (loc != -1) GL33.glUniform1f(loc, value);
    }

    public void setVec4(String name, float x, float y, float z, float w) {
        int loc = GL33.glGetUniformLocation(program, name);
        if (loc != -1) GL33.glUniform4f(loc, x, y, z, w);
    }

    public void delete() {
        if (valid) GL33.glDeleteProgram(program);
        valid = false;
    }

    private int compileShader(int type, String source) {
        if (source == null) return -1;
        int shader = GL33.glCreateShader(type);
        GL33.glShaderSource(shader, source);
        GL33.glCompileShader(shader);
        int compiled = GL33.glGetShaderi(shader, GL33.GL_COMPILE_STATUS);
        if (compiled == GL33.GL_FALSE) {
            String log = GL33.glGetShaderInfoLog(shader);
            System.err.println("Shader compile error (" + (type == GL33.GL_VERTEX_SHADER ? "vertex" : "fragment") + "): " + log);
            GL33.glDeleteShader(shader);
            return -1;
        }
        return shader;
    }

    private String loadSource(String path) {
        try {
            String ns = path.contains(":") ? path.substring(0, path.indexOf(':')) : "oxevy";
            String p = path.contains(":") ? path.substring(path.indexOf(':') + 1) : path;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ns, p);
            var res = Minecraft.getInstance().getResourceManager().getResource(id).orElse(null);
            if (res == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(res.open(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            System.err.println("Failed to load shader: " + path + " - " + e.getMessage());
            return null;
        }
    }
}
