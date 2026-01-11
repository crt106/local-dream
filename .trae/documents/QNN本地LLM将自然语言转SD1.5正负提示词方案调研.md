## 现状梳理（已定位到代码点）
- 目前 prompt/negative_prompt 由用户在 Android UI 手动输入：
  - [ModelRunScreen.kt:L1441-L1499](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/ui/screens/ModelRunScreen.kt#L1441-L1499)
  - [RenderPrepScreen.kt:L1336-L1359](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/ui/screens/RenderPrepScreen.kt#L1336-L1359)
- 输入通过 Intent extras（prompt/negative_prompt）→ 前台服务 → localhost:8081 `/generate` JSON 传给 C++ 后端：
  - [BackgroundGenerationService.kt:L94-L171](file:///d:/ProgramSource2/local-dream/app/src/main/java/io/github/xororz/localdream/service/BackgroundGenerationService.kt#L94-L171)
  - [main.cpp:L2390-L2403](file:///d:/ProgramSource2/local-dream/app/src/main/cpp/src/main.cpp#L2390-L2403)
- 后端使用 `processPromptPair(prompt, negative_prompt, 77)` 生成 CFG 的 cond/uncond 文本输入（意味着最终要控制在 77 token 上限）：
  - [main.cpp:L836-L860](file:///d:/ProgramSource2/local-dream/app/src/main/cpp/src/main.cpp#L836-L860)

## 可行性结论（本地 LLM → SD1.5 提示词）
- 任务本质是“需求理解 + 英文化/标签化 + 结构化输出（正/负提示词）+ 长度压缩到 77 token”。这类“提示词重写/抽取”对模型参数规模要求不高，核心在于：输出约束、模板、后处理。
- 在 Snapdragon 8 Elite 上，用 QNN/HTP 运行本地小模型是可行路径；工程里也已经包含两类 QNN 路径：
  - 通过 MNN 的 QNN backend（可给 MNN-LLM 选 backend=`npu`）：[llm.cpp:L45-L61](file:///d:/ProgramSource2/local-dream/app/src/main/cpp/3rdparty/MNN/transformers/llm/engine/src/llm.cpp#L45-L61)
  - 直接集成 QNN SDK SampleApp（现有封装偏 SD 子图执行，非通用 LLM wrapper）：[QnnModel.hpp](file:///d:/ProgramSource2/local-dream/app/src/main/cpp/src/QnnModel.hpp)

## “Qwen2.5-1.5B-Instruct 够不够用？”判断
- 够用的前提：
  - 用强约束的 system prompt + JSON schema 输出（例如固定字段：positive/negative/style/seed建议等），并做严格后处理（截断、去重、禁词、77 token 压缩）。
  - 目标不是“文学创作”，而是“可控的标签化描述与负面缺陷列表”。1.5B 在中文理解、英文化、实体抽取与风格映射上通常足以胜任。
- 风险点（小模型更容易踩）：
  - 复杂构图（多主体关系、相机语言、材质细节）→ 可能漏要素或表达不稳定。
  - 输出长度/格式不稳定（不按 JSON/夹带解释）→ 需要更强的约束与重试策略。
  - SD1.5 的 77 token 上限导致“语义压缩”很关键，小模型压缩时更容易丢关键信息。
- 推荐落地策略：
  - 先以 1.5B 做 MVP；同时保留 0.5B/3B 的 A/B 口子：
    - 0.5B：更省内存更快，但更依赖模板/词表，复杂请求质量风险更大。
    - 3B：更稳但更占内存/延迟。

## 运行时路线对比（建议优先级）
- 路线 A（优先）：MNN-LLM 推理 + 选择 MNN 的 QNN backend（如果算子覆盖足够）
  - 优点：工程内已有 MNN-LLM 引擎与配置体系，易集成；可做 CPU 回退。
  - 风险：MNN 的 QNN backend 对 transformer 算子/动态 shape/KV cache 的覆盖需要实测。
- 路线 B：直接使用 Snapdragon AI Hub 已适配的 QNN 模型（如果有 Qwen2.5-1.5B 的 QNN 发布包）
  - 优点：适配工作少、性能更确定。
  - 风险：模型格式/接口/授权与打包方式需要对齐你们现有 SampleApp 管线；KV cache/多子图管理复杂。
- 路线 C（备选）：LLM 用 MNN CPU（或 GPU），扩散继续走 QNN
  - 现实工程上往往足够：prompt 生成只发生一次，哪怕 500ms~2s 也可接受，同时避免 NPU 争用。

## 输出协议与质量策略（保证 SD1.5 可用）
- 输出协议：LLM 仅输出 JSON（严格解析失败则重试一次），例如：
  - `{"positive":"...","negative":"...","notes":"..."}`
- 后处理（必须做）：
  - 统一语言：positive/negative 强制英文 tag（SD1.5 更吃英文）。
  - 词表与风格映射：把“国风/赛博/写真/胶片”等映射到稳定 tag 组合。
  - 77 token 压缩：按权重优先级保留关键信息（主体/动作/场景/镜头/光照/画质），其余删除；负向提示词附带默认缺陷列表并去重。
  - 安全与合规过滤：按产品策略剔除敏感内容（不在 native 里记录原文）。

## 工程接入点（两种落地方式）
- 方式 1（推荐）：在 App 端新增“自然语言诉求”输入框与“自动生成提示词”按钮
  - 生成后把 prompt/negative_prompt 回填到现有输入框，用户仍可微调。
  - 只改 UI/Service，不改后端扩散逻辑。
- 方式 2：在 C++ 8081 后端增加 `/nl2prompt` 或在 `/generate` 内部支持 `nl_request` 字段
  - 后端负责把自然语言转 prompt，再走现有 `processPromptPair()`。

## 需要补充验证的关键点（落地前必须实测）
- QNN 侧：
  - MNN-LLM 是否能在当前 Android 构建里实际走到 QNN（需要确认 MNN_QNN 宏与 QNN so 打包）。
  - 目标 SoC（8 Elite）下 LLM 子图是否有算子缺失、是否会掉回 CPU。
- 体验侧：
  - 端上延迟与峰值内存；1.5B 4bit 权重体积与 KV cache 需要压测。
  - 77 token 压缩策略对成图质量的影响。

## 下一步实施计划（你确认后我再开始改代码/跑验证）
1. 在工程里新增“NL→Prompt”模块（先走 MNN-LLM CPU 路线，保证功能闭环；同时预留 backend=`npu` 开关）。
2. 引入一个小模型（优先 Qwen2.5-1.5B-Instruct 的 MNN 量化产物）并打包到 Android assets，接入 JNI 推理（参考仓库内 MnnLlmChat 的 JNI 组织方式）。
3. 在 UI 增加自然语言输入与一键生成，生成结果回填现有 prompt/negative_prompt；服务端接口保持不变。
4. 实装严格 JSON 输出 + 77 token 压缩 + 默认 negative 列表与去重。
5. 做一套离线样例集（中英混合请求、复杂构图、风格词）对比：手写 prompt vs 自动生成 prompt 的图质量与稳定性。
6. 在确认功能稳定后，再把 backend 切到 `npu` 做 QNN 路线性能与兼容性验证；若 QNN 覆盖不完整则回退 CPU 作为默认。

如果你确认该方案，我会按上述步骤直接开始：先把 NL→Prompt 功能跑通，再做 QNN 加速与性能压测。