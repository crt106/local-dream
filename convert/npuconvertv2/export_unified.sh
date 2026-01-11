#!/bin/bash
set -e

# ============================================================================
# Unified NPU Model Export Script
# ============================================================================
# This script supports:
# 1. Full export from scratch
# 2. Resume from interruption (auto-detect completed resolutions)
# 3. Export single resolution (use --resolution flag)
# ============================================================================

# ==================== Configuration ====================
clip_skip=2
model_path=/mnt/d/sd1.5_models/cyberrealistic_v90.safetensors
model_name=CyberrealisticV90
realistic=true

# Define extra resolutions (width height)
# NOTE: Both width and height MUST be multiples of 8 (preferably 64 for best NPU performance)
extra_resolutions=(
    "176 704"   # ~1:4 ratio (close to 180x700, but 8-aligned)
    "320 176"   # ~16:9 ratio (close to 320x180, but 8-aligned)
    "512 768"   # 2:3 ratio
    "768 512"   # 3:2 ratio
)

# SOC versions to build for
soc_versions=("8gen2")
extra_resolution_soc_versions=("8gen2")

# ==================== Parse Arguments ====================
SINGLE_RESOLUTION=""
FORCE_REBUILD=false
SKIP_BASE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --resolution)
            SINGLE_RESOLUTION="$2"
            shift 2
            ;;
        --force)
            FORCE_REBUILD=true
            shift
            ;;
        --skip-base)
            SKIP_BASE=true
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --resolution WxH    Export only a single resolution (e.g., --resolution 176x704)"
            echo "  --force             Force rebuild even if output exists"
            echo "  --skip-base         Skip base 512x512 model (assumes it exists)"
            echo "  --help              Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                           # Full export (auto-resume if interrupted)"
            echo "  $0 --resolution 176x704      # Export only 176x704 patch"
            echo "  $0 --skip-base               # Export only extra resolutions"
            echo "  $0 --force                   # Force rebuild everything"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# ==================== Environment Setup ====================
export UV_LINK_MODE=copy

echo "=========================================="
echo "NPU Model Export Script"
echo "=========================================="
echo "Model: $model_name"
echo "Path: $model_path"
echo "Realistic mode: $realistic"
echo "SOC versions: ${soc_versions[*]}"
echo "=========================================="

# Create/activate main virtual environment
if [ ! -f .venv/bin/activate ]; then
    echo "Creating main virtual environment..."
    uv venv -p 3.10.17
fi
echo "Activating main virtual environment..."
source .venv/bin/activate
echo "Syncing dependencies..."
uv sync --active

# Set realistic flag
realistic_flag=""
if [ "$realistic" = true ]; then
    realistic_flag="--realistic"
fi

# Setup CUDA environment if needed
prepare_py=(python)
prepare_args=()
if [ "${PREPARE_DEVICE:-}" = "cuda" ]; then
    echo ""
    echo "CUDA mode enabled, checking .venv_cuda..."
    
    cuda_env_ready=false
    if [ -f .venv_cuda/bin/activate ]; then
        if .venv_cuda/bin/python -c "import torch; assert torch.cuda.is_available()" 2>/dev/null; then
            cuda_env_ready=true
            echo "✓ CUDA environment is ready"
        else
            echo "⚠ CUDA environment exists but PyTorch CUDA is not properly installed"
            echo "  Reinstalling CUDA dependencies..."
        fi
    else
        echo "CUDA environment not found, creating..."
    fi
    
    if [ "$cuda_env_ready" = false ]; then
        if [ ! -d .venv_cuda ]; then
            echo "Creating CUDA virtual environment..."
            uv venv -p 3.10.17 .venv_cuda
        fi
        
        source .venv_cuda/bin/activate
        echo "Syncing CUDA environment dependencies..."
        uv sync --active
        echo "Installing CUDA version of PyTorch..."
        uv pip install --index-url "${TORCH_CUDA_INDEX_URL:-https://download.pytorch.org/whl/cu121}" "torch==2.5.1+${TORCH_CUDA_BUILD:-cu121}"
        deactivate
        source .venv/bin/activate
        echo "✓ CUDA environment setup complete"
    fi
    
    prepare_py=(./.venv_cuda/bin/python)
    prepare_args=(--device cuda --dtype fp16)
    echo "Will use CUDA for data preparation"
else
    echo "Using CPU mode for data preparation"
fi

# ==================== Helper Functions ====================

# Check if a resolution has been successfully processed
check_resolution_complete() {
    local size=$1
    local soc=$2
    
    # Check if output directory exists
    if [ ! -d "output_${size}" ]; then
        return 1
    fi
    
    # Check if unet.bin exists in the output
    if [ ! -f "output_${size}/qnn_models_${soc}/unet.bin" ]; then
        return 1
    fi
    
    # Check if patch file exists (for non-512x512 resolutions)
    if [ "$size" != "512" ]; then
        if [ ! -f "output_512/qnn_models_${soc}/${size}.patch" ]; then
            return 1
        fi
    fi
    
    return 0
}

# Process base 512x512 resolution
process_base_resolution() {
    echo ""
    echo "=========================================="
    echo "Processing base resolution: 512x512"
    echo "=========================================="
    
    if [ "$FORCE_REBUILD" = false ] && check_resolution_complete "512" "${soc_versions[0]}"; then
        echo "✓ Base 512x512 already exists, skipping..."
        return 0
    fi
    
    if [ -d "output_512" ] && [ "$FORCE_REBUILD" = true ]; then
        echo "Force rebuild enabled, removing existing output_512..."
        rm -rf output_512
    fi
    
    echo "Step 1/3: Preparing data..."
    "${prepare_py[@]}" prepare_data.py --model_path $model_path --clip_skip $clip_skip $realistic_flag "${prepare_args[@]}"
    
    echo "Step 2/3: Generating quantization data..."
    python gen_quant_data.py
    
    echo "Step 3/3: Exporting ONNX and converting to QNN..."
    python export_onnx.py --model_path $model_path --clip_skip $clip_skip
    
    for soc in "${soc_versions[@]}"; do
        bash scripts/convert_all.sh --min_soc $soc
    done
    
    if [ -d "output" ]; then
        if [ -d "output_512" ]; then
            rm -rf output_512
        fi
        mv output output_512
        echo "✓ Base 512x512 completed"
    else
        echo "✗ Error: output directory not found"
        exit 1
    fi
}

# Process a single extra resolution
process_extra_resolution() {
    local width=$1
    local height=$2
    local size="${width}x${height}"
    
    echo ""
    echo "=========================================="
    echo "Processing resolution: ${size}"
    echo "=========================================="
    
    # Check if already completed
    if [ "$FORCE_REBUILD" = false ] && check_resolution_complete "$size" "${extra_resolution_soc_versions[0]}"; then
        echo "✓ Resolution ${size} already completed, skipping..."
        return 0
    fi
    
    # Validate resolution (must be multiple of 8)
    if [ $((width % 8)) -ne 0 ] || [ $((height % 8)) -ne 0 ]; then
        echo "⚠ WARNING: Resolution ${size} is not a multiple of 8!"
        echo "  This may cause 'Incompatible dimension of arrays' errors."
        echo "  Recommended: Use multiples of 8 (preferably 64) for both width and height."
        read -p "Continue anyway? (y/N) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Skipping ${size}..."
            return 0
        fi
    fi
    
    # Remove existing output if force rebuild
    if [ -d "output_${size}" ] && [ "$FORCE_REBUILD" = true ]; then
        echo "Force rebuild enabled, removing existing output_${size}..."
        rm -rf "output_${size}"
    fi
    
    echo "Step 1/4: Preparing data..."
    "${prepare_py[@]}" prepare_data.py --model_path $model_path --clip_skip $clip_skip --height $height --width $width $realistic_flag "${prepare_args[@]}"
    
    echo "Step 2/4: Generating quantization data..."
    python gen_quant_data.py
    
    echo "Step 3/4: Exporting ONNX (UNet only)..."
    python export_onnx_unet_only.py --model_path $model_path --clip_skip $clip_skip --height $height --width $width
    
    echo "Step 4/4: Converting to QNN..."
    for soc in "${extra_resolution_soc_versions[@]}"; do
        bash scripts/convert_all_unet_only.sh --min_soc $soc
    done
    
    # Move output directory
    if [ -d "output" ]; then
        if [ -d "output_${size}" ]; then
            rm -rf "output_${size}"
        fi
        mv output "output_${size}"
    else
        echo "✗ Error: output directory not found"
        exit 1
    fi
    
    # Generate patch files
    echo "Generating patch file..."
    for soc in "${extra_resolution_soc_versions[@]}"; do
        if [ ! -f "output_512/qnn_models_${soc}/unet.bin" ]; then
            echo "✗ Error: Base unet.bin not found at output_512/qnn_models_${soc}/unet.bin"
            echo "  Please run base 512x512 export first."
            exit 1
        fi
        
        echo "Creating patch: output_512/qnn_models_${soc}/${size}.patch"
        zstd --patch-from ./output_512/qnn_models_${soc}/unet.bin \
             output_${size}/qnn_models_${soc}/unet.bin \
             -o ./output_512/qnn_models_${soc}/${size}.patch
        
        if [ -f "./output_512/qnn_models_${soc}/${size}.patch" ]; then
            patch_size=$(du -h "./output_512/qnn_models_${soc}/${size}.patch" | cut -f1)
            echo "✓ Patch created: ${patch_size}"
        else
            echo "✗ Error: Patch file creation failed"
            exit 1
        fi
    done
    
    echo "✓ Resolution ${size} completed"
}

# ==================== Main Execution ====================

# Handle single resolution mode
if [ -n "$SINGLE_RESOLUTION" ]; then
    echo "Single resolution mode: $SINGLE_RESOLUTION"
    
    # Parse resolution
    if [[ $SINGLE_RESOLUTION =~ ^([0-9]+)x([0-9]+)$ ]]; then
        width=${BASH_REMATCH[1]}
        height=${BASH_REMATCH[2]}
        
        # Check if base model exists (required for patch generation)
        if [ ! -d "output_512/qnn_models_${soc_versions[0]}" ]; then
            echo "✗ Error: Base 512x512 model not found"
            echo "  Please run full export first, or use --skip-base if you want to build base model"
            exit 1
        fi
        
        process_extra_resolution $width $height
        
        echo ""
        echo "=========================================="
        echo "Single resolution export completed!"
        echo "=========================================="
        echo "Patch file: output_512/qnn_models_${soc_versions[0]}/${width}x${height}.patch"
        echo ""
        echo "To deploy, re-package the model:"
        echo "  zip -r ${model_name}_qnn2.28_${soc_versions[0]}.zip output_512/qnn_models_${soc_versions[0]}"
        
        exit 0
    else
        echo "✗ Error: Invalid resolution format: $SINGLE_RESOLUTION"
        echo "  Expected format: WIDTHxHEIGHT (e.g., 176x704)"
        exit 1
    fi
fi

# ==================== Full Export Mode ====================

# Process base 512x512 resolution
if [ "$SKIP_BASE" = false ]; then
    process_base_resolution
else
    echo "Skipping base 512x512 (--skip-base flag set)"
    if [ ! -d "output_512" ]; then
        echo "✗ Error: output_512 not found but --skip-base is set"
        exit 1
    fi
fi

# Process extra resolutions
echo ""
echo "=========================================="
echo "Processing extra resolutions..."
echo "=========================================="

for resolution in "${extra_resolutions[@]}"; do
    read -r width height <<< "$resolution"
    process_extra_resolution $width $height
done

# ==================== Package Outputs ====================
echo ""
echo "=========================================="
echo "Packaging outputs..."
echo "=========================================="

for soc in "${soc_versions[@]}"; do
    output_file="${model_name}_qnn2.28_${soc}.zip"
    
    if [ -f "$output_file" ]; then
        echo "Removing existing $output_file..."
        rm "$output_file"
    fi
    
    echo "Creating $output_file..."
    zip -r $output_file output_512/qnn_models_${soc}
    
    if [ -f "$output_file" ]; then
        file_size=$(du -h "$output_file" | cut -f1)
        echo "✓ Package created: $output_file ($file_size)"
    fi
done

# ==================== Summary ====================
echo ""
echo "=========================================="
echo "Export completed successfully!"
echo "=========================================="
echo "Base model: output_512/"
echo "Extra resolutions:"
for resolution in "${extra_resolutions[@]}"; do
    read -r width height <<< "$resolution"
    size="${width}x${height}"
    if check_resolution_complete "$size" "${soc_versions[0]}"; then
        echo "  ✓ ${size}"
    else
        echo "  ✗ ${size} (failed or skipped)"
    fi
done
echo ""
echo "Package files:"
for soc in "${soc_versions[@]}"; do
    output_file="${model_name}_qnn2.28_${soc}.zip"
    if [ -f "$output_file" ]; then
        echo "  ✓ $output_file"
    fi
done
echo "=========================================="
