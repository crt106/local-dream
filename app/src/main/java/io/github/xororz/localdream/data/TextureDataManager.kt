package io.github.xororz.localdream.data

import android.graphics.Bitmap
import android.util.Log

/**
 * 管理 NPU 生成的纹理数据的单例类。
 * 用于在 RenderPrepScreen 和 PlayActivity 之间传递生成的纹理 Bitmap，
 * 避免通过文件 I/O 中转。
 */
object TextureDataManager {
    private const val TAG = "TextureDataManager"
    
    // 存储生成的纹理，key 为 elementId
    private val generatedTextures = mutableMapOf<String, Bitmap>()
    
    /**
     * 存储指定元素的生成纹理
     * @param elementId 元素 ID
     * @param bitmap 生成的纹理 Bitmap
     */
    fun setTexture(elementId: String, bitmap: Bitmap) {
        Log.d(TAG, "setTexture: elementId=$elementId, size=${bitmap.width}x${bitmap.height}")
        // 如果已有旧的 bitmap，先回收
        generatedTextures[elementId]?.let { oldBitmap ->
            if (!oldBitmap.isRecycled && oldBitmap != bitmap) {
                // 不主动回收，让 GC 处理，避免在使用中被回收
                Log.d(TAG, "Replacing existing texture for $elementId")
            }
        }
        generatedTextures[elementId] = bitmap
    }
    
    /**
     * 获取指定元素的生成纹理
     * @param elementId 元素 ID
     * @return 纹理 Bitmap，如果不存在则返回 null
     */
    fun getTexture(elementId: String): Bitmap? {
        val bitmap = generatedTextures[elementId]
        Log.d(TAG, "getTexture: elementId=$elementId, found=${bitmap != null}")
        return bitmap?.takeIf { !it.isRecycled }
    }
    
    /**
     * 获取所有生成的纹理
     * @return 元素 ID 到 Bitmap 的映射
     */
    fun getAllTextures(): Map<String, Bitmap> {
        return generatedTextures.filterValues { !it.isRecycled }
    }
    
    /**
     * 检查是否有指定元素的纹理
     */
    fun hasTexture(elementId: String): Boolean {
        return generatedTextures[elementId]?.let { !it.isRecycled } == true
    }
    
    /**
     * 清除所有存储的纹理引用
     * 注意：不主动回收 Bitmap，避免在 UI 仍在使用时被回收导致崩溃
     * Bitmap 的回收由 GC 自动处理
     */
    fun clearAll() {
        Log.d(TAG, "clearAll: clearing ${generatedTextures.size} texture references")
        generatedTextures.clear()
    }
    
    /**
     * 清除指定元素的纹理引用
     */
    fun clearTexture(elementId: String) {
        generatedTextures.remove(elementId)
        Log.d(TAG, "clearTexture: removed reference for $elementId")
    }
    
    /**
     * 获取当前存储的纹理数量
     */
    fun getTextureCount(): Int = generatedTextures.count { !it.value.isRecycled }
}
