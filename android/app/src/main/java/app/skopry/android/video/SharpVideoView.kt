package app.skopry.android.video

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import android.view.SurfaceHolder
import app.skopry.android.client.SkopryClient
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * The picture drawn through a bicubic (Catmull-Rom) resampler instead of the compositor's bilinear one. The
 * decoder renders into a SurfaceTexture and this view's own GL surface, the size the picture has on the screen,
 * is filled from it by a shader. At a fractional enlargement (the panel 1.31× the stream, Aa 1×) bilinear smears
 * every edge over three pixels; Catmull-Rom keeps them tighter (edge width 1.76 against 1.93 measured on the same
 * text) and is the identity at 1:1, so it costs nothing where the picture is already exact.
 *
 * Opt-in (`SkopryClient.sharpUpscale`): it adds a GL pass between the decoder and the screen, about one display
 * frame of latency.
 */
class SharpVideoView(context: Context, private val client: SkopryClient) : GLSurfaceView(context) {
    private val drawer = Drawer()

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 0, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(drawer)
        renderMode = RENDERMODE_WHEN_DIRTY
        holder.addCallback(object : SurfaceHolder.Callback {
            // The GL context outlives the screen's surface, so the decoder's SurfaceTexture does too: it is
            // only told to show again (and, while there is no screen, to decode without showing).
            override fun surfaceCreated(h: SurfaceHolder) { drawer.surface?.let { client.decoder.setSurface(it) } }
            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) { drawer.surface?.let { client.decoder.setSurface(it) } }
            override fun surfaceDestroyed(h: SurfaceHolder) { client.decoder.detachSurfaceNow() }
        })
    }

    override fun onDetachedFromWindow() {
        client.decoder.detachSurfaceNow()
        super.onDetachedFromWindow()
        drawer.release()
    }

    private inner class Drawer : Renderer, SurfaceTexture.OnFrameAvailableListener {
        @Volatile var surface: Surface? = null
        private var texture: SurfaceTexture? = null
        private var program = 0
        private var textureId = 0
        private var aPosition = 0; private var aCoord = 0
        private var uMatrix = 0; private var uSize = 0; private var uTex = 0
        private val matrix = FloatArray(16)
        private val quad = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            // x, y, u, v: a strip over the whole surface; (u, v) = (0, 0) is the bottom left, as SurfaceTexture's matrix expects.
            put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            program = link(VERTEX, FRAGMENT)
            aPosition = GLES20.glGetAttribLocation(program, "aPosition"); aCoord = GLES20.glGetAttribLocation(program, "aCoord")
            uMatrix = GLES20.glGetUniformLocation(program, "uMatrix"); uSize = GLES20.glGetUniformLocation(program, "uSize"); uTex = GLES20.glGetUniformLocation(program, "uTex")
            val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); textureId = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val t = SurfaceTexture(textureId).also { it.setOnFrameAvailableListener(this) }
            texture = t
            val s = Surface(t)
            surface = s
            client.decoder.setSurface(s)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { GLES20.glViewport(0, 0, width, height) }

        override fun onDrawFrame(gl: GL10?) {
            val t = texture ?: return
            try { t.updateTexImage() } catch (_: RuntimeException) { return }
            t.getTransformMatrix(matrix)
            val size = client.videoSize ?: return
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glUniformMatrix4fv(uMatrix, 1, false, matrix, 0)
            GLES20.glUniform2f(uSize, size.first.toFloat(), size.second.toFloat())
            quad.position(0); GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(aPosition)
            quad.position(2); GLES20.glVertexAttribPointer(aCoord, 2, GLES20.GL_FLOAT, false, 16, quad); GLES20.glEnableVertexAttribArray(aCoord)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        override fun onFrameAvailable(t: SurfaceTexture?) { requestRender() }

        fun release() {
            surface?.release(); surface = null
            texture?.release(); texture = null
        }

        private fun link(vertex: String, fragment: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type)
                GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
                val ok = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
                check(ok[0] != 0) { "shader: " + GLES20.glGetShaderInfoLog(shader) }
                return shader
            }
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vertex)); GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fragment))
            GLES20.glLinkProgram(p)
            val ok = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            check(ok[0] != 0) { "link: " + GLES20.glGetProgramInfoLog(p) }
            return p
        }
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aCoord;
            varying vec2 vCoord;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); vCoord = aCoord; }
        """

        /**
         * Catmull-Rom in 9 taps: the 16 texels of a bicubic footprint are read as 9 bilinear samples (Sigg and
         * Hadwiger, "Fast Third-Order Texture Filtering", GPU Gems 2). Positions are in texels of the decoded
         * picture, clamped to its inside so the codec's padding (1866 wide in a 1872 buffer) is never read.
         */
        const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            uniform samplerExternalOES uTex;
            uniform mat4 uMatrix;
            uniform vec2 uSize;
            varying vec2 vCoord;
            vec4 tap(vec2 p) {
                vec2 q = clamp(p, vec2(0.5), uSize - 0.5);
                return texture2D(uTex, (uMatrix * vec4(q / uSize, 0.0, 1.0)).xy);
            }
            void main() {
                vec2 pos = vCoord * uSize;
                vec2 c = floor(pos - 0.5) + 0.5;
                vec2 f = pos - c;
                vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
                vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
                vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
                vec2 w3 = f * f * (-0.5 + 0.5 * f);
                vec2 w12 = w1 + w2;
                vec2 p0 = c - 1.0;
                vec2 p3 = c + 2.0;
                vec2 p12 = c + w2 / w12;
                vec4 color =
                    tap(vec2(p0.x, p0.y)) * w0.x * w0.y + tap(vec2(p12.x, p0.y)) * w12.x * w0.y + tap(vec2(p3.x, p0.y)) * w3.x * w0.y +
                    tap(vec2(p0.x, p12.y)) * w0.x * w12.y + tap(vec2(p12.x, p12.y)) * w12.x * w12.y + tap(vec2(p3.x, p12.y)) * w3.x * w12.y +
                    tap(vec2(p0.x, p3.y)) * w0.x * w3.y + tap(vec2(p12.x, p3.y)) * w12.x * w3.y + tap(vec2(p3.x, p3.y)) * w3.x * w3.y;
                gl_FragColor = vec4(color.rgb, 1.0);
            }
        """
    }
}
