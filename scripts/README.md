# Scripts 目录

本目录包含用于开发和调试的 Python 脚本。

## 环境设置

项目使用 **uv** 管理 Python 依赖和虚拟环境。

### 安装 uv

如果尚未安装 uv：

```bash
# Windows (PowerShell)
powershell -ExecutionPolicy ByPass -c "irm https://astral.sh/uv/install.ps1 | iex"

# 或使用 pip
pip install uv
```

### 同步依赖

```bash
# 在项目根目录运行，自动创建 .venv 并安装所有依赖
uv sync
```

### 添加新依赖

```bash
# 添加新包
uv add <package_name>

# 添加开发依赖
uv add --dev <package_name>
```

## 运行脚本

### 方法 1: 使用 uv run（推荐）

```bash
# uv 会自动使用正确的虚拟环境
uv run python scripts/draw_tracking_area.py
```

### 方法 2: 激活虚拟环境

```powershell
# Windows PowerShell
.\.venv\Scripts\Activate.ps1

# 然后运行脚本
python scripts/draw_tracking_area.py
```

## 可用脚本

### draw_tracking_area.py

**功能**: 可视化 After Effects 导出的追踪数据，在视频首帧上绘制追踪区域。

**用法**:
```bash
# 使用默认参数运行
uv run python scripts/draw_tracking_area.py

# 指定参数运行
uv run python scripts/draw_tracking_area.py \
    -t "path/to/tracking_data.txt" \
    -i "path/to/first_frame.png" \
    -o "path/to/output.png"
```

**参数**:
- `-t, --tracking`: 追踪数据文件路径（AE 导出的 txt 文件）
- `-i, --image`: 首帧图片路径
- `-o, --output`: 输出图片路径

**输出**:
- 在指定图片上绘制追踪四边形区域
- 用不同颜色标记四个角点（TL=红, TR=绿, BL=蓝, BR=黄）
- 显示图例信息

### convert_mocha_to_json.py

**功能**: 将 Mocha Pro 导出的 After Effects Corner Pin 格式（.txt）转换为项目所需的 JSON 格式。

**用法**:
```bash
# 基本用法
uv run python scripts/convert_mocha_to_json.py 广告牌1.txt tracking_data.json

# 指定参数运行（单行，PowerShell 兼容）
uv run python scripts/convert_mocha_to_json.py app/src/main/assets/广告牌1.txt app/src/main/assets/tracking_data.json --id billboard_1 --video output_sbs_2.mp4 --mask-color 255 0 0
```

**参数**:
- `input`: 输入文件路径（Mocha Pro 导出的 .txt 文件）
- `output`: 输出文件路径（JSON 格式）
- `--id`: 元素 ID（默认: billboard_1）
- `--video`: 关联的视频文件路径（默认: output_sbs_2.mp4）
- `--mask-color R G B`: 遮罩颜色 RGB 值（默认: 255 0 0）
- `--compact`: 紧凑输出 JSON（默认格式化输出）

**输入格式** (Mocha Pro 导出的 After Effects Corner Pin):
```
Adobe After Effects 6.0 Keyframe Data
    Units Per Second    30
    Source Width    720
    Source Height   1280
    ...
Effects    ADBE Corner Pin #1    ADBE Corner Pin-0001
    Frame    X pixels    Y pixels
    0    386.829    -3.7859
    1    386.934    -3.67776
    ...
```

**输出格式** (JSON):
```json
{
  "id": "billboard_1",
  "videoPath": "output_sbs_2.mp4",
  "maskColor": [255, 0, 0],
  "frames": [
    {
      "frame": 0,
      "corners": [
        {"x": 386.829, "y": -3.7859},
        {"x": 539.952, "y": -4.09976},
        {"x": 402.968, "y": 677.231},
        {"x": 559.195, "y": 673.463}
      ]
    },
    ...
  ]
}
```
