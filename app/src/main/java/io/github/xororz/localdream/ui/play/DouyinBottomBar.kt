package io.github.xororz.localdream.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DouyinBottomBar(
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 默认选中"首页"
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF161616)) // Figma: #161616
            .padding(vertical = 6.dp), // Reduced vertical padding
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 首页
        BottomNavItem(
            label = "首页",
            isSelected = selectedTab == 0,
            onClick = { selectedTab = 0 }
        )
        
        // 朋友
        BottomNavItem(
            label = "朋友",
            isSelected = selectedTab == 1,
            onClick = { selectedTab = 1 }
        )
        
        // 加号按钮（高度还原样式）
        Box(
            modifier = Modifier
                .width(45.dp)
                .height(28.dp)
                .clickable { /* 无实际作用 */ },
            contentAlignment = Alignment.Center
        ) {
            // 左边青色层
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(12.dp)
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF00F5FF))
            )
            // 右边红色层
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(12.dp)
                    .align(Alignment.CenterEnd)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFE2C55))
            )
            // 中间黑色主体
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White) // 这里在抖音里是黑色背景带白色加号，但外部有颜色，我们用白色主体来模拟
                    .padding(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "发布",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // 消息
        BottomNavItem(
            label = "消息",
            isSelected = selectedTab == 3,
            onClick = { selectedTab = 3 }
        )
        
        // 我
        BottomNavItem(
            label = "我",
            isSelected = selectedTab == 4,
            onClick = { selectedTab = 4 }
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color(0xFFFFFFFF) else Color(0x80FFFFFF), // Figma: #ffffff vs #ffffff80
            fontSize = 16.sp, // Figma: 16px (duxTabBars .label/.label2) - Note: Figma code shows bottom bar labels are also 16px
            fontWeight = FontWeight.Medium, // Figma: 500
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
