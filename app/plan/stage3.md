第三阶段：端侧 AI 模型接入 (AI Engine)
目标：让骁龙 8 Elite 的 NPU 跑起来，生成一张图。

步骤 3.1：获取优化模型

来源：访问 Qualcomm AI Hub 。

下载：下载 Stable Diffusion 1.5 的量化版本。

格式选择：.tflite (LiteRT) 或 .qnn (QNN 原生)。


精度推荐：优先选择 W8A16 (权重8位，激活16位)，在速度和画质间平衡 。

步骤 3.2：集成 LiteRT (TensorFlow Lite) 与 QNN Delegate

依赖：在 build.gradle 中引入 qnn-runtime 和 qnn-litert-delegate 。

初始化：

Java

QnnDelegate.Options options = new QnnDelegate.Options();
options.setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND); // 强制使用 HTP NPU [cite: 88]
Interpreter interpreter = new Interpreter(modelFile, options);
步骤 3.3：实现 Zero-Copy (零拷贝) 管道
关键技术：AHardwareBuffer。


流程 ：

使用 C++ (NDK) 调用 AHardwareBuffer_allocate 创建一块内存，Usage 标记需包含 GPU_SAMPLED_IMAGE。

将这块 Buffer 传给 AI 解释器作为 Output Tensor 的目标地址。

同时，使用 eglCreateImageKHR 将这块 Buffer 包装成 OpenGL 纹理 (u_AITexture)。

测试：运行一次推理，直接在 Phase 2 的 Shader 中渲染这个 u_AITexture，不要经过 CPU 的 Bitmap 转换。