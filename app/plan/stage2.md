第二阶段：Android 渲染管线搭建 (Viewer Demo)
目标：在手机上播放视频，并证明 UV 映射逻辑是正确的（此时尚不需要 AI，仅用一张静态图片代替）。

步骤 2.1：ExoPlayer 基础播放
操作：创建一个 Android 项目，引入 androidx.media3。

配置：使用 ExoPlayer 播放 output_sbs_10bit_audio.mp4。


硬件解码检查：确保 Logcat 显示解码器调用了骁龙的硬件解码器（通常带 qcom 字样），确认支持 Main10 Profile 。

步骤 2.2：OpenGL ES 3.2 渲染环境
容器：使用 GLSurfaceView。

纹理流：

创建一个 SurfaceTexture。

将 ExoPlayer 的 Surface 设置为这个 SurfaceTexture。

在 GL 中绑定 GL_TEXTURE_EXTERNAL_OES 纹理 。

步骤 2.3：编写 UV 映射 Shader (GLSL)
操作：编写 Fragment Shader 实现纹理替换逻辑。

输入：

u_VideoTexture: SBS 视频纹理。

u_TestTexture: 一张静态测试图片（如一张 Logo 或彩虹图）。


逻辑 (伪代码) ：

OpenGL Shading Language

// 1. 获取当前像素的纹理坐标 (v_TexCoord)
// 2. 计算右半边(UV区)的采样坐标
vec2 uvMapCoord = vec2(v_TexCoord.x * 0.5 + 0.5, v_TexCoord.y);
// 3. 读取 UV 数据 (注意 10-bit 视频读出来是归一化的 0.0-1.0)
vec4 uvData = texture(u_VideoTexture, uvMapCoord);
// 4. 使用读取到的 RG 值作为坐标，去采样静态测试图
// 注意：视频编码通常将 U 放在 R/Y 通道，V 放在 G/U 通道，需根据 FFmpeg 映射调整
vec2 targetUV = vec2(uvData.r, uvData.g);
vec4 replaceColor = texture(u_TestTexture, targetUV);
// 5. 最终输出
FragColor = replaceColor; // 先只显示替换后的结果看对不对
验证标准：视频播放时，你的测试图片应该完美地“粘”在视频里的物体上，随其运动。如果出现马赛克，检查是否成功开启了 10-bit 解码。