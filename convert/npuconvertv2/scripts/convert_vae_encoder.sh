set -e

# Use SUFFIX from parent script, default to empty if not set
SUFFIX=${SUFFIX:-""}

# Skip conversion if libmodel.so already exists
if [ ! -f "./qnn_vae_encoder/x86_64-linux-clang/libmodel.so" ]; then
    qnn-onnx-converter -n --input_network ./vae_encoder/model.onnx \
        --preserve_io layout \
        --input_list ./input_list_vae_encoder.txt \
        --use_per_channel_quantization \
        --bias_bitwidth 32 \
        --act_bitwidth 16

    qnn-model-lib-generator -c ./vae_encoder/model.cpp -b ./vae_encoder/model.bin -t x86_64-linux-clang -o ./qnn_vae_encoder
fi

qnn-context-binary-generator --model ./qnn_vae_encoder/x86_64-linux-clang/libmodel.so --backend ${QNN_SDK_ROOT}/lib/x86_64-linux-clang/libQnnHtp.so --output_dir "./output/qnn_models$SUFFIX" --binary_file vae_encoder --config_file "./htp_backend$SUFFIX.json"

