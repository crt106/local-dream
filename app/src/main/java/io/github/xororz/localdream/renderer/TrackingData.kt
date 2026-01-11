package io.github.xororz.localdream.renderer

data class TrackingConfig(
    val id: String,
    val videoPath: String,
    val elements: List<TrackingElement>
)

data class TrackingElement(
    val id: String,
    val texturePath: String,
    val recommendedWidth: Int,
    val recommendedHeight: Int,
    val maskColor: IntArray, // [R, G, B]
    val frames: List<FrameData>
)

data class FrameData(
    val frame: Int,
    val corners: List<Point>
)

data class Point(
    val x: Float,
    val y: Float
)
