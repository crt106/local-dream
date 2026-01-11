package io.github.xororz.localdream.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow

@Composable
fun DouyinSideActions(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(end = 4.dp, bottom = 20.dp), // Figma Action area width 56px, PlayActivity used 93px margin for text, so action should be centered in the remaining space or aligned right
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp) // tt: margin-top 16px, tt2: margin-top 14px. Average to 15-16dp
    ) {
        // 头像（带白色边框和底部的红色加号）
        Box(
            modifier = Modifier.size(48.dp, 57.dp), // Figma avatar2 48x57
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp) // Figma avatar 44x44 but inside 48x57 container
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape) // Figma outline-width 2px
                    .background(Color.DarkGray)
            )
            
            // 底部的小红加号
            Box(
                modifier = Modifier
                    .size(22.dp) // Slightly larger
                    .align(Alignment.BottomCenter)
                    .offset(y = (-2).dp) // Adjust position
                    .clip(CircleShape)
                    .background(Color(0xFFFE2C55)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "关注",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        
        // 喜欢
        SideActionItem(
            icon = Icons.Default.Favorite,
            count = "4.6万",
            iconColor = Color.White,
            iconSize = 36.dp, // Figma tt: 36x36
            marginTop = 0.dp // Handled by Arrangement
        )
        
        // 评论
        SideActionItem(
            icon = Icons.Default.ModeComment, 
            count = "1009",
            iconSize = 36.dp
        )
        
        // 收藏
        SideActionItem(
            icon = Icons.Default.Star,
            count = "8.2万",
            iconSize = 36.dp
        )
        
        // 分享
        SideActionItem(
            icon = Icons.Default.Reply, 
            count = "4.6万",
            iconSize = 36.dp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 旋转的音乐唱片
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray)
            )
        }
    }
}

@Composable
private fun SideActionItem(
    icon: ImageVector,
    count: String,
    iconColor: Color = Color.White,
    iconSize: androidx.compose.ui.unit.Dp = 36.dp,
    marginTop: androidx.compose.ui.unit.Dp = 0.dp,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(top = marginTop)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = count,
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )
        // Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = count,
            color = Color.White,
            fontSize = 13.sp, // Figma: 13px
            fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(
                shadow = Shadow(
                    color = Color(0x33000000), // Figma: #00000033
                    offset = Offset(0f, 1f),
                    blurRadius = 4f
                )
            )
        )
    }
}
