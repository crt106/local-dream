set -e

# Use SUFFIX from parent script, default to empty if not set
SUFFIX=${SUFFIX:-""}

# Skip conversion if libmodel.so already exists
if [ ! -f "./qnn_unet/x86_64-linux-clang/libmodel.so" ]; then
    qnn-onnx-converter -n --input_network ./unet/model.onnx \
        --preserve_io layout \
        --input_list ./input_list_unet.txt \
        --use_per_channel_quantization \
        --bias_bitwidth 32 \
        --act_bitwidth 16

    qnn-model-lib-generator -c ./unet/model.cpp -b ./unet/model.bin -t x86_64-linux-clang -o ./qnn_unet
fi

qnn-context-binary-generator --model ./qnn_unet/x86_64-linux-clang/libmodel.so --backend ${QNN_SDK_ROOT}/lib/x86_64-linux-clang/libQnnHtp.so --output_dir "./output/qnn_models$SUFFIX" --binary_file unet --config_file "./htp_backend$SUFFIX.json"