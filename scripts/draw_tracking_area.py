"""
绘制追踪区域验证脚本
读取 AE 追踪数据并在首帧图片上绘制四边形区域
"""

from PIL import Image, ImageDraw, ImageFont
from pathlib import Path
import argparse
import os

# 获取项目根目录
SCRIPT_DIR = Path(__file__).parent
PROJECT_ROOT = SCRIPT_DIR.parent

# 默认文件路径（相对于项目根目录）
DEFAULT_TRACKING_FILE = PROJECT_ROOT / "app" / "src" / "main" / "assets" / "广告牌1.txt"
DEFAULT_FIRST_FRAME_IMAGE = PROJECT_ROOT / "app" / "src" / "main" / "res" / "drawable" / "output_sbs_2_first_frame.png"
DEFAULT_OUTPUT_IMAGE = SCRIPT_DIR / "tracking_area_visualization.png"

def parse_ae_tracking_data(file_path):
    """解析 After Effects 追踪数据文件"""
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.strip().split('\n')
    
    # 解析元数据
    metadata = {}
    for line in lines[:8]:
        if '\t' in line:
            parts = line.strip().split('\t')
            if len(parts) >= 2:
                key = parts[0].strip()
                value = parts[1].strip()
                metadata[key] = value
    
    source_width = int(metadata.get('Source Width', 720))
    source_height = int(metadata.get('Source Height', 1280))
    
    # 解析 Corner Pin 数据
    corner_pins = {1: [], 2: [], 3: [], 4: []}
    current_pin = None
    reading_data = False
    
    for i, line in enumerate(lines):
        # 检测 Corner Pin 部分开始
        if 'ADBE Corner Pin-0001' in line:
            current_pin = 1
            reading_data = False
        elif 'ADBE Corner Pin-0002' in line:
            current_pin = 2
            reading_data = False
        elif 'ADBE Corner Pin-0003' in line:
            current_pin = 3
            reading_data = False
        elif 'ADBE Corner Pin-0004' in line:
            current_pin = 4
            reading_data = False
        elif current_pin and 'Frame' in line and 'X pixels' in line:
            # 跳过表头行
            reading_data = True
            continue
        elif current_pin and reading_data and line.strip():
            parts = line.split('\t')
            # 过滤掉空字符串
            parts = [p for p in parts if p.strip()]
            if len(parts) >= 3:
                try:
                    frame = int(parts[0])
                    x = float(parts[1])
                    y = float(parts[2])
                    corner_pins[current_pin].append((frame, x, y))
                except ValueError:
                    pass
        elif current_pin and not line.strip():
            # 空行表示当前部分结束
            if reading_data:
                reading_data = False
    
    return metadata, corner_pins, source_width, source_height

def get_frame_corners(corner_pins, frame_index=0):
    """获取指定帧的四个角点"""
    corners = {}
    for pin_id, data in corner_pins.items():
        for frame, x, y in data:
            if frame == frame_index:
                corners[pin_id] = (x, y)
                break
    return corners

def main(tracking_file=None, image_file=None, output_file=None):
    # 使用提供的路径或默认路径
    tracking_file = Path(tracking_file) if tracking_file else DEFAULT_TRACKING_FILE
    image_file = Path(image_file) if image_file else DEFAULT_FIRST_FRAME_IMAGE
    output_file = Path(output_file) if output_file else DEFAULT_OUTPUT_IMAGE
    
    print("正在解析追踪数据...")
    metadata, corner_pins, source_width, source_height = parse_ae_tracking_data(tracking_file)
    
    print(f"源视频尺寸: {source_width} x {source_height}")
    print(f"Corner Pin 数据:")
    for pin_id, data in corner_pins.items():
        if data:
            print(f"  Pin {pin_id}: {len(data)} 帧数据")
    
    # 获取第0帧的角点
    frame0_corners = get_frame_corners(corner_pins, 0)
    print(f"\n第0帧角点坐标:")
    pin_names = {1: "左上", 2: "右上", 3: "左下", 4: "右下"}
    for pin_id, (x, y) in sorted(frame0_corners.items()):
        print(f"  {pin_names[pin_id]}: ({x:.2f}, {y:.2f})")
    
    # 加载图片
    print(f"\n正在加载图片: {image_file}")
    img = Image.open(image_file)
    img_width, img_height = img.size
    print(f"图片尺寸: {img_width} x {img_height}")
    
    # 判断是否为 SBS 格式（图片宽度是源视频宽度的两倍）
    # 从图片看，左半部分是实际视频，右半部分是遮罩
    is_sbs = img_width > source_width
    
    if is_sbs:
        print("检测到 SBS (并排) 格式")
        # 只在左半边绘制
        video_width = img_width // 2
        scale_x = video_width / source_width
        scale_y = img_height / source_height
    else:
        scale_x = img_width / source_width
        scale_y = img_height / source_height
    
    print(f"缩放比例: X={scale_x:.4f}, Y={scale_y:.4f}")
    
    # 创建可绘制的图片副本
    draw_img = img.copy()
    draw = ImageDraw.Draw(draw_img)
    
    # 转换坐标并绘制
    # Corner Pin 顺序: 1=左上, 2=右上, 3=左下, 4=右下
    # 绘制顺序: 左上 -> 右上 -> 右下 -> 左下 -> 左上
    if all(pin_id in frame0_corners for pin_id in [1, 2, 3, 4]):
        tl = frame0_corners[1]  # 左上
        tr = frame0_corners[2]  # 右上
        bl = frame0_corners[3]  # 左下
        br = frame0_corners[4]  # 右下
        
        # 转换为实际图片坐标
        def to_img_coords(point):
            return (point[0] * scale_x, point[1] * scale_y)
        
        tl_img = to_img_coords(tl)
        tr_img = to_img_coords(tr)
        bl_img = to_img_coords(bl)
        br_img = to_img_coords(br)
        
        print(f"\n图片坐标系中的角点:")
        print(f"  左上: ({tl_img[0]:.2f}, {tl_img[1]:.2f})")
        print(f"  右上: ({tr_img[0]:.2f}, {tr_img[1]:.2f})")
        print(f"  左下: ({bl_img[0]:.2f}, {bl_img[1]:.2f})")
        print(f"  右下: ({br_img[0]:.2f}, {br_img[1]:.2f})")
        
        # 定义多边形顶点 (顺时针: 左上 -> 右上 -> 右下 -> 左下)
        polygon = [tl_img, tr_img, br_img, bl_img]
        
        # 绘制填充的半透明区域
        overlay = Image.new('RGBA', draw_img.size, (0, 0, 0, 0))
        overlay_draw = ImageDraw.Draw(overlay)
        overlay_draw.polygon(polygon, fill=(0, 255, 0, 80))  # 绿色半透明填充
        
        # 合并图层
        draw_img = draw_img.convert('RGBA')
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
        
        # 添加图例
        legend_x = 10
        legend_y = 10
        legend_text = [
            "追踪区域可视化",
            f"源尺寸: {source_width}x{source_height}",
            f"图片尺寸: {img_width}x{img_height}",
            "",
            "角点颜色:",
            "  TL(左上) - 红",
            "  TR(右上) - 绿", 
            "  BL(左下) - 蓝",
            "  BR(右下) - 黄",
        ]
        
        # 绘制图例背景
        bg_height = len(legend_text) * 16 + 10
        draw.rectangle([legend_x - 5, legend_y - 5, legend_x + 180, legend_y + bg_height], 
                      fill=(0, 0, 0, 180))
        
        for i, text in enumerate(legend_text):
            draw.text((legend_x, legend_y + i * 16), text, fill=(255, 255, 255))
    
    # 保存结果
    draw_img = draw_img.convert('RGB')
    draw_img.save(output_file)
    print(f"\n✓ 可视化结果已保存到: {output_file}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="绘制追踪区域验证脚本")
    parser.add_argument("-t", "--tracking", help="追踪数据文件路径")
    parser.add_argument("-i", "--image", help="首帧图片路径")
    parser.add_argument("-o", "--output", help="输出图片路径")
    args = parser.parse_args()
    
    main(args.tracking, args.image, args.output)
