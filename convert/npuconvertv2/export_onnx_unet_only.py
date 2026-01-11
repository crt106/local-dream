import torch
from redefined_modules.diffusers.models.unet_2d_condition import UNet2DConditionModel
from redefined_modules.diffusers.models.attention import CrossAttention
import onnx
import pathlib
import shutil
from diffusers import StableDiffusionPipeline
from transformers import CLIPTextModel
import argparse
import os


def delete_folders(folders):
    """Delete the specified folders if they exist."""
    for folder in folders:
        if os.path.exists(folder):
            print(f"Removing {folder}")
            shutil.rmtree(folder)


# Delete cache at the beginning of the script
folders_to_delete = ["qnn_unet", "qnn_vae_encoder", "qnn_vae_decoder"]
delete_folders(folders_to_delete)


def replace_mha_with_sha_blocks(unet_model):
    for name, module in unet_model.named_modules():
        if isinstance(module, CrossAttention):
            module.replace_linear_to_convs()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_path", type=str, required=True)
    parser.add_argument("--width", type=int, default=512)
    parser.add_argument("--height", type=int, default=512)
    parser.add_argument("--clip_skip", type=int, default=1)
    return parser.parse_args()


args = parse_args()
width = args.width
height = args.height
clip_skip = args.clip_skip
model_path = args.model_path

for export_path in ["clip", "unet", "vae_decoder", "vae_encoder"]:
    pathlib.Path(export_path).mkdir(parents=True, exist_ok=True)

if model_path.endswith("safetensors"):
    pipe = StableDiffusionPipeline.from_single_file(
        model_path,
        attn_processor_type="default",
    )
    pipe.save_pretrained("./model")
    model_path = "./model"
else:
    pipe = StableDiffusionPipeline.from_pretrained(model_path)

unet = UNet2DConditionModel.from_pretrained(
    model_path,
    subfolder="unet",
    revision="main",
)
unet.config.return_dict = False

replace_mha_with_sha_blocks(unet)
unet.eval()

with torch.no_grad():
    torch.onnx.export(
        unet,
        (
            torch.randn(1, 4, height // 8, width // 8),
            torch.tensor([0], dtype=torch.long),
            torch.randn(1, 77, 768),
        ),
        "unet/model.onnx",
        input_names=["sample", "timestamp", "text_embedding"],
        output_names=["output"],
    )

model = onnx.load("unet/model.onnx")
shutil.rmtree("unet")
pathlib.Path("unet").mkdir(parents=True, exist_ok=True)
onnx.save_model(
    model,
    "unet/model.onnx",
    save_as_external_data=True,
    all_tensors_to_one_file=True,
    location="weights.pb",
    size_threshold=0,
    convert_attribute=False,
)
