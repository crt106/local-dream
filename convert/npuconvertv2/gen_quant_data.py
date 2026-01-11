from PIL import Image
import numpy as np
import pickle as pkl
import random
import os


def generate_raw(data_list, output_dir="./raw", input_list_file="./input_list.txt"):
    os.makedirs(output_dir, exist_ok=True)

    all_paths = []
    for i, row in enumerate(data_list):
        row_paths = []
        for j, item in enumerate(row):
            if item.dtype == np.int64:
                item = item.astype(np.int32)
            file_path = f"{output_dir}/{i}_{j}.raw"
            item.tofile(file_path)
            row_paths.append(file_path)
        all_paths.append(row_paths)

    with open(input_list_file, "w") as f:
        for row_paths in all_paths:
            f.write(" ".join(row_paths) + "\n")


with open("./data.pkl", "rb") as f:
    data = pkl.load(f)

processed_data = []
for sample, timestamp, text_embed in data["unet"]:
    sample = sample.astype(np.float32)
    text_embed = text_embed.astype(np.float32)

    if np.abs(sample).max() > 7.2:
        continue

    processed_data.append([sample[0], timestamp, text_embed[0]])
    processed_data.append([sample[1], timestamp, text_embed[1]])

print(f"Total valid samples for unet: {len(processed_data)}")
if len(processed_data) > 400:
    processed_data = random.sample(processed_data, 400)

generate_raw(
    processed_data,
    output_dir="./unet_input_raw",
    input_list_file="./input_list_unet.txt",
)

processed_data = []
for (latent,) in data["vae"]:
    latent = latent.astype(np.float32)
    processed_data.append([latent[0]])

generate_raw(
    processed_data,
    output_dir="./vae_decoder_input_raw",
    input_list_file="./input_list_vae_decoder.txt",
)


size = 512

images = os.listdir("images")
processed_data = []
for image in images:
    img = Image.open(os.path.join("images", image))
    img = img.convert("RGB")
    img = img.resize((size, size))
    img = np.array(img)
    img = img.astype(np.float32) / 255.0
    img = img.transpose(2, 0, 1)
    img = img * 2 - 1
    processed_data.append([img])

generate_raw(
    processed_data,
    output_dir="./vae_encoder_input_raw",
    input_list_file="./input_list_vae_encoder.txt",
)
