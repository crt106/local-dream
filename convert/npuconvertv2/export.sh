set -e

clip_skip=2 # 1 or 2
model_path=/mnt/d/sd1.5_models/cyberrealistic_v90.safetensors # Path to your model
model_name=CyberrealisticV90 # Name used for output files
realistic=true  # Set to true to enable --realistic mode. It will use prompts for realistic images.
# Define extra resolutions, format: (width height). Leave empty to skip extra resolutions.
extra_resolutions=(
    "180 700"
    "320 180"
    "512 768"
    "768 512"
)

# Define SOC version list
soc_versions=("8gen2")
# Non-flagship SOC versions can't run higher resolutions
extra_resolution_soc_versions=("8gen2")

if [ ! -f .venv/bin/activate ]; then
    uv venv -p 3.10.17
fi
source .venv/bin/activate
uv sync --active

prepare_py=(python)
prepare_args=()
if [ "${PREPARE_DEVICE:-}" = "cuda" ]; then
    if [ ! -f .venv_cuda/bin/activate ]; then
        uv venv -p 3.10.17 .venv_cuda
    fi
    source .venv_cuda/bin/activate
    uv sync --active
    uv pip install --index-url "${TORCH_CUDA_INDEX_URL:-https://download.pytorch.org/whl/cu121}" "torch==2.5.1+${TORCH_CUDA_BUILD:-cu121}"
    deactivate
    source .venv/bin/activate
    prepare_py=(./.venv_cuda/bin/python)
    prepare_args=(--device cuda --dtype fp16)
fi

# Set realistic flag based on realistic variable
realistic_flag=""
if [ "$realistic" = true ]; then
    realistic_flag="--realistic"
fi

# Function to process extra resolutions
process_extra_resolution() {
    local width=$1
    local height=$2
    local size="${width}x${height}"

    echo "Processing resolution: ${size}"

    # Prepare data and export
    "${prepare_py[@]}" prepare_data.py --model_path $model_path --clip_skip $clip_skip --height $height --width $width $realistic_flag "${prepare_args[@]}"
    python gen_quant_data.py
    python export_onnx_unet_only.py --model_path $model_path --clip_skip $clip_skip --height $height --width $width

    # Convert for all SOC versions
    for soc in "${extra_resolution_soc_versions[@]}"; do
        bash scripts/convert_all_unet_only.sh --min_soc $soc
    done

    # Move output directory
    mv output output_${size}

    # Generate patch files
    for soc in "${extra_resolution_soc_versions[@]}"; do
        zstd --patch-from ./output_512/qnn_models_${soc}/unet.bin \
             output_${size}/qnn_models_${soc}/unet.bin \
             -o ./output_512/qnn_models_${soc}/${size}.patch
    done
}

# ======== Base resolution 512x512 (must execute) ========
echo "Processing base resolution: 512x512"
"${prepare_py[@]}" prepare_data.py --model_path $model_path --clip_skip $clip_skip $realistic_flag "${prepare_args[@]}"
python gen_quant_data.py
python export_onnx.py --model_path $model_path --clip_skip $clip_skip

for soc in "${soc_versions[@]}"; do
    bash scripts/convert_all.sh --min_soc $soc
done

mv output output_512

# ======== Process extra resolutions ========
for resolution in "${extra_resolutions[@]}"; do
    read -r width height <<< "$resolution"
    process_extra_resolution $width $height
done

# ======== Package outputs ========
echo "Packaging output files..."
for soc in "${soc_versions[@]}"; do
    zip -r ${model_name}_qnn2.28_${soc}.zip output_512/qnn_models_${soc}
done
