package io.github.xororz.localdream.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.TimeUnit

@Composable
fun VideoProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // 当不在拖动时，同步播放器的位置
    LaunchedEffect(currentPosition, isDragging) {
        if (!isDragging && duration > 0) {
            sliderPosition = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0x40000000))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 当前时间
        Text(
            text = formatTime(if (isDragging) (sliderPosition * duration).toLong() else currentPosition),
            color = Color.White,
            fontSize = 12.sp
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 进度条
        Slider(
            value = sliderPosition,
            onValueChange = { 
                isDragging = true
                sliderPosition = it
            },
            onValueChangeFinished = {
                isDragging = false
                val seekPosition = (sliderPosition * duration).toLong()
                onSeek(seekPosition)
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color(0x80FFFFFF)
            )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // 总时长
        Text(
            text = formatTime(duration),
            color = Color.White,
            fontSize = 12.sp
        )
    }
}

private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
