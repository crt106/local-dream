set -e

# Use SUFFIX from parent script, default to empty if not set
SUFFIX=${SUFFIX:-""}

mnnconvert -f ONNX --modelFile clip/model.onnx --MNNModel "output/qnn_models$SUFFIX/clip_v2.mnn" --fp16
cp clip/pos_emb.bin "output/qnn_models$SUFFIX/pos_emb.bin"
cp clip/token_emb.bin "output/qnn_models$SUFFIX/token_emb.bin"