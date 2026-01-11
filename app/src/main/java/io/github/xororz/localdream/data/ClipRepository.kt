package io.github.xororz.localdream.data

import android.content.Context
import org.json.JSONObject

/**
 * Clip 信息数据类
 */
data class ClipInfo(
    val id: String,
    val videoPath: String,
    val elements: List<ElementInfo>
)

/**
 * 元素信息数据类
 */
data class ElementInfo(
    val id: String,
    val texturePath: String,
    val recommendedWidth: Int,
    val recommendedHeight: Int,
    val maskColor: IntArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ElementInfo
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Clip 数据仓库，负责扫描和解析 assets/clips 目录下的 clip 配置
 */
class ClipRepository(private val context: Context) {
    
    /**
     * 获取所有可用的 clips
     */
    fun getAvailableClips(): List<ClipInfo> {
        val clips = mutableListOf<ClipInfo>()
        try {
            val clipDirs = context.assets.list("clips") ?: return emptyList()
            for (clipDir in clipDirs) {
                val clipInfo = loadClipInfo(clipDir)
                if (clipInfo != null) {
                    clips.add(clipInfo)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ClipRepository", "Error loading clips", e)
        }
        return clips
    }

    /**
     * 加载单个 clip 的配置信息
     */
    private fun loadClipInfo(clipId: String): ClipInfo? {
        return try {
            val configPath = "clips/$clipId/config.json"
            val jsonString = context.assets.open(configPath).bufferedReader().use { it.readText() }
            parseClipConfig(clipId, jsonString)
        } catch (e: Exception) {
            android.util.Log.e("ClipRepository", "Error loading clip: $clipId", e)
            null
        }
    }

    /**
     * 解析 clip 配置 JSON
     */
    private fun parseClipConfig(clipId: String, jsonString: String): ClipInfo {
        val jsonObj = JSONObject(jsonString)
        val id = jsonObj.getString("id")
        val videoPath = "clips/$clipId/${jsonObj.getString("videoPath")}"
        
        val elementsArray = jsonObj.getJSONArray("elements")
        val elements = mutableListOf<ElementInfo>()
        
        for (i in 0 until elementsArray.length()) {
            val elemObj = elementsArray.getJSONObject(i)
            val elemId = elemObj.getString("id")
            val texturePath = "clips/$clipId/${elemObj.getString("texturePath")}"
            val recommendedWidth = elemObj.optInt("recommendedWidth", 512)
            val recommendedHeight = elemObj.optInt("recommendedHeight", 512)
            
            val colorArray = elemObj.getJSONArray("maskColor")
            val maskColor = intArrayOf(
                colorArray.getInt(0),
                colorArray.getInt(1),
                colorArray.getInt(2)
            )
            
            elements.add(
                ElementInfo(
                    id = elemId,
                    texturePath = texturePath,
                    recommendedWidth = recommendedWidth,
                    recommendedHeight = recommendedHeight,
                    maskColor = maskColor
                )
            )
        }
        
        return ClipInfo(
            id = id,
            videoPath = videoPath,
            elements = elements
        )
    }
}
