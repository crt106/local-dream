## 调研结论
- 你们工程目前 prompt/negative_prompt 的“手动输入点”在 Android Compose：ModelRunScreen 与 RenderPrepScreen；随后通过 Intent extras（"prompt"/"negative_prompt"）进入 BackgroundGenerationService，再以 JSON POST 到本地 8081 后端 `/generate`，C++ 侧解析后进入 `processPromptPair(prompt, negative_prompt, 77)` 做 CFG 两路文本编码（neg/pos）并继续 SD 推理链路。[ModelRunScreen.kt](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/ui/screens/ModelRunScreen.kt#L1441-L1593) / [BackgroundGenerationService.kt](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/service/BackgroundGenerationService.kt#L94-L227) / [main.cpp](file:///d:/ProgramSource2/local-dream/app/src/main/cpp/src/main.cpp#L1390-L1424)
- 项目已集成 QNN SDK SampleApp 并能动态加载 `libQnnHtp.so` 与模型二进制（当前用于 SD 各子图）。因此走“直接用 AI Hub 已适配的 QNN/QAIRT 产物”是可衔接的，但 LLM 更推荐走 Genie 这条官方 LLM 运行时路线，而不是复用你们现有的 SD `QnnModel` 封装。
- Qualcomm 的 LLM on-device 教程路线是：用 Qualcomm AI Hub/AI Hub Models 产出 QAIRT context binaries（*.bin），再用 QAIRT SDK 的 Genie 在 Android/Windows 设备端运行；部分模型可直接拿到已编译资产，跳过导出步骤。（https://github.com/quic/ai-hub-apps/tree/main/tutorials/llm_on_genie）
- AI Hub Models repo 的 Qwen2.5-1.5B-Instruct 提供“面向 Qualcomm 设备的 on-device 优化导出脚本”，并指向“LLM on-device deployment tutorial”作为部署方式。（https://github.com/quic/ai-hub-models/blob/main/qai_hub_models/models/qwen2_5_1_5b_instruct/README.md）
- AI Hub 侧的 QAIRT/AI Engine Direct 支持三类执行资产：model libraries（.so/.dll）、context binaries（.bin）、DLC；context binaries 与具体 HTP 目标强相关，旧设备编译的 context 在新芯片上通常可用但可能性能欠佳。（https://app.aihub.qualcomm.com/docs/hub/faq.html）
- ai-hub-models 的 release 说明提到“开源 LLM 预编译 QNN context binaries（面向 Snapdragon 8 Elite）”以及 Qwen2.5 1.5B 的导出 bug 修复/可用性更新，这意味着你有机会直接拿到现成资产或至少走一键导出+编译流程。（https://github.com/quic/ai-hub-models/releases）

## 是否“1.5B 够用”
- 目标任务是“自然语言诉求 → SD1.5 正/负提示词（短文本、结构化输出）”，相比通用对话/长文写作，这类任务更依赖指令遵循、格式化输出与少量 SD 领域先验。Qwen2.5-1.5B-Instruct 属于多语言指令模型，做这种 JSON/模板化生成通常足够。
- 为了稳定性，关键在于：
  - 强约束输出格式（JSON schema）
  - 少样例 few-shot（覆盖写实/二次元/构图/风格/摄影参数等）
  - 后处理规则（去重、截断、敏感词/坏词过滤、强制负面基础词）
- 如果后续想把“风格词库/LoRA 触发词/摄影参数”等做得更“懂 SD”，模型越大越容易，但 1.5B 作为第一版端侧 promptify 很合理；不够再上 3B。

## 核心路线（改为 AI Hub QNN 包直用）
### 方案 A（推荐）：Genie 跑 AI Hub 的 LLM context binaries
- 资产：通过 ai-hub-models 的 Qwen2.5-1.5B 导出/下载得到 `genie_bundle`（通常包含：多个 ctx-bins *.bin、genie_config.json、htp_backend_ext_config、tokenizer.json 等；具体以官方教程/导出脚本输出为准）。（https://github.com/quic/ai-hub-apps/tree/main/tutorials/llm_on_genie）
- 运行时：在 Android NDK 侧集成 QAIRT SDK（含 Genie），并将必须的 QAIRT/QNN/HTP 依赖 so 打包进 APK；按设备 Hexagon 架构设置 ADSP_LIBRARY_PATH 等（Android 环境变量路径可在 App 内以等价方式处理/或由 QAIRT 初始化逻辑管理）。（https://github.com/quic/ai-hub-apps/tree/main/tutorials/llm_on_genie）
- 推理：在本地 8081 后端新增一个“promptify”接口（如 `/promptify`），输入自然语言，输出 `{positive_prompt, negative_prompt}`；Android 侧在点击生成前先调该接口填充 prompt/negative_prompt，再复用现有 `/generate`。

### 方案 B（备选）：直接 QNN C API 执行 LLM（不走 Genie）
- 工作量明显更大：要自己实现 tokenizer、prefill/decoding 循环、KV cache 管理、采样策略、多 ctx-bins/多图切换等；虽然你们已有 SampleApp+QnnModel，但它是 SD 子图定制，不是 LLM 通用引擎。

## 拟实施计划（不改动代码前提下的确认稿）
1) 资产获取策略
- 优先检查 ai-hub-models 是否已提供 Qwen2.5-1.5B 的“预编译 context binaries/Genie bundle”（通常在 Releases 或模型导出脚本支持 `--fetch-static-assets`）。若可直接下载，则以“版本锁定（QAIRT/QNN SDK 版本）+ SHA 校验”纳入工程。
- 若无现成包：按官方教程在宿主机（Windows 可用 x64 Python 或 WSL）运行 qwen2.5-1.5B 的 export，指定目标设备为 Snapdragon 8 Elite，产出 genie_bundle。（https://github.com/quic/ai-hub-apps/tree/main/tutorials/llm_on_genie）

2) 工程侧集成 Genie（Android）
- 在 app/src/main/cpp 增加 Genie 依赖链接与运行时库打包逻辑（参照你们现有在 [CMakeLists.txt](file:///d:/ProgramSource2/local-dream/app/src/main/cpp/CMakeLists.txt#L7-L63) 对 QNN so 的复制方式，但改为 QAIRT SDK 推荐的文件集合与版本）。
- 在 C++ 后端增加 `PromptifyEngine`（单例），启动时加载 genie_bundle（ctx-bins + 配置 + tokenizer）；支持流式/非流式输出。

3) Promptify 协议与提示词策略
- 设计 system prompt：要求输出严格 JSON：
  - `positive_prompt`: 逗号分隔英文关键词，长度上限 N
  - `negative_prompt`: 默认负面基底 + 根据用户意图补充
  - 可选字段：`style`, `aspect_ratio`, `seed_hint`（若你们后端未来要接）
- Few-shot：内置 10~20 条典型用例（写实/二次元/建筑/产品/人像/夜景/赛博等），保障稳定。
- 后处理：
  - 清洗换行/引号/多余标点
  - 去重、裁剪 token 过长
  - 强制加入负面基底（如 lowres, bad anatomy, blurry 等，按你们模型偏好可配置）

4) App 侧接入（最小侵入）
- UI：新增“自然语言描述”输入框与“自动生成提示词”按钮；按钮触发 `/promptify`，成功后自动填充现有 prompt/negative_prompt 文本框（不破坏老流程）。涉及文件：[ModelRunScreen.kt](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/ui/screens/ModelRunScreen.kt) / [RenderPrepScreen.kt](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/ui/screens/RenderPrepScreen.kt)
- Service：BackgroundGenerationService 在启动生成前可选调用一次 promptify（开关控制），并将返回值写回现有 JSON 字段，继续调用 `/generate`。涉及文件：[BackgroundGenerationService.kt](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/service/BackgroundGenerationService.kt)

5) 验证与回滚
- 功能验证：
  - 固定 20 条中文/英文自然语言用例，检查 JSON 解析率、prompt 长度、SD 出图是否明显符合意图。
- 性能验证：
  - 统计 TTFT 与 TPS（若 Genie 提供接口/日志），以及“从点击到开始扩散”的额外延迟。
- 回滚策略：保留手动 prompt 输入原逻辑；promptify 失败时自动降级为原 prompt 字段。

## 风险与对策
- QAIRT/QNN 版本不一致：你们当前 SD 转换脚本产物名含 `qnn2.28`，而 AI Hub/Genie 资产可能依赖更新的 QAIRT/QNN 版本；对策是以 AI Hub 产物所用版本为准，工程侧按同版本打包运行时库。（https://github.com/quic/ai-hub-apps/tree/main/tutorials/llm_on_genie）
- Context binary 不兼容/1008：多与 HTP 库/ADSP_LIBRARY_PATH/架构不匹配相关；对策是按 QAIRT 指引打包与路径设置，并做启动自检与错误提示。（https://app.aihub.qualcomm.com/docs/hub/faq.html）
- 产物体积：LLM ctx-bins + tokenizer 体积可能较大；对策是做“模型包下载/外置存储”与“按需加载”。

如果你确认这条方案，我下一步会：先把“Qwen2.5-1.5B 是否能直接拿到 8 Elite 的预编译 genie_bundle/ctx-bins、对应 QAIRT 版本与文件清单”进一步落到具体下载路径与目录结构，然后给出工程侧的最小改动点清单与接口定义。