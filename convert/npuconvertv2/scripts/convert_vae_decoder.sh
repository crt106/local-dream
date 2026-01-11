set -e

# Use SUFFIX from parent script, default to empty if not set
SUFFIX=${SUFFIX:-""}

# Skip conversion if libmodel.so already exists
if [ ! -f "./qnn_vae_decoder/x86_64-linux-clang/libmodel.so" ]; then
    qnn-onnx-converter -n --input_network ./vae_decoder/model.onnx \
        --preserve_io layout \
        --input_list ./input_list_vae_decoder.txt \
        --use_per_channel_quantization \
        --bias_bitwidth 32 \
        --act_bitwidth 16

    qnn-model-lib-generator -c ./vae_decoder/model.cpp -b ./vae_decoder/model.bin -t x86_64-linux-clang -o ./qnn_vae_decoder
fi

qnn-context-binary-generator --model ./qnn_vae_decoder/x86_64-linux-clang/libmodel.so --backend ${QNN_SDK_ROOT}/lib/x86_64-linux-clang/libQnnHtp.so --output_dir "./output/qnn_models$SUFFIX" --binary_file vae_decoder --config_file "./htp_backend$SUFFIX.json"