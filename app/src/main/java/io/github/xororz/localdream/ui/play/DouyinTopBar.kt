package io.github.xororz.localdream.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DouyinTopBar(
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(4) } // 默认选中"推荐"
    val tabs = listOf("北京", "经验", "关注", "商城", "推荐")
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 12.dp), // Figma 顶部有一些 padding，这里稍微增加 vertical padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧菜单图标
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "菜单",
            tint = Color(0xFFFFFFFF), // Figma: #ffffff
            modifier = Modifier
                .size(24.dp)
                .clickable { /* 无实际作用 */ }
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 中间 Tab 栏
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { selectedTab = index }
                ) {
                    Text(
                        text = tab,
                        color = if (selectedTab == index) Color(0xFFFFFFFF) else Color(0x80FFFFFF), // Figma: #ffffff vs #ffffff80
                        fontSize = 16.sp, // Figma: 16px
                        fontWeight = FontWeight.Medium, // Figma: 500
                        letterSpacing = 0.sp
                    )
                    if (selectedTab == index) {
                        Spacer(modifier = Modifier.height(6.dp)) // 增加间距
                        Box(
                            modifier = Modifier
                                .width(30.dp) // Figma 看起来下划线比较宽
                                .height(2.dp)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 右侧搜索图标
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "搜索",
            tint = Color(0xFFFFFFFF),
            modifier = Modifier
                .size(24.dp)
                .clickable { /* 无实际作用 */ }
        )
    }
}
