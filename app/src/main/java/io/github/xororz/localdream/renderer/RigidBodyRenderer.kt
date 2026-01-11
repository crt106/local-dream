package io.github.xororz.localdream.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class RigidBodyRenderer(
    private val context: Context,
    private val config: TrackingConfig,
    private val textureOverrides: Map<String, Bitmap> = emptyMap(),
    private val onSurfaceTextureAvailable: (SurfaceTexture) -> Unit,
    private val getPresentationTimeUs: () -> Long,
    private val getVideoFrameRate: () -> Float
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val TAG = "RigidBodyRenderer"
    private var surfaceTexture: SurfaceTexture? = null
    private var videoTextureId: Int = 0

    private inner class ElementRenderer(val element: TrackingElement) {
        var textureId: Int = 0
        var verticesBuffer: FloatBuffer
        var isVisible: Boolean = false

        init {
            val data = FloatArray(20)
            verticesBuffer = ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
        }

        fun updateVertices(frameData: FrameData) {
            if (frameData.corners.size < 4) return
            val bl = frameData.corners[2]
            val br = frameData.corners[3]
            val tl = frameData.corners[0]
            val tr = frameData.corners[1]

            val vertices = floatArrayOf(
                toNdcX(bl.x), toNdcY(bl.y), 0f, 0.0f, 1.0f,
                toNdcX(br.x), toNdcY(br.y), 0f, 1.0f, 1.0f,
                toNdcX(tl.x), toNdcY(tl.y), 0f, 0.0f, 0.0f,
                toNdcX(tr.x), toNdcY(tr.y), 0f, 1.0f, 0.0f
            )
            verticesBuffer.position(0)
            verticesBuffer.put(vertices).position(0)
        }

        fun draw() {
            if (!isVisible) return
            GLES30.glUseProgram(overlayProgramId)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
            GLES30.glUniform1i(overlayUTextureHandle, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
            GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
            GLES30.glUniform1i(overlayUMaskTextureHandle, 1)

            val r = element.maskColor[0] / 255.0f
            val g = element.maskColor[1] / 255.0f
            val b = element.maskColor[2] / 255.0f
            GLES30.glUniform3f(overlayUTargetMaskColorHandle, r, g, b)
            GLES30.glUniform1f(overlayUColorToleranceHandle, 0.45f) // 显著提高容差以应对压缩噪点

            Matrix.setIdentityM(mVPMatrix, 0)
            GLES30.glUniformMatrix4fv(overlayUMVPMatrixHandle, 1, false, mVPMatrix, 0)
            GLES30.glUniformMatrix4fv(overlayUSTMatrixHandle, 1, false, sTMatrix, 0)

            verticesBuffer.position(0)
            GLES30.glVertexAttribPointer(overlayAPositionHandle, 3, GLES30.GL_FLOAT, false, 20, verticesBuffer)
            GLES30.glEnableVertexAttribArray(overlayAPositionHandle)
            verticesBuffer.position(3)
            GLES30.glVertexAttribPointer(overlayATextureCoordHandle, 2, GLES30.GL_FLOAT, false, 20, verticesBuffer)
            GLES30.glEnableVertexAttribArray(overlayATextureCoordHandle)
            
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
    }

    private val elementRenderers = mutableListOf<ElementRenderer>()
    private val mVPMatrix = FloatArray(16)
    private val sTMatrix = FloatArray(16)

    private var bgProgramId: Int = 0
    private var bgUMVPMatrixHandle: Int = 0
    private var bgUSTMatrixHandle: Int = 0
    private var bgAPositionHandle: Int = 0
    private var bgATextureCoordHandle: Int = 0
    private var bgUVideoTextureHandle: Int = 0

    private var overlayProgramId: Int = 0
    private var overlayUMVPMatrixHandle: Int = 0
    private var overlayUSTMatrixHandle: Int = 0
    private var overlayAPositionHandle: Int = 0
    private var overlayATextureCoordHandle: Int = 0
    private var overlayUTextureHandle: Int = 0
    private var overlayUMaskTextureHandle: Int = 0
    private var overlayUTargetMaskColorHandle: Int = 0
    private var overlayUColorToleranceHandle: Int = 0

    private val fullScreenVertices: FloatBuffer
    private var updateSurface = false
    private val SOURCE_WIDTH = 720f
    private val SOURCE_HEIGHT = 1280f

    init {
        val fullScreenData = floatArrayOf(
            -1.0f, -1.0f, 0f, 0.0f, 0.0f,
            1.0f, -1.0f, 0f, 1.0f, 0.0f,
            -1.0f, 1.0f, 0f, 0.0f, 1.0f,
            1.0f, 1.0f, 0f, 1.0f, 1.0f
        )
        fullScreenVertices = ByteBuffer.allocateDirect(fullScreenData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        fullScreenVertices.put(fullScreenData).position(0)
        Matrix.setIdentityM(sTMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        videoTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR.toFloat())
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR.toFloat())

        surfaceTexture = SurfaceTexture(videoTextureId)
        surfaceTexture?.setOnFrameAvailableListener(this)
        onSurfaceTextureAvailable(surfaceTexture!!)

        elementRenderers.clear()
        this.config.elements.forEach { element ->
            val er = ElementRenderer(element)
            // 优先使用外部传入的纹理覆盖，否则从 assets 加载
            val overrideBitmap = textureOverrides[element.id]
            if (overrideBitmap != null) {
                Log.d(TAG, "Using texture override for element: ${element.id}, size: ${overrideBitmap.width}x${overrideBitmap.height}")
                er.textureId = loadTextureFromBitmap(overrideBitmap)
            } else {
                Log.d(TAG, "Loading texture from assets for element: ${element.id}")
                er.textureId = loadTextureFromAssets(context, element.texturePath)
            }
            elementRenderers.add(er)
        }

        initBgShader()
        initOverlayShader()
    }

    private fun initBgShader() {
        val vs = loadShader(GLES30.GL_VERTEX_SHADER, BG_VERTEX_SHADER)
        val fs = loadShader(GLES30.GL_FRAGMENT_SHADER, BG_FRAGMENT_SHADER)
        bgProgramId = createProgram(vs, fs)
        bgUMVPMatrixHandle = GLES30.glGetUniformLocation(bgProgramId, "uMVPMatrix")
        bgUSTMatrixHandle = GLES30.glGetUniformLocation(bgProgramId, "uSTMatrix")
        bgAPositionHandle = GLES30.glGetAttribLocation(bgProgramId, "aPosition")
        bgATextureCoordHandle = GLES30.glGetAttribLocation(bgProgramId, "aTextureCoord")
        bgUVideoTextureHandle = GLES30.glGetUniformLocation(bgProgramId, "uVideoTexture")
    }

    private fun initOverlayShader() {
        val vs = loadShader(GLES30.GL_VERTEX_SHADER, OVERLAY_VERTEX_SHADER)
        val fs = loadShader(GLES30.GL_FRAGMENT_SHADER, OVERLAY_FRAGMENT_SHADER)
        overlayProgramId = createProgram(vs, fs)
        overlayUMVPMatrixHandle = GLES30.glGetUniformLocation(overlayProgramId, "uMVPMatrix")
        overlayUSTMatrixHandle = GLES30.glGetUniformLocation(overlayProgramId, "uSTMatrix")
        overlayAPositionHandle = GLES30.glGetAttribLocation(overlayProgramId, "aPosition")
        overlayATextureCoordHandle = GLES30.glGetAttribLocation(overlayProgramId, "aTextureCoord")
        overlayUTextureHandle = GLES30.glGetUniformLocation(overlayProgramId, "uTexture")
        overlayUMaskTextureHandle = GLES30.glGetUniformLocation(overlayProgramId, "uMaskTexture")
        overlayUTargetMaskColorHandle = GLES30.glGetUniformLocation(overlayProgramId, "uTargetMaskColor")
        overlayUColorToleranceHandle = GLES30.glGetUniformLocation(overlayProgramId, "uColorTolerance")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (updateSurface) {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(sTMatrix)
                updateSurface = false
            }
        }
        val presentationTimeUs = getPresentationTimeUs()
        val frameRate = getVideoFrameRate()
        if (frameRate > 0f && presentationTimeUs >= 0) {
            val positionSec = presentationTimeUs / 1_000_000.0
            val exactFrame = (positionSec * frameRate + 0.5).toInt()
            elementRenderers.forEach { er ->
                val frameData = er.element.frames.find { it.frame == exactFrame }
                if (frameData != null) {
                    er.updateVertices(frameData)
                    er.isVisible = true
                } else {
                    er.isVisible = false
                }
            }
        } else {
            elementRenderers.forEach { it.isVisible = false }
        }
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT or GLES30.GL_COLOR_BUFFER_BIT)
        drawBackground()
        elementRenderers.forEach { it.draw() }
    }

    private fun toNdcX(x: Float): Float = (x / SOURCE_WIDTH) * 2f - 1f
    private fun toNdcY(y: Float): Float = 1f - (y / SOURCE_HEIGHT) * 2f

    private fun drawBackground() {
        GLES30.glUseProgram(bgProgramId)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES30.glUniform1i(bgUVideoTextureHandle, 0)
        Matrix.setIdentityM(mVPMatrix, 0)
        GLES30.glUniformMatrix4fv(bgUMVPMatrixHandle, 1, false, mVPMatrix, 0)
        GLES30.glUniformMatrix4fv(bgUSTMatrixHandle, 1, false, sTMatrix, 0)
        fullScreenVertices.position(0)
        GLES30.glVertexAttribPointer(bgAPositionHandle, 3, GLES30.GL_FLOAT, false, 20, fullScreenVertices)
        GLES30.glEnableVertexAttribArray(bgAPositionHandle)
        fullScreenVertices.position(3)
        GLES30.glVertexAttribPointer(bgATextureCoordHandle, 2, GLES30.GL_FLOAT, false, 20, fullScreenVertices)
        GLES30.glEnableVertexAttribArray(bgATextureCoordHandle)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) { updateSurface = true }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, shaderCode)
        GLES30.glCompileShader(shader)
        return shader
    }

    private fun createProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)
        return program
    }

    private fun loadTextureFromAssets(context: Context, assetPath: String): Int {
        val textureHandle = IntArray(1)
        GLES30.glGenTextures(1, textureHandle, 0)
        if (textureHandle[0] != 0) {
            try {
                context.assets.open(assetPath).use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    if (bitmap != null) {
                        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])
                        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Texture load error", e)
            }
        }
        return textureHandle[0]
    }

    /**
     * 从 Bitmap 创建 OpenGL 纹理
     * 注意：不回收 bitmap，由 TextureDataManager 管理生命周期
     */
    private fun loadTextureFromBitmap(bitmap: Bitmap): Int {
        val textureHandle = IntArray(1)
        GLES30.glGenTextures(1, textureHandle, 0)
        if (textureHandle[0] != 0) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            Log.d(TAG, "Created texture from bitmap: handle=${textureHandle[0]}, size=${bitmap.width}x${bitmap.height}")
        } else {
            Log.e(TAG, "Failed to generate texture handle")
        }
        return textureHandle[0]
    }

    companion object {
        private const val BG_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 v_TexCoord;
            void main() {
              gl_Position = uMVPMatrix * aPosition;
              v_TexCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """
        private const val BG_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES uVideoTexture;
            void main() {
                vec2 leftCoord = vec2(v_TexCoord.x * 0.5, v_TexCoord.y);
                gl_FragColor = texture2D(uVideoTexture, leftCoord);
            }
        """
        private const val OVERLAY_VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 v_TexCoord;
            varying vec2 v_ScreenCoord;
            void main() {
              gl_Position = uMVPMatrix * aPosition;
              v_TexCoord = aTextureCoord.xy;
              vec2 baseUV = (aPosition.xy + 1.0) * 0.5;
              v_ScreenCoord = (uSTMatrix * vec4(baseUV, 0.0, 1.0)).xy;
            }
        """
        private const val OVERLAY_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            varying vec2 v_ScreenCoord;
            uniform sampler2D uTexture;
            uniform samplerExternalOES uMaskTexture;
            uniform vec3 uTargetMaskColor;
            uniform float uColorTolerance;
            void main() {
                vec4 overlayColor = texture2D(uTexture, v_TexCoord);
                vec2 maskCoord = vec2(v_ScreenCoord.x * 0.5 + 0.5, v_ScreenCoord.y);
                vec4 maskSample = texture2D(uMaskTexture, maskCoord);
                float dist = distance(maskSample.rgb, uTargetMaskColor);
                float softness = 0.1; // 减小边缘平滑带，增加确切程度
                float lowerEdge = max(0.0, uColorTolerance - softness);
                float upperEdge = uColorTolerance + softness;
                // 使用反向逻辑：距离越近，visibility 越高
                float visibility = 1.0 - smoothstep(lowerEdge, upperEdge, dist);
                gl_FragColor = vec4(overlayColor.rgb, overlayColor.a * visibility);
            }
        """
    }
}
