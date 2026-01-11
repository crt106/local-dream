package io.github.xororz.localdream.renderer

import io.github.xororz.localdream.R
import android.content.Context
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

class SoftBodyRenderer(
    private val context: Context,
    private val onSurfaceTextureAvailable: (SurfaceTexture) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val TAG = "SoftBodyRenderer"

    private var surfaceTexture: SurfaceTexture? = null
    private var videoTextureId: Int = 0
    private var testTextureId: Int = 0

    private val mVPMatrix = FloatArray(16)
    private val sTMatrix = FloatArray(16)

    private var programId: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uSTMatrixHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var aTextureCoordHandle: Int = 0
    private var uVideoTextureHandle: Int = 0
    private var uTestTextureHandle: Int = 0
    private var uTextureScaleHandle: Int = 0

    // Scale factor for the texture to correct distortion.
    // X, Y. 1.0 means no scaling.
    // Adjust these values to fix aspect ratio issues.
    var textureScaleX: Float = 1.0f
    var textureScaleY: Float = 1.0f

    private val triangleVerticesData = floatArrayOf(
        // X, Y, Z, U, V
        -1.0f, -1.0f, 0f, 0.0f, 0.0f,
        1.0f, -1.0f, 0f, 1.0f, 0.0f,
        -1.0f, 1.0f, 0f, 0.0f, 1.0f,
        1.0f, 1.0f, 0f, 1.0f, 1.0f
    )

    private val triangleVertices: FloatBuffer

    private var updateSurface = false

    init {
        triangleVertices = ByteBuffer.allocateDirect(triangleVerticesData.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        triangleVertices.put(triangleVerticesData).position(0)
        Matrix.setIdentityM(sTMatrix, 0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated start")
        
        // Create OES Texture for Video
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        videoTextureId = textures[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        checkGlError("glBindTexture videoTextureId")

        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR.toFloat())
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR.toFloat())

        // Create SurfaceTexture
        surfaceTexture = SurfaceTexture(videoTextureId)
        surfaceTexture?.setOnFrameAvailableListener(this)
        onSurfaceTextureAvailable(surfaceTexture!!)

        // Load Test Texture (Static Image) with Mipmaps
        testTextureId = loadTexture(context, R.drawable.texture_cat1)

        // Compile Shaders
        val vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = loadShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertexShader)
        GLES30.glAttachShader(programId, fragmentShader)

        GLES30.glLinkProgram(programId)
        Log.d(TAG, "Shaders linked. Program ID: $programId")
        
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES30.GL_TRUE) {
            Log.e(TAG, "Could not link program: ")
            Log.e(TAG, GLES30.glGetProgramInfoLog(programId))
            GLES30.glDeleteProgram(programId)
            programId = 0
            return
        }

        uMVPMatrixHandle = GLES30.glGetUniformLocation(programId, "uMVPMatrix")
        uSTMatrixHandle = GLES30.glGetUniformLocation(programId, "uSTMatrix")
        aPositionHandle = GLES30.glGetAttribLocation(programId, "aPosition")
        aTextureCoordHandle = GLES30.glGetAttribLocation(programId, "aTextureCoord")
        uVideoTextureHandle = GLES30.glGetUniformLocation(programId, "u_VideoTexture")
        uTestTextureHandle = GLES30.glGetUniformLocation(programId, "u_TestTexture")
        uTextureScaleHandle = GLES30.glGetUniformLocation(programId, "u_TextureScale")

        checkGlError("glGetUniformLocation")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged width=$width height=$height")
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

        GLES30.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT or GLES30.GL_COLOR_BUFFER_BIT)

        GLES30.glUseProgram(programId)
        checkGlError("glUseProgram")

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES30.glUniform1i(uVideoTextureHandle, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, testTextureId)
        GLES30.glUniform1i(uTestTextureHandle, 1)

        GLES30.glUniform2f(uTextureScaleHandle, textureScaleX, textureScaleY)

        triangleVertices.position(0)
        GLES30.glVertexAttribPointer(aPositionHandle, 3, GLES30.GL_FLOAT, false, 20, triangleVertices)
        GLES30.glEnableVertexAttribArray(aPositionHandle)

        triangleVertices.position(3)
        GLES30.glVertexAttribPointer(aTextureCoordHandle, 2, GLES30.GL_FLOAT, false, 20, triangleVertices)
        GLES30.glEnableVertexAttribArray(aTextureCoordHandle)

        Matrix.setIdentityM(mVPMatrix, 0)
        GLES30.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mVPMatrix, 0)
        GLES30.glUniformMatrix4fv(uSTMatrixHandle, 1, false, sTMatrix, 0)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            updateSurface = true
        }
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES30.glCreateShader(shaderType)
        if (shader != 0) {
            GLES30.glShaderSource(shader, source)
            GLES30.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $shaderType:")
                Log.e(TAG, "Shader Source: $source")
                Log.e(TAG, GLES30.glGetShaderInfoLog(shader))
                GLES30.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }

    private fun loadTexture(context: Context, resourceId: Int): Int {
        val textureHandle = IntArray(1)
        GLES30.glGenTextures(1, textureHandle, 0)
        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options()
            options.inScaled = false
            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)

            if (bitmap != null) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])
                
                // Mipmaps for Anti-Aliasing (Flickering fix)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
                
                // Generate Mipmaps
                GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
                
                bitmap.recycle()
            }
        }
        return textureHandle[0]
    }

    private fun checkGlError(op: String) {
        var error: Int
        while (GLES30.glGetError().also { error = it } != GLES30.GL_NO_ERROR) {
            Log.e(TAG, "$op: glError $error")
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
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

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_VideoTexture;
            uniform sampler2D u_TestTexture;
            uniform vec2 u_TextureScale; 
            
            void main() {
                // v_TexCoord is in 0..1 range (Screen Space).
                
                // 1. Sample Original Video (Left Eye)
                vec2 leftEyeCoord = vec2(v_TexCoord.x * 0.5, v_TexCoord.y);
                vec4 originalColor = texture2D(u_VideoTexture, leftEyeCoord);
                
                // 2. Sample UV Data (Right Eye)
                float uvHeightRatio = 1080.0 / 1280.0;
                
                vec4 replaceColor = vec4(0.0);
                float alpha = 0.0;
                
                // Only process if within the valid UV video area
                if (v_TexCoord.y <= uvHeightRatio) {
                    vec2 uvMapCoord = vec2(v_TexCoord.x * 0.5 + 0.5, v_TexCoord.y);
                    vec4 uvData = texture2D(u_VideoTexture, uvMapCoord);
                    
                    // Decode UVs
                    vec2 rawUV = vec2(uvData.r, uvData.g);
                    
                    // -----------------------------------------------------------
                    // Fix 1: Threshold Logic
                    // 0.001 was too low (let background noise in).
                    // 0.01 caused top-left clipping.
                    // Trying 0.005 as a sweet spot.
                    // Using length() is more robust than separate x/y checks.
                    // -----------------------------------------------------------
                    if (length(rawUV) > 0.005) {
                        
                        // Apply Scale: (UV - 0.5) * Scale + 0.5
                        vec2 adjustedUV = (rawUV - 0.5) * u_TextureScale + 0.5;
                        
                        // -----------------------------------------------------------
                        // Fix 2: Restore Boundary Check (CRITICAL)
                        // This prevents background noise (which passes threshold but maps to < 0 or > 1) 
                        // from smearing the texture edges across the screen due to CLAMP_TO_EDGE.
                        // -----------------------------------------------------------
                        if (adjustedUV.x >= 0.0 && adjustedUV.x <= 1.0 && 
                            adjustedUV.y >= 0.0 && adjustedUV.y <= 1.0) {
                            
                            replaceColor = texture2D(u_TestTexture, adjustedUV);
                            alpha = 1.0;
                        }
                    }
                }
                
                // Mix original video with the replacement texture
                gl_FragColor = mix(originalColor, replaceColor, alpha);
            }
        """
    }
}
