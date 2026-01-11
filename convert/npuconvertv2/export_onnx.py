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
folders_to_delete = [
    "qnn_unet",
    "qnn_vae_encoder",
    "qnn_vae_decoder",
]
for folder in os.listdir("."):
    if folder.startswith("output"):
        folders_to_delete.append(folder)
delete_folders(folders_to_delete)


def replace_mha_with_sha_blocks(unet_model):
    for name, module in unet_model.named_modules():
        if isinstance(module, CrossAttention):
            module.replace_linear_to_convs()


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_path", type=str, required=True)
    parser.add_argument("--size", type=int, default=512)
    parser.add_argument("--clip_skip", type=int, default=1)
    return parser.parse_args()


args = parse_args()
size = args.size
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


def generate_attn_mask(seq_len, device="cpu", dtype=torch.float32):
    mask = torch.tril(torch.ones(seq_len, seq_len, dtype=torch.bool))
    attn_mask = torch.zeros(seq_len, seq_len, dtype=dtype, device=device)
    attn_mask.masked_fill_(~mask, torch.finfo(attn_mask.dtype).min)
    attn_mask = attn_mask.unsqueeze(0).unsqueeze(0)

    return attn_mask


text_encoder = CLIPTextModel.from_pretrained(
    model_path,
    subfolder="text_encoder",
    attn_implementation="eager",  # no SDPA
)


class Wrapper(torch.nn.Module):
    def __init__(self, text_encoder):
        super().__init__()
        if clip_skip > 1:
            text_encoder.text_model.encoder.layers = (
                text_encoder.text_model.encoder.layers[: -(clip_skip - 1)]
            )
        self.text_encoder = text_encoder

    def forward(self, input_embedding):
        casual_mask = generate_attn_mask(
            77, device=input_embedding.device, dtype=input_embedding.dtype
        )
        hidden_states = self.text_encoder.text_model.encoder(
            input_embedding, causal_attention_mask=casual_mask
        ).last_hidden_state
        hidden_states = self.text_encoder.text_model.final_layer_norm(hidden_states)
        return hidden_states


clip = Wrapper(text_encoder)
clip.eval()
input_embedding = torch.randn(1, 77, 768)
torch.onnx.export(
    clip,
    input_embedding,
    "clip/model.onnx",
    input_names=["input_embedding"],
    output_names=["last_hidden_state"],
)
clip.text_encoder.text_model.embeddings.position_embedding.weight.data.numpy().tofile(
    "clip/pos_emb.bin"
)
clip.text_encoder.text_model.embeddings.token_embedding.weight.data.to(
    torch.float16
).numpy().tofile("clip/token_emb.bin")

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
            torch.randn(1, 4, size // 8, size // 8),
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
    convert_attribute=False,
)


class Wrapper(torch.nn.Module):
    def __init__(self, pipe):
        super().__init__()
        self.vae = pipe.vae

    def forward(self, input_ids):
        return self.vae.decode(input_ids, return_dict=False)[0]


vae = Wrapper(pipe)
vae.eval()

latents = torch.randn(1, 4, size // 8, size // 8)
torch.onnx.export(
    vae,
    latents,
    "vae_decoder/model.onnx",
    input_names=["input"],
    output_names=["output"],
)


class Wrapper(torch.nn.Module):
    def __init__(self, pipe):
        super().__init__()
        self.vae = pipe.vae

    def forward(self, image):
        output = self.vae.encode(image).latent_dist
        return output.mean, output.std


vae = Wrapper(pipe)
vae.eval()

dummy_input = torch.randn(1, 3, size, size)
torch.onnx.export(
    vae,
    dummy_input,
    "vae_encoder/model.onnx",
    input_names=["input"],
    output_names=["mean", "std"],
)
