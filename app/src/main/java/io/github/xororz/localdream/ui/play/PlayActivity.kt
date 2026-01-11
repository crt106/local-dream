package io.github.xororz.localdream.ui.play

import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import android.graphics.Bitmap
import io.github.xororz.localdream.data.TextureDataManager
import io.github.xororz.localdream.renderer.*
import io.github.xororz.localdream.ui.theme.LocalDreamTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicLong

@androidx.annotation.OptIn(UnstableApi::class)
class PlayActivity : ComponentActivity() {

    private val TAG = "PlayActivity"
    private var player: ExoPlayer? = null
    private var renderer: RigidBodyRenderer? = null
    
    private val currentPresentationTimeUs = AtomicLong(0L)
    @Volatile private var videoFrameRate: Float = 30f

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        android.util.Log.d(TAG, "onCreate: START")
        
        val clipId = intent?.getStringExtra("clipId") ?: "playlet_01"
        val useGeneratedTextures = intent?.getBooleanExtra("useGeneratedTextures", false) ?: false
        
        // 获取生成的纹理覆盖
        val textureOverrides: Map<String, Bitmap> = if (useGeneratedTextures) {
            TextureDataManager.getAllTextures()
        } else {
            emptyMap()
        }
        android.util.Log.d(TAG, "clipId=$clipId, useGeneratedTextures=$useGeneratedTextures, textureCount=${textureOverrides.size}")
        
        try {
            val clipId = clipId
            val trackingConfig = loadTrackingConfig(clipId)

            if (trackingConfig != null) {
                // 初始化 Player
                player = ExoPlayer.Builder(this).build()
                player?.setVideoFrameMetadataListener { presentationTimeUs, _, _, _ ->
                    currentPresentationTimeUs.set(presentationTimeUs)
                }
                
                setContent {
                    LocalDreamTheme {
                        PlayScreen(
                            trackingConfig = trackingConfig,
                            player = player,
                            currentPresentationTimeUs = currentPresentationTimeUs,
                            videoFrameRate = videoFrameRate,
                            textureOverrides = textureOverrides,
                            onRendererCreated = { createdRenderer ->
                                renderer = createdRenderer
                            }
                        )
                    }
                }
                
                // 设置播放器监听器
                setupPlayerListeners()
                
                // 开始播放
                val videoUri = "asset:///${trackingConfig.videoPath}"
                android.util.Log.d(TAG, "Initializing player with: $videoUri")
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUri))
                player?.setMediaItem(mediaItem)
                player?.prepare()
                player?.playWhenReady = true
                player?.repeatMode = ExoPlayer.REPEAT_MODE_ONE
                
            } else {
                android.util.Log.e(TAG, "Failed to load tracking config")
                finish()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "CRITICAL ERROR in onCreate", e)
        }
    }

    private fun setupPlayerListeners() {
        player?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                // 视频尺寸变化处理已在 Compose 中处理
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e(TAG, "ExoPlayer Error", error)
            }
        })
        
        player?.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoInputFormatChanged(eventTime: AnalyticsListener.EventTime, format: androidx.media3.common.Format, decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?) {
                if (format.frameRate > 0) {
                    videoFrameRate = format.frameRate
                }
            }
        })
    }

    private fun loadTrackingConfig(clipId: String): TrackingConfig? {
        try {
            val configPath = "clips/$clipId/config.json"
            val jsonString = assets.open(configPath).bufferedReader().use { it.readText() }
            val jsonObj = org.json.JSONObject(jsonString)
            val id = jsonObj.getString("id")
            val videoPath = "clips/$clipId/${jsonObj.getString("videoPath")}"
            val elementsArray = jsonObj.getJSONArray("elements")
            val elements = mutableListOf<TrackingElement>()
            for (i in 0 until elementsArray.length()) {
                val elemObj = elementsArray.getJSONObject(i)
                val elemId = elemObj.getString("id")
                val texturePath = "clips/$clipId/${elemObj.getString("texturePath")}"
                val recommendedWidth = elemObj.optInt("recommendedWidth", 0)
                val recommendedHeight = elemObj.optInt("recommendedHeight", 0)
                val colorArray = elemObj.getJSONArray("maskColor")
                val maskColor = intArrayOf(colorArray.getInt(0), colorArray.getInt(1), colorArray.getInt(2))
                val framesArray = elemObj.getJSONArray("frames")
                val frames = mutableListOf<FrameData>()
                for (j in 0 until framesArray.length()) {
                    val frameObj = framesArray.getJSONObject(j)
                    val cornersArray = frameObj.getJSONArray("corners")
                    val corners = mutableListOf<Point>()
                    for (k in 0 until cornersArray.length()) {
                        val pObj = cornersArray.getJSONObject(k)
                        corners.add(Point(pObj.getDouble("x").toFloat(), pObj.getDouble("y").toFloat()))
                    }
                    frames.add(FrameData(frameObj.getInt("frame"), corners))
                }
                elements.add(TrackingElement(elemId, texturePath, recommendedWidth, recommendedHeight, maskColor, frames))
            }
            return TrackingConfig(id, videoPath, elements)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "JSON error", e)
            return null
        }
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}

@Composable
fun PlayScreen(
    trackingConfig: TrackingConfig,
    player: ExoPlayer?,
    currentPresentationTimeUs: AtomicLong,
    videoFrameRate: Float,
    textureOverrides: Map<String, Bitmap> = emptyMap(),
    onRendererCreated: (RigidBodyRenderer) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    
    // 定期更新播放进度
    LaunchedEffect(Unit) {
        while (true) {
            player?.let {
                currentPosition = it.currentPosition
                duration = it.duration.coerceAtLeast(0L)
            }
            delay(100) // 每 100ms 更新一次
        }
    }
    
    // 创建 GLSurfaceView
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            
            val createdRenderer = RigidBodyRenderer(
                context = context,
                config = trackingConfig,
                textureOverrides = textureOverrides,
                onSurfaceTextureAvailable = { surfaceTexture ->
                    android.util.Log.d("PlayActivity", "SurfaceTexture available, switching to main thread to set surface")
                    val surface = Surface(surfaceTexture)
                    scope.launch(Dispatchers.Main) {
                        player?.setVideoSurface(surface)
                    }
                },
                getPresentationTimeUs = { currentPresentationTimeUs.get() },
                getVideoFrameRate = { videoFrameRate }
            )
            
            setRenderer(createdRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            onRendererCreated(createdRenderer)
        }
    }
    
    // 处理 GLSurfaceView 生命周期
    DisposableEffect(Unit) {
        glSurfaceView.onResume()
        onDispose {
            glSurfaceView.onPause()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. 背景视频层 - 占据全屏或保持比例，点击切换播放/暂停
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    player?.let {
                        if (it.isPlaying) {
                            it.pause()
                            isPlaying = false
                        } else {
                            it.play()
                            isPlaying = true
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { glSurfaceView },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(9f / 16f)
            )
        }

        // 2. UI 交互层 - 叠加在视频之上
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // 顶部导航栏区域
            DouyinTopBar(
                modifier = Modifier.statusBarsPadding()
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 下半部分内容区域
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 文案和标签 (左侧)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 12.dp, end = 93.dp) // 右侧留出 93dp 间距
                        .fillMaxWidth()
                ) {
                    // 付费标签
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFE9B25A), // 假设的金色，实际应参考 Figma 图片颜色
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "付费",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = Color(0x57292929), // 参考 Figma maskBottom rectangle
                            shape = RoundedCornerShape(2.dp),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = "试看00:10 购买",
                                    color = Color(0xFFE9B25A),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFFE9B25A),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "@兔了个剧场",
                        color = Color.White,
                        fontSize = 17.sp, // Figma: 17px
                        fontWeight = FontWeight.Medium, // Figma: 500
                        lineHeight = 24.sp // Figma: 24px
                    )
                    
                    Spacer(modifier = Modifier.height(5.dp)) // Figma: margin-top 5px
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "第 1 集",
                            color = Color.White,
                            fontSize = 15.sp, // Figma: 15px
                            lineHeight = 21.sp // Figma: 21px
                        )
                        Spacer(modifier = Modifier.width(6.dp)) // Figma: column-gap 6px
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(13.dp) // Figma: height 13px
                                .background(Color(0x33FFFFFF)) // Figma: #ffffff33
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "刁真真用美食征服后宫的奇妙旅程",
                            color = Color.White,
                            fontSize = 15.sp, // Figma: 15px
                            maxLines = 1
                        )
                    }
                    
                    // 描述文字
                    Text(
                        text = "真的是非常特别的一段旅程呢～",
                        color = Color.White,
                        fontSize = 15.sp, // Figma: 15px
                        lineHeight = 21.sp, // Figma: 21px
                        maxLines = 2
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp)) // Figma: margin-top 12px
                    
                    // 短剧链接
                    Surface(
                        color = Color(0x57292929), // Figma: #29292957
                        shape = RoundedCornerShape(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), // Figma: padding 10px 12px
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow, // 暂时用 PlayArrow 替代 icDuanju
                                contentDescription = null,
                                tint = Color(0xE5FFFFFF), // Figma: #ffffffe5
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "短剧",
                                color = Color(0xE5FFFFFF), // Figma: #ffffffe5
                                fontSize = 13.sp, // Figma: 13px
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xE5FFFFFF))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "霸道发发爱上我之天地良心",
                                color = Color(0xE5FFFFFF), // Figma: #ffffffe5
                                fontSize = 13.sp, // Figma: 13px
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xE5FFFFFF), // Figma: #ffffffe5
                                modifier = Modifier.size(20.dp) // Figma: 20px
                            )
                        }
                    }
                }

                // 右侧操作按钮
                DouyinSideActions(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 12.dp)
                )
            }

            // 进度条
            // VideoProgressBar(
            //     currentPosition = currentPosition,
            //     duration = duration,
            //     onSeek = { position ->
            //         player?.seekTo(position)
            //     },
            //     modifier = Modifier
            //         .fillMaxWidth()
            //         .padding(bottom = 2.dp)
            // )
            
            // 底部导航栏
            DouyinBottomBar()
        }
    }
}
