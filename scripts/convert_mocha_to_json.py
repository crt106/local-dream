#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Mocha Pro 导出数据转换脚本

将 Mocha Pro 导出的 After Effects Corner Pin 格式（.txt）转换为项目所需的 JSON 格式。

使用方法:
    python convert_mocha_to_json.py <输入文件.txt> <输出文件.json> [选项]

示例:
    python convert_mocha_to_json.py 广告牌1.txt tracking_data.json --id billboard_1 --video output_sbs_2.mp4
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


def parse_mocha_export(file_path: str) -> Tuple[Dict[str, any], Dict[int, Dict[int, Tuple[float, float]]]]:
    """
    解析 Mocha Pro 导出的 After Effects Corner Pin 格式文件。
    
    Args:
        file_path: 输入文件路径
        
    Returns:
        元组 (metadata, corner_data)
        - metadata: 包含 Units Per Second, Source Width, Source Height 等信息
        - corner_data: {corner_index: {frame: (x, y)}} 格式的角点数据
    """
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    lines = content.split('\n')
    
    # 解析元数据
    metadata = {}
    for line in lines[:10]:
        line = line.strip()
        if 'Units Per Second' in line:
            metadata['fps'] = int(line.split('\t')[-1])
        elif 'Source Width' in line:
            metadata['width'] = int(line.split('\t')[-1])
        elif 'Source Height' in line:
            metadata['height'] = int(line.split('\t')[-1])
    
    # 解析角点数据
    # Corner Pin 编号: 0001=左上, 0002=右上, 0003=左下, 0004=右下
    corner_data: Dict[int, Dict[int, Tuple[float, float]]] = {
        1: {},  # 左上
        2: {},  # 右上
        3: {},  # 左下
        4: {},  # 右下
    }
    
    current_corner = None
    reading_data = False
    
    for line in lines:
        line = line.strip()
        
        # 检测 Corner Pin 块的开始
        corner_match = re.search(r'ADBE Corner Pin-000(\d)', line)
        if corner_match:
            current_corner = int(corner_match.group(1))
            reading_data = False
            continue
        
        # 检测数据行的开始（Frame X pixels Y pixels）
        if current_corner and 'Frame' in line and 'X pixels' in line:
            reading_data = True
            continue
        
        # 检测块的结束
        if line.startswith('Effects') or line.startswith('End of Keyframe Data') or line == '':
            if line.startswith('Effects') and 'ADBE Corner Pin' not in line:
                current_corner = None
                reading_data = False
            elif line == '':
                # 空行可能表示块结束
                pass
            continue
        
        # 读取数据行
        if reading_data and current_corner:
            parts = line.split('\t')
            # 过滤空字符串
            parts = [p for p in parts if p.strip()]
            if len(parts) >= 3:
                try:
                    frame = int(parts[0])
                    x = float(parts[1])
                    y = float(parts[2])
                    corner_data[current_corner][frame] = (x, y)
                except (ValueError, IndexError):
                    continue
    
    return metadata, corner_data


def convert_to_json_format(
    corner_data: Dict[int, Dict[int, Tuple[float, float]]],
    element_id: str = "billboard_1",
    video_path: str = "output_sbs_2.mp4",
    mask_color: List[int] = None
) -> Dict:
    """
    将解析后的角点数据转换为目标 JSON 格式。
    
    Args:
        corner_data: {corner_index: {frame: (x, y)}} 格式的角点数据
        element_id: 元素 ID
        video_path: 关联的视频文件路径
        mask_color: 遮罩颜色 [R, G, B]
        
    Returns:
        符合目标格式的字典
    """
    if mask_color is None:
        mask_color = [255, 0, 0]
    
    # 获取所有帧号
    all_frames = set()
    for corner_frames in corner_data.values():
        all_frames.update(corner_frames.keys())
    
    frames_list = []
    for frame_num in sorted(all_frames):
        # 检查该帧是否所有角点都有数据
        if all(frame_num in corner_data[i] for i in range(1, 5)):
            corners = []
            # 角点顺序: 左上(1), 右上(2), 左下(3), 右下(4)
            for corner_idx in [1, 2, 3, 4]:
                x, y = corner_data[corner_idx][frame_num]
                corners.append({
                    "x": x,
                    "y": y
                })
            
            frames_list.append({
                "frame": frame_num,
                "corners": corners
            })
    
    return {
        "id": element_id,
        "videoPath": video_path,
        "maskColor": mask_color,
        "frames": frames_list
    }


def main():
    parser = argparse.ArgumentParser(
        description='将 Mocha Pro 导出的 After Effects Corner Pin 格式转换为 JSON 格式',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog='''
示例:
    python convert_mocha_to_json.py 广告牌1.txt tracking_data.json
    python convert_mocha_to_json.py 广告牌1.txt tracking_data.json --id billboard_1 --video output.mp4
    python convert_mocha_to_json.py 广告牌1.txt tracking_data.json --mask-color 255 0 0
        '''
    )
    
    parser.add_argument('input', help='输入文件路径（Mocha Pro 导出的 .txt 文件）')
    parser.add_argument('output', help='输出文件路径（JSON 格式）')
    parser.add_argument('--id', default='billboard_1', help='元素 ID（默认: billboard_1）')
    parser.add_argument('--video', default='output_sbs_2.mp4', help='关联的视频文件路径（默认: output_sbs_2.mp4）')
    parser.add_argument('--mask-color', nargs=3, type=int, default=[255, 0, 0],
                        metavar=('R', 'G', 'B'), help='遮罩颜色 RGB 值（默认: 255 0 0）')
    parser.add_argument('--pretty', action='store_true', help='格式化输出 JSON（默认开启）')
    parser.add_argument('--compact', action='store_true', help='紧凑输出 JSON')
    
    args = parser.parse_args()
    
    # 检查输入文件
    input_path = Path(args.input)
    if not input_path.exists():
        print(f"错误: 输入文件不存在: {args.input}", file=sys.stderr)
        sys.exit(1)
    
    print(f"正在解析 Mocha Pro 导出文件: {args.input}")
    
    try:
        metadata, corner_data = parse_mocha_export(args.input)
    except Exception as e:
        print(f"错误: 解析文件失败: {e}", file=sys.stderr)
        sys.exit(1)
    
    # 显示解析结果统计
    frame_count = len(corner_data[1]) if corner_data[1] else 0
    print(f"解析完成:")
    print(f"  - 源视频尺寸: {metadata.get('width', 'N/A')}x{metadata.get('height', 'N/A')}")
    print(f"  - 帧率: {metadata.get('fps', 'N/A')} fps")
    print(f"  - 帧数: {frame_count}")
    for i in range(1, 5):
        corner_names = {1: '左上', 2: '右上', 3: '左下', 4: '右下'}
        print(f"  - Corner {i} ({corner_names[i]}): {len(corner_data[i])} 帧")
    
    # 转换为 JSON 格式
    print(f"\n正在转换为 JSON 格式...")
    result = convert_to_json_format(
        corner_data,
        element_id=args.id,
        video_path=args.video,
        mask_color=args.mask_color
    )
    
    # 写入输出文件
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    
    indent = None if args.compact else 2
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(result, f, indent=indent, ensure_ascii=False)
    
    print(f"转换完成: {args.output}")
    print(f"  - 输出帧数: {len(result['frames'])}")
    
    # 显示第一帧数据作为预览
    if result['frames']:
        first_frame = result['frames'][0]
        print(f"\n第一帧预览 (frame {first_frame['frame']}):")
        corner_names = ['左上', '右上', '左下', '右下']
        for i, corner in enumerate(first_frame['corners']):
            print(f"  {corner_names[i]}: ({corner['x']:.3f}, {corner['y']:.3f})")


if __name__ == '__main__':
    main()
