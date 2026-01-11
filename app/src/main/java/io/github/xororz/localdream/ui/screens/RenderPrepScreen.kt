package io.github.xororz.localdream.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.xororz.localdream.R
import io.github.xororz.localdream.data.ClipInfo
import io.github.xororz.localdream.data.ClipRepository
import io.github.xororz.localdream.data.ElementInfo
import io.github.xororz.localdream.data.Model
import io.github.xororz.localdream.data.ModelRepository
import io.github.xororz.localdream.data.PatchScanner
import io.github.xororz.localdream.data.Resolution
import io.github.xororz.localdream.data.TextureDataManager
import io.github.xororz.localdream.service.BackendService
import io.github.xororz.localdream.service.BackgroundGenerationService
import io.github.xororz.localdream.service.BackgroundGenerationService.GenerationState
import io.github.xororz.localdream.ui.play.PlayActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 健康检查后端服务是否就绪
 */
private suspend fun checkBackendHealthWithPolling(
    timeoutMs: Long = 60000L,
    onHealthy: () -> Unit,
    onUnhealthy: (String) -> Unit
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .build()
    
    val startTime = System.currentTimeMillis()
    
    while (currentCoroutineContext().isActive) {
        if (System.currentTimeMillis() - startTime > timeoutMs) {
            withContext(Dispatchers.Main) {
                onUnhealthy("后端启动超时")
            }
            break
        }
        
        try {
            val request = Request.Builder()
                .url("http://localhost:8081/health")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                withContext(Dispatchers.Main) {
                    onHealthy()
                }
                break
            }
        } catch (e: Exception) {
            // 连接失败，继续重试
        }
        
        delay(200)
    }
}

/**
 * 每个元素的生图配置
 */
data class ElementGenConfig(
    val elementId: String,
    var prompt: String = "masterpiece, best quality,",
    var negativePrompt: String = "lowres, bad anatomy,",
    var steps: Float = 20f,
    var cfg: Float = 7f,
    var seed: String = "",
    var selectedWidth: Int = 512,
    var selectedHeight: Int = 512,
    var generatedBitmap: Bitmap? = null,
    var isGenerating: Boolean = false,
    var isGenerated: Boolean = false
)

/**
 * 根据推荐尺寸找到最接近的可用分辨率
 * 优先匹配宽高比，其次匹配面积
 */
fun findBestResolution(
    recommendedWidth: Int,
    recommendedHeight: Int,
    availableResolutions: List<Resolution>
): Resolution {
    if (availableResolutions.isEmpty()) {
        return Resolution(512, 512)
    }
    
    val recommendedRatio = recommendedWidth.toFloat() / recommendedHeight.toFloat()
    val recommendedArea = recommendedWidth * recommendedHeight
    
    // 按宽高比差异和面积差异综合排序
    return availableResolutions.minByOrNull { res ->
        val ratio = res.width.toFloat() / res.height.toFloat()
        val ratioDiff = abs(ratio - recommendedRatio)
        val areaDiff = abs(res.width * res.height - recommendedArea)
        // 宽高比权重更高
        ratioDiff * 10000 + areaDiff * 0.001f
    } ?: Resolution(512, 512)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderPrepScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipRepository = remember { ClipRepository(context) }
    val modelRepository = remember { ModelRepository(context) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    // 状态
    var availableClips by remember { mutableStateOf<List<ClipInfo>>(emptyList()) }
    var selectedClip by remember { mutableStateOf<ClipInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 模型相关
    val downloadedModels = remember(modelRepository.models) {
        modelRepository.models.filter { it.isDownloaded }
    }
    var selectedModel by remember { mutableStateOf<Model?>(null) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    var availableResolutions by remember { mutableStateOf<List<Resolution>>(emptyList()) }
    
    // 元素配置列表
    val elementConfigs = remember { mutableStateListOf<ElementGenConfig>() }
    
    // 生图状态
    val serviceState by BackgroundGenerationService.generationState.collectAsState()
    val backendState by BackendService.backendState.collectAsState()
    var isGenerating by remember { mutableStateOf(false) }
    var currentGeneratingIndex by remember { mutableStateOf(-1) }
    var progress by remember { mutableStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBackendStarting by remember { mutableStateOf(false) }
    var allElementsGenerated by remember { mutableStateOf(false) }
    
    // 追踪当前后端的分辨率配置
    var currentBackendWidth by remember { mutableStateOf(512) }
    var currentBackendHeight by remember { mutableStateOf(512) }
    
    // 判断后端是否就绪
    val isBackendReady = backendState is BackendService.BackendState.Running
    
    // 加载可用的 clips
    LaunchedEffect(Unit) {
        availableClips = clipRepository.getAvailableClips()
        if (availableClips.isNotEmpty()) {
            selectedClip = availableClips.first()
        }
        if (downloadedModels.isNotEmpty()) {
            selectedModel = downloadedModels.first()
        }
        isLoading = false
    }
    
    // 当选择的模型变化时，获取可用分辨率
    LaunchedEffect(selectedModel) {
        selectedModel?.let { model ->
            availableResolutions = PatchScanner.scanAvailableResolutions(context, model.id)
            // 默认添加 512x512
            if (availableResolutions.none { it.width == 512 && it.height == 512 }) {
                availableResolutions = listOf(Resolution(512, 512)) + availableResolutions
            }
            Log.d("RenderPrepScreen", "Available resolutions for ${model.id}: $availableResolutions")
        }
    }
    
    // 当选择的 clip 或可用分辨率变化时，更新元素配置
    LaunchedEffect(selectedClip, availableResolutions) {
        selectedClip?.let { clip ->
            elementConfigs.clear()
            clip.elements.forEach { element ->
                val bestRes = findBestResolution(
                    element.recommendedWidth,
                    element.recommendedHeight,
                    availableResolutions
                )
                elementConfigs.add(
                    ElementGenConfig(
                        elementId = element.id,
                        prompt = "masterpiece, best quality,",
                        negativePrompt = "lowres, bad anatomy, bad hands,",
                        selectedWidth = bestRes.width,
                        selectedHeight = bestRes.height
                    )
                )
            }
        }
    }
    
    // 监听生图状态
    LaunchedEffect(serviceState) {
        when (val state = serviceState) {
            is GenerationState.Progress -> {
                progress = state.progress
            }
            is GenerationState.Complete -> {
                if (currentGeneratingIndex >= 0 && currentGeneratingIndex < elementConfigs.size) {
                    elementConfigs[currentGeneratingIndex] = elementConfigs[currentGeneratingIndex].copy(
                        generatedBitmap = state.bitmap,
                        isGenerating = false,
                        isGenerated = true
                    )
                }
                progress = 0f
                BackgroundGenerationService.markBitmapConsumed()
            }
            is GenerationState.Error -> {
                errorMessage = state.message
                if (currentGeneratingIndex >= 0 && currentGeneratingIndex < elementConfigs.size) {
                    elementConfigs[currentGeneratingIndex] = elementConfigs[currentGeneratingIndex].copy(
                        isGenerating = false
                    )
                }
                isGenerating = false
                progress = 0f
            }
            else -> {}
        }
    }
    
    // 检查是否所有元素都已生成
    LaunchedEffect(elementConfigs.toList()) {
        allElementsGenerated = elementConfigs.isNotEmpty() && elementConfigs.all { it.isGenerated }
    }
    
    // 启动后端服务（用于 UI 按钮手动启动）
    fun startBackendService(model: Model, width: Int, height: Int) {
        isBackendStarting = true
        errorMessage = null
        
        val intent = Intent(context, BackendService::class.java).apply {
            putExtra("modelId", model.id)
            putExtra("width", width)
            putExtra("height", height)
        }
        context.startForegroundService(intent)
        
        // 更新当前后端分辨率记录
        currentBackendWidth = width
        currentBackendHeight = height
        
        // 使用健康检查等待后端就绪
        scope.launch {
            checkBackendHealthWithPolling(
                timeoutMs = 120000L,
                onHealthy = { isBackendStarting = false },
                onUnhealthy = { msg -> 
                    errorMessage = msg
                    isBackendStarting = false
                }
            )
        }
    }
    
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("渲染准备") },
                navigationIcon = {
                    IconButton(onClick = { 
                        // 停止后端服务
                        context.stopService(Intent(context, BackendService::class.java))
                        navController.navigateUp() 
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clip 选择区域
            ClipSelectionSection(
                clips = availableClips,
                selectedClip = selectedClip,
                onClipSelected = { selectedClip = it }
            )
            
            // 模型选择区域
            if (downloadedModels.isNotEmpty()) {
                ModelSelectionSection(
                    models = downloadedModels,
                    selectedModel = selectedModel,
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it },
                    isBackendReady = isBackendReady,
                    isBackendStarting = isBackendStarting,
                    onModelSelected = { model ->
                        selectedModel = model
                        modelDropdownExpanded = false
                    },
                    onStartBackend = {
                        selectedModel?.let { model ->
                            // 使用第一个元素的尺寸启动后端
                            val width = elementConfigs.firstOrNull()?.selectedWidth ?: 512
                            val height = elementConfigs.firstOrNull()?.selectedHeight ?: 512
                            startBackendService(model, width, height)
                        }
                    }
                )
            } else {
                // 没有已下载的模型提示
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "请先在主页下载至少一个模型",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            
            // 后端状态提示
            if (isBackendStarting && !isBackendReady) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "正在启动模型后端服务...",
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            
            // 元素配置区域
            AnimatedVisibility(
                visible = selectedClip != null && elementConfigs.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                selectedClip?.let { clip ->
                    ElementConfigSection(
                        elements = clip.elements,
                        elementConfigs = elementConfigs,
                        availableResolutions = availableResolutions,
                        onConfigChange = { index, config ->
                            if (index in elementConfigs.indices) {
                                elementConfigs[index] = config
                            }
                        },
                        context = context,
                        currentGeneratingIndex = currentGeneratingIndex,
                        progress = progress
                    )
                }
            }
            
            // 错误信息显示
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                errorMessage?.let { msg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { errorMessage = null },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 生成所有图片按钮
            Button(
                onClick = {
                    if (selectedModel == null) return@Button
                    
                    scope.launch {
                        isGenerating = true
                        errorMessage = null
                        
                        // 找到第一个需要生成的元素来确定初始分辨率
                        val firstToGenerate = elementConfigs.indexOfFirst { !it.isGenerated }
                        val targetConfig = if (firstToGenerate >= 0) elementConfigs[firstToGenerate] else elementConfigs.firstOrNull()
                        
                        // 如果后端没有运行或分辨率不匹配，先启动/重启后端
                        if (!isBackendReady || 
                            (targetConfig != null && (targetConfig.selectedWidth != currentBackendWidth || targetConfig.selectedHeight != currentBackendHeight))) {
                            
                            isBackendStarting = true
                            
                            // 停止现有后端
                            if (isBackendReady) {
                                context.stopService(Intent(context, BackendService::class.java))
                                delay(500)
                            }
                            
                            // 启动后端
                            val width = targetConfig?.selectedWidth ?: 512
                            val height = targetConfig?.selectedHeight ?: 512
                            
                            val intent = Intent(context, BackendService::class.java).apply {
                                putExtra("modelId", selectedModel!!.id)
                                putExtra("width", width)
                                putExtra("height", height)
                            }
                            context.startForegroundService(intent)
                            currentBackendWidth = width
                            currentBackendHeight = height
                            
                            // 使用健康检查 API 等待后端就绪
                            var backendReady = false
                            checkBackendHealthWithPolling(
                                timeoutMs = 120000L,
                                onHealthy = { backendReady = true },
                                onUnhealthy = { msg -> errorMessage = msg }
                            )
                            
                            isBackendStarting = false
                            
                            if (!backendReady) {
                                isGenerating = false
                                return@launch
                            }
                        }
                        
                        // 依次为每个元素生成图片
                        for (i in elementConfigs.indices) {
                            if (!isGenerating) break
                            
                            val config = elementConfigs[i]
                            
                            // 跳过已成功生成的元素
                            if (config.isGenerated && config.generatedBitmap != null) {
                                Log.d("RenderPrepScreen", "Skipping already generated element: ${config.elementId}")
                                continue
                            }
                            
                            currentGeneratingIndex = i
                            
                            // 如果当前元素尺寸与后端配置不同，需要重启后端
                            if (config.selectedWidth != currentBackendWidth || 
                                config.selectedHeight != currentBackendHeight) {
                                Log.d("RenderPrepScreen", "Resolution changed: ${currentBackendWidth}x${currentBackendHeight} -> ${config.selectedWidth}x${config.selectedHeight}, restarting backend...")
                                
                                isBackendStarting = true
                                
                                // 先停止当前后端
                                context.stopService(Intent(context, BackendService::class.java))
                                delay(500)
                                
                                // 用新分辨率重启后端
                                val intent = Intent(context, BackendService::class.java).apply {
                                    putExtra("modelId", selectedModel!!.id)
                                    putExtra("width", config.selectedWidth)
                                    putExtra("height", config.selectedHeight)
                                }
                                context.startForegroundService(intent)
                                currentBackendWidth = config.selectedWidth
                                currentBackendHeight = config.selectedHeight
                                
                                // 使用健康检查 API 等待后端就绪
                                var backendReady = false
                                checkBackendHealthWithPolling(
                                    timeoutMs = 120000L,
                                    onHealthy = { backendReady = true },
                                    onUnhealthy = { msg -> errorMessage = msg }
                                )
                                
                                isBackendStarting = false
                                
                                if (!backendReady) {
                                    elementConfigs[i] = config.copy(isGenerating = false)
                                    break
                                }
                            }
                            
                            // 更新状态为生成中
                            elementConfigs[i] = config.copy(isGenerating = true)
                            
                            // 启动生成服务
                            val genIntent = Intent(context, BackgroundGenerationService::class.java).apply {
                                putExtra("prompt", config.prompt)
                                putExtra("negative_prompt", config.negativePrompt)
                                putExtra("steps", config.steps.roundToInt())
                                putExtra("cfg", config.cfg)
                                config.seed.toLongOrNull()?.let { putExtra("seed", it) }
                                putExtra("width", config.selectedWidth)
                                putExtra("height", config.selectedHeight)
                            }
                            context.startForegroundService(genIntent)
                            
                            // 等待生成完成
                            BackgroundGenerationService.generationState.first { state ->
                                state is GenerationState.Complete || state is GenerationState.Error
                            }
                            
                            // 等待服务停止
                            val waitStart = System.currentTimeMillis()
                            while (BackgroundGenerationService.isServiceRunning.value) {
                                if (System.currentTimeMillis() - waitStart > 5000L) break
                                delay(100)
                            }
                            
                            BackgroundGenerationService.resetState()
                            delay(300)
                        }
                        
                        currentGeneratingIndex = -1
                        isGenerating = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = selectedModel != null && selectedClip != null && !isGenerating && downloadedModels.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val pendingCount = elementConfigs.count { !it.isGenerated }
                    val currentInPending = elementConfigs.take(currentGeneratingIndex + 1).count { !it.isGenerated || it.isGenerating }
                    Text(if (isBackendStarting) "启动后端中..." else "生成中 ($currentInPending/$pendingCount)...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val pendingCount = elementConfigs.count { !it.isGenerated }
                    val totalCount = elementConfigs.size
                    Text(
                        text = if (pendingCount == 0) "全部已生成" else "生成图片 ($pendingCount/$totalCount)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 清除所有已生成图片按钮（只有在有已生成的图片时显示）
            AnimatedVisibility(
                visible = elementConfigs.any { it.isGenerated } && !isGenerating
            ) {
                OutlinedButton(
                    onClick = {
                        // 清除所有元素的生成状态和 Bitmap
                        for (i in elementConfigs.indices) {
                            elementConfigs[i] = elementConfigs[i].copy(
                                generatedBitmap = null,
                                isGenerated = false,
                                isGenerating = false
                            )
                        }
                        // 清除 TextureDataManager 中的数据
                        TextureDataManager.clearAll()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("清除所有已生成图片")
                }
            }
            
            // 开始渲染按钮
            Button(
                onClick = {
                    // 将生成的纹理存入 TextureDataManager
                    selectedClip?.let { clip ->
                        // 清除旧数据
                        TextureDataManager.clearAll()
                        
                        // 存储每个元素的生成纹理
                        for (i in elementConfigs.indices) {
                            val config = elementConfigs[i]
                            val element = clip.elements.getOrNull(i)
                            config.generatedBitmap?.let { bitmap ->
                                // 使用元素 ID 作为 key 存储纹理
                                val elementId = element?.id ?: "element_$i"
                                TextureDataManager.setTexture(elementId, bitmap)
                            }
                        }
                    }
                    
                    // 跳转到 PlayActivity
                    val intent = Intent(context, PlayActivity::class.java).apply {
                        putExtra("clipId", selectedClip?.id ?: "playlet_01")
                        putExtra("useGeneratedTextures", true)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = allElementsGenerated && !isGenerating,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "开始渲染",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSelectionSection(
    models: List<Model>,
    selectedModel: Model?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isBackendReady: Boolean,
    isBackendStarting: Boolean,
    onModelSelected: (Model) -> Unit,
    onStartBackend: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "选择生图模型",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 后端状态指示
                Surface(
                    color = if (isBackendReady) 
                        MaterialTheme.colorScheme.tertiaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isBackendReady) "✓ 后端就绪" else "○ 未启动",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBackendReady) 
                            MaterialTheme.colorScheme.onTertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange
            ) {
                OutlinedTextField(
                    value = selectedModel?.name ?: "请选择模型",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        model.name,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        if (model.runOnCpu) "CPU" else "NPU",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = { onModelSelected(model) },
                            leadingIcon = {
                                if (model.id == selectedModel?.id) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipSelectionSection(
    clips: List<ClipInfo>,
    selectedClip: ClipInfo?,
    onClipSelected: (ClipInfo) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "选择视频片段",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (clips.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无可用的视频片段",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(clips) { clip ->
                        ClipCard(
                            clip = clip,
                            isSelected = clip.id == selectedClip?.id,
                            onClick = { onClipSelected(clip) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipCard(
    clip: ClipInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else 
            null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = clip.id,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "${clip.elements.size} 个元素",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ElementConfigSection(
    elements: List<ElementInfo>,
    elementConfigs: List<ElementGenConfig>,
    availableResolutions: List<Resolution>,
    onConfigChange: (Int, ElementGenConfig) -> Unit,
    context: android.content.Context,
    currentGeneratingIndex: Int,
    progress: Float
) {
    val pagerState = rememberPagerState(pageCount = { elements.size })
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "元素配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Text(
                    text = "${pagerState.currentPage + 1} / ${elements.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // 横向滑动的元素配置页
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp),
                pageSpacing = 16.dp
            ) { page ->
                ElementConfigCard(
                    element = elements[page],
                    config = elementConfigs.getOrNull(page) ?: ElementGenConfig(elements[page].id),
                    availableResolutions = availableResolutions,
                    index = page,
                    context = context,
                    isGenerating = page == currentGeneratingIndex,
                    progress = if (page == currentGeneratingIndex) progress else 0f,
                    onConfigChange = { newConfig -> onConfigChange(page, newConfig) }
                )
            }
            
            // 底部页面指示点
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                elements.forEachIndexed { index, _ ->
                    val config = elementConfigs.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    config?.isGenerated == true -> MaterialTheme.colorScheme.tertiary
                                    index == pagerState.currentPage -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ElementConfigCard(
    element: ElementInfo,
    config: ElementGenConfig,
    availableResolutions: List<Resolution>,
    index: Int,
    context: android.content.Context,
    isGenerating: Boolean,
    progress: Float,
    onConfigChange: (ElementGenConfig) -> Unit
) {
    var textureBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var resolutionExpanded by remember { mutableStateOf(false) }
    
    // 加载纹理预览
    LaunchedEffect(element.texturePath) {
        try {
            val inputStream = context.assets.open(element.texturePath)
            textureBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
        } catch (e: Exception) {
            Log.e("RenderPrepScreen", "Error loading texture: ${element.texturePath}", e)
        }
    }
    
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 元素标题和状态
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "元素 ${index + 1}: ${element.id}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                
                if (config.isGenerated) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "已生成",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 纹理预览区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (config.generatedBitmap != null) {
                    Image(
                        bitmap = config.generatedBitmap!!.asImageBitmap(),
                        contentDescription = "生成的图片",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else if (textureBitmap != null) {
                    Image(
                        bitmap = textureBitmap!!.asImageBitmap(),
                        contentDescription = "原始纹理",
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
                
                // 生成中的遮罩
                if (isGenerating) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            
            // 尺寸选择
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "推荐: ${element.recommendedWidth}×${element.recommendedHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                // 分辨率下拉选择
                ExposedDropdownMenuBox(
                    expanded = resolutionExpanded,
                    onExpandedChange = { resolutionExpanded = it }
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.menuAnchor()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "生成: ${config.selectedWidth}×${config.selectedHeight}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = resolutionExpanded)
                        }
                    }
                    
                    ExposedDropdownMenu(
                        expanded = resolutionExpanded,
                        onDismissRequest = { resolutionExpanded = false }
                    ) {
                        availableResolutions.forEach { res ->
                            DropdownMenuItem(
                                text = { Text("${res.width}×${res.height}") },
                                onClick = {
                                    onConfigChange(config.copy(
                                        selectedWidth = res.width,
                                        selectedHeight = res.height
                                    ))
                                    resolutionExpanded = false
                                },
                                leadingIcon = {
                                    if (res.width == config.selectedWidth && res.height == config.selectedHeight) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Prompt 输入
            OutlinedTextField(
                value = config.prompt,
                onValueChange = { onConfigChange(config.copy(prompt = it)) },
                label = { Text(stringResource(R.string.image_prompt)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Negative Prompt 输入
            OutlinedTextField(
                value = config.negativePrompt,
                onValueChange = { onConfigChange(config.copy(negativePrompt = it)) },
                label = { Text(stringResource(R.string.negative_prompt)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Steps 滑块
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.steps, config.steps.roundToInt()),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = config.steps,
                    onValueChange = { onConfigChange(config.copy(steps = it)) },
                    valueRange = 1f..50f,
                    steps = 48,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // CFG 滑块
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.cfg_scale, config.cfg),
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = config.cfg,
                    onValueChange = { onConfigChange(config.copy(cfg = it)) },
                    valueRange = 1f..20f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
