"""
批量绘制追踪区域脚本
读取 tracking_data.json 中的frame位置数据，与scripts/frames下的图片一一对应绘制
"""

from PIL import Image, ImageDraw
from pathlib import Path
import json
import os

# 获取项目根目录
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent

# 默认文件路径
TRACKING_DATA_FILE = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "tracking_data.json"
FRAMES_DIR = SCRIPT_DIR / "frames"
OUTPUT_DIR = SCRIPT_DIR / "tracking_output"


def load_tracking_data(file_path):
    """加载 tracking_data.json 文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    return data


def draw_tracking_on_frame(image_path, corners, output_path):
    """
    在图片上绘制追踪区域
    
    corners格式: [{"x": ..., "y": ...}, ...] (共4个点: 左上, 右上, 左下, 右下)
    """
    # 加载图片
    img = Image.open(image_path)
    img_width, img_height = img.size
    
    # 判断是否为 SBS 格式（图片宽度大于720）
    source_width = 720
    source_height = 1280
    is_sbs = img_width > source_width
    
    if is_sbs:
        video_width = img_width // 2
        scale_x = video_width / source_width
        scale_y = img_height / source_height
    else:
        scale_x = img_width / source_width
        scale_y = img_height / source_height
    
    # 解析角点 (corners顺序: 左上, 右上, 左下, 右下)
    if len(corners) != 4:
        print(f"  警告: corners数量不是4个，跳过")
        return False
    
    tl = (corners[0]["x"], corners[0]["y"])  # 左上
    tr = (corners[1]["x"], corners[1]["y"])  # 右上
    bl = (corners[2]["x"], corners[2]["y"])  # 左下
    br = (corners[3]["x"], corners[3]["y"])  # 右下
    
    # 转换为实际图片坐标
    def to_img_coords(point):
        return (point[0] * scale_x, point[1] * scale_y)
    
    tl_img = to_img_coords(tl)
    tr_img = to_img_coords(tr)
    bl_img = to_img_coords(bl)
    br_img = to_img_coords(br)
    
    # 定义多边形顶点 (顺时针: 左上 -> 右上 -> 右下 -> 左下)
    polygon = [tl_img, tr_img, br_img, bl_img]
    
    # 创建可绘制的图片副本
    draw_img = img.convert('RGBA')
    
    # 绘制填充的半透明区域
    overlay = Image.new('RGBA', draw_img.size, (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.polygon(polygon, fill=(0, 255, 0, 80))  # 绿色半透明填充
    
    # 合并图层
    draw_img = Image.alpha_composite(draw_img, overlay)
    draw = ImageDraw.Draw(draw_img)
    
    # 绘制边框线（粗线）
    line_color = (255, 0, 0)  # 红色线条
    line_width = 3
    draw.line([tl_img, tr_img], fill=line_color, width=line_width)
    draw.line([tr_img, br_img], fill=line_color, width=line_width)
    draw.line([br_img, bl_img], fill=line_color, width=line_width)
    draw.line([bl_img, tl_img], fill=line_color, width=line_width)
    
    # 绘制角点标记
    point_radius = 8
    point_colors = {
        'TL': (255, 0, 0),    # 左上 - 红
        'TR': (0, 255, 0),    # 右上 - 绿
        'BL': (0, 0, 255),    # 左下 - 蓝
        'BR': (255, 255, 0)   # 右下 - 黄
    }
    
    for label, point in [('TL', tl_img), ('TR', tr_img), ('BL', bl_img), ('BR', br_img)]:
        x, y = point
        color = point_colors[label]
        draw.ellipse([x - point_radius, y - point_radius, 
                     x + point_radius, y + point_radius], 
                    fill=color, outline=(255, 255, 255), width=2)
        # 添加标签
        draw.text((x + 12, y - 8), label, fill=(255, 255, 255))
    
    # 保存结果
    draw_img = draw_img.convert('RGB')
    draw_img.save(output_path)
    return True


def main():
    print("=" * 60)
    print("批量追踪区域可视化脚本")
    print("=" * 60)
    
    # 加载追踪数据
    print(f"\n正在加载追踪数据: {TRACKING_DATA_FILE}")
    tracking_data = load_tracking_data(TRACKING_DATA_FILE)
    frames_data = tracking_data.get("frames", [])
    print(f"共 {len(frames_data)} 帧追踪数据")
    
    # 检查frames目录
    if not FRAMES_DIR.exists():
        print(f"错误: frames目录不存在: {FRAMES_DIR}")
        return
    
    # 创建输出目录
    OUTPUT_DIR.mkdir(exist_ok=True)
    print(f"输出目录: {OUTPUT_DIR}")
    
    # 获取所有帧图片
    frame_files = sorted(FRAMES_DIR.glob("frame_*.png"))
    print(f"共找到 {len(frame_files)} 张帧图片")
    
    # 批量处理
    success_count = 0
    skip_count = 0
    
    print("\n开始处理...")
    for frame_info in frames_data:
        frame_idx = frame_info["frame"]
        corners = frame_info["corners"]
        
        # 构建帧图片文件名 (frame_idx是0-based, 文件名是1-based)
        frame_filename = f"frame_{frame_idx + 1:04d}.png"
        frame_path = FRAMES_DIR / frame_filename
        
        if not frame_path.exists():
            print(f"  帧 {frame_idx}: 图片不存在 ({frame_filename}), 跳过")
            skip_count += 1
            continue
        
        # 输出文件名
        output_filename = f"tracking_{frame_idx:04d}.png"
        output_path = OUTPUT_DIR / output_filename
        
        # 绘制
        if draw_tracking_on_frame(frame_path, corners, output_path):
            success_count += 1
            if success_count % 10 == 0 or frame_idx < 5:
                print(f"  帧 {frame_idx}: ✓ 已保存 -> {output_filename}")
    
    print(f"\n" + "=" * 60)
    print(f"处理完成!")
    print(f"  成功: {success_count} 张")
    print(f"  跳过: {skip_count} 张")
    print(f"  输出目录: {OUTPUT_DIR}")
    print("=" * 60)


if __name__ == "__main__":
    main()
