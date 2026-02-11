import json
from pathlib import Path

ROOT = Path(".")
SRC = ROOT / "src" / "main" / "resources"
BUILD = ROOT / "build" / "resources" / "main"

LUNAR = {
    "Dawnlit": {
        "system_id": "ParticleSystem_Lunar_Dawnlit",
        "spawners": [
            {
                "spawner_id": "ParticleSpawner_Lunar_Dawnlit_Aurora",
                "texture": "Particles/Textures/Basic/Gradient_Reflected_Inv.png",
                "color_start": "#b58cff",
                "color_end": "#7fd6ff",
                "render": "BlendAdd",
                "spawn_min": 2,
                "spawn_max": 3,
                "life_min": 3.5,
                "life_max": 6.0,
                "scale_min": 0.55,
                "scale_max": 0.95,
                "emit": 0.55,
                "speed_min": 0.01,
                "speed_max": 0.04,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Dawnlit_Beam",
                "texture": "Particles/Textures/Basic/LightBeam2.png",
                "color_start": "#d6b5ff",
                "color_end": "#8fd2ff",
                "render": "BlendAdd",
                "spawn_min": 3,
                "spawn_max": 6,
                "life_min": 1.2,
                "life_max": 2.2,
                "scale_min": 0.20,
                "scale_max": 0.35,
                "emit": 0.70,
                "speed_min": 0.03,
                "speed_max": 0.08,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Dawnlit_Star",
                "texture": "Particles/Textures/Basic/Star5.png",
                "color_start": "#caa3ff",
                "color_end": "#8fd2ff",
                "render": "BlendAdd",
                "spawn_min": 6,
                "spawn_max": 12,
                "life_min": 0.8,
                "life_max": 1.3,
                "scale_min": 0.05,
                "scale_max": 0.09,
                "emit": 0.55,
                "speed_min": 0.06,
                "speed_max": 0.12,
            },
        ],
    },
    "Dawnbound": {
        "system_id": "ParticleSystem_Lunar_Dawnbound",
        "spawners": [
            {
                "spawner_id": "ParticleSpawner_Lunar_Dawnbound_Sigil",
                "texture": "Particles/Textures/Circles/Signature_Spin7.png",
                "color_start": "#a96cff",
                "color_end": "#6ad0ff",
                "render": "BlendAdd",
                "spawn_min": 1,
                "spawn_max": 1,
                "life_min": 3.5,
                "life_max": 6.0,
                "scale_min": 0.32,
                "scale_max": 0.52,
                "emit": 0.30,
                "speed_min": 0.0,
                "speed_max": 0.02,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Dawnbound_Ring",
                "texture": "Particles/Textures/Circles/Ring_Trail.png",
                "color_start": "#caa3ff",
                "color_end": "#8fd2ff",
                "render": "BlendAdd",
                "spawn_min": 1,
                "spawn_max": 2,
                "life_min": 2.5,
                "life_max": 4.0,
                "scale_min": 0.25,
                "scale_max": 0.40,
                "emit": 0.25,
                "speed_min": 0.01,
                "speed_max": 0.03,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Dawnbound_Wisp",
                "texture": "Particles/Textures/Circles/Circle_Wisp.png",
                "color_start": "#caa3ff",
                "color_end": "#8fd2ff",
                "render": "BlendAdd",
                "spawn_min": 3,
                "spawn_max": 6,
                "life_min": 2.0,
                "life_max": 3.5,
                "scale_min": 0.12,
                "scale_max": 0.22,
                "emit": 0.45,
                "speed_min": 0.03,
                "speed_max": 0.08,
            },
        ],
    },
    "Amberlit": {
        "system_id": "ParticleSystem_Lunar_Amberlit",
        "spawners": [
            {
                "spawner_id": "ParticleSpawner_Lunar_Amberlit_Aura",
                "texture": "Particles/Textures/Basic/Glow_Star.png",
                "color_start": "#ffb65c",
                "color_end": "#ffe0b6",
                "render": "BlendAdd",
                "spawn_min": 1,
                "spawn_max": 2,
                "life_min": 3.0,
                "life_max": 5.0,
                "scale_min": 0.45,
                "scale_max": 0.70,
                "emit": 0.60,
                "speed_min": 0.01,
                "speed_max": 0.04,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Amberlit_Sunray",
                "texture": "Particles/Textures/Basic/Ray.png",
                "color_start": "#ffcc7a",
                "color_end": "#ff9a3c",
                "render": "BlendAdd",
                "spawn_min": 4,
                "spawn_max": 8,
                "life_min": 1.2,
                "life_max": 2.0,
                "scale_min": 0.22,
                "scale_max": 0.36,
                "emit": 0.75,
                "speed_min": 0.03,
                "speed_max": 0.08,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Amberlit_Mote",
                "texture": "Particles/Textures/Basic/Ball5.png",
                "color_start": "#ffd08a",
                "color_end": "#ff9a3c",
                "render": "BlendAdd",
                "spawn_min": 10,
                "spawn_max": 20,
                "life_min": 1.0,
                "life_max": 1.8,
                "scale_min": 0.04,
                "scale_max": 0.08,
                "emit": 0.55,
                "speed_min": 0.05,
                "speed_max": 0.10,
            },
        ],
    },
    "Amberbound": {
        "system_id": "ParticleSystem_Lunar_Amberbound",
        "spawners": [
            {
                "spawner_id": "ParticleSpawner_Lunar_Amberbound_Ring",
                "texture": "Particles/Textures/Basic/Ring6.png",
                "color_start": "#ffb15f",
                "color_end": "#ff7a00",
                "render": "BlendAdd",
                "spawn_min": 1,
                "spawn_max": 2,
                "life_min": 3.0,
                "life_max": 5.0,
                "scale_min": 0.45,
                "scale_max": 0.75,
                "emit": 0.45,
                "speed_min": 0.01,
                "speed_max": 0.04,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Amberbound_Sigil",
                "texture": "Particles/Textures/Circles/Signature_Spin5.png",
                "color_start": "#ffb15f",
                "color_end": "#ff7a00",
                "render": "BlendAdd",
                "spawn_min": 1,
                "spawn_max": 1,
                "life_min": 3.0,
                "life_max": 4.5,
                "scale_min": 0.28,
                "scale_max": 0.45,
                "emit": 0.25,
                "speed_min": 0.0,
                "speed_max": 0.02,
            },
            {
                "spawner_id": "ParticleSpawner_Lunar_Amberbound_Glint",
                "texture": "Particles/Textures/Basic/Star3.png",
                "color_start": "#ffd08a",
                "color_end": "#ff9a3c",
                "render": "BlendAdd",
                "spawn_min": 12,
                "spawn_max": 24,
                "life_min": 0.7,
                "life_max": 1.2,
                "scale_min": 0.06,
                "scale_max": 0.12,
                "emit": 0.75,
                "speed_min": 0.08,
                "speed_max": 0.16,
            },
        ],
    },
}

PARTICLE_DIR = Path("Server/Particles/Block/Lunar")
SPAWNER_DIR = PARTICLE_DIR / "Spawners"


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=True)


def make_spawner(cfg: dict) -> dict:
    return {
        "RenderMode": cfg["render"],
        "ParticleRotationInfluence": "Billboard",
        "LinearFiltering": True,
        "ParticleRotateWithSpawner": True,
        "LightInfluence": 0,
        "MaxConcurrentParticles": 0,
        "ParticleLifeSpan": {"Min": cfg["life_min"], "Max": cfg["life_max"]},
        "SpawnRate": {"Min": cfg["spawn_min"], "Max": cfg["spawn_max"]},
        "TotalParticles": {"Min": -1, "Max": -1},
        "InitialVelocity": {
            "Speed": {"Min": cfg["speed_min"], "Max": cfg["speed_max"]},
            "Yaw": {"Min": -180, "Max": 180},
            "Pitch": {"Min": -20, "Max": 60},
        },
        "Particle": {
            "Texture": cfg["texture"],
            "ScaleRatioConstraint": "OneToOne",
            "UVOption": "RandomFlipU",
            "Animation": {
                "0": {
                    "FrameIndex": {"Min": 0, "Max": 0},
                    "Opacity": 0.0,
                    "Color": cfg["color_start"],
                },
                "20": {"Opacity": 1.0},
                "80": {"Opacity": 1.0},
                "100": {"Opacity": 0.0, "Color": cfg["color_end"]},
            },
            "InitialAnimationFrame": {
                "Rotation": {"Z": {"Min": 0, "Max": 360}},
                "Scale": {
                    "X": {"Min": cfg["scale_min"], "Max": cfg["scale_max"]},
                    "Y": {"Min": cfg["scale_min"], "Max": cfg["scale_max"]},
                },
                "Opacity": 1.0,
                "FrameIndex": {"Min": 0, "Max": 0},
            },
        },
        "ParticleCollision": {"BlockType": "None", "Action": "Expire"},
        "EmitOffset": {
            "X": {"Min": 0.1, "Max": cfg["emit"]},
            "Y": {"Min": 0.1, "Max": cfg["emit"]},
            "Z": {"Min": 0.1, "Max": cfg["emit"]},
        },
        "IsLowRes": True,
    }


def make_system(cfg: dict) -> dict:
    return {
        "Spawners": [
            {"SpawnerId": sp["spawner_id"], "PositionOffset": {"Y": 0.25}}
            for sp in cfg["spawners"]
        ],
        "IsImportant": False,
    }


def generate_particles(root: Path) -> None:
    for cfg in LUNAR.values():
        system_path = root / PARTICLE_DIR / (cfg["system_id"] + ".particlesystem")
        for sp in cfg["spawners"]:
            spawner_path = root / SPAWNER_DIR / (sp["spawner_id"] + ".particlespawner")
            write_json(spawner_path, make_spawner(sp))
        write_json(system_path, make_system(cfg))


def add_particles_to_states(path: Path) -> None:
    with path.open("r", encoding="utf-8") as fh:
        data = json.load(fh)
    defs = data.get("BlockType", {}).get("State", {}).get("Definitions", {})
    if not isinstance(defs, dict):
        return

    suffix_map = {
        "dawnlit": LUNAR["Dawnlit"]["system_id"],
        "dawnbound": LUNAR["Dawnbound"]["system_id"],
        "amberlit": LUNAR["Amberlit"]["system_id"],
        "amberbound": LUNAR["Amberbound"]["system_id"],
    }

    for state_id, state in defs.items():
        for suffix, sys_id in suffix_map.items():
            if suffix in state_id:
                particles = state.get("Particles")
                if particles is None:
                    particles = []
                if not any(
                    isinstance(p, dict) and p.get("SystemId") == sys_id for p in particles
                ):
                    particles.append({"SystemId": sys_id})
                state["Particles"] = particles
                break

    with path.open("w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=True)


def main() -> None:
    generate_particles(SRC)
    generate_particles(BUILD)

    block_src = (
        SRC
        / "Server/Item/Items/Plant/Crop/Lettuce/Mghg_Plant_Crop_Lettuce_Block.json"
    )
    block_build = (
        BUILD
        / "Server/Item/Items/Plant/Crop/Lettuce/Mghg_Plant_Crop_Lettuce_Block.json"
    )
    if block_src.exists():
        add_particles_to_states(block_src)
    if block_build.exists():
        add_particles_to_states(block_build)


if __name__ == "__main__":
    main()
