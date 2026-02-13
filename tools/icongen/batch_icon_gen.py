import argparse
import json
import subprocess
import sys
from pathlib import Path

def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--item-json", required=True)
    p.add_argument("--assets-root", required=True)
    p.add_argument("--out-dir", required=True)
    p.add_argument("--config", required=True)
    p.add_argument("--patch-json", action="store_true")
    p.add_argument("--only", default=None)
    p.add_argument("--limit", type=int, default=None)
    return p.parse_args()

def safe_filename(s: str) -> str:
    return "".join(c if c.isalnum() or c in "._-" else "_" for c in s)

def resolve_blocktype_model_and_texture(state_obj: dict):
    bt = state_obj.get("BlockType") if isinstance(state_obj.get("BlockType"), dict) else None
    if not bt:
        return None, None
    model = bt.get("CustomModel")
    tex_list = bt.get("CustomModelTexture")
    texture = None
    if isinstance(tex_list, list) and tex_list and isinstance(tex_list[0], dict):
        texture = tex_list[0].get("Texture")
    if isinstance(model, str) and model and isinstance(texture, str) and texture:
        return model, texture
    return None, None

def main():
    args = parse_args()
    item_path = Path(args.item_json)
    assets_root = Path(args.assets_root)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    doc = json.loads(item_path.read_text(encoding="utf-8"))
    states = doc.get("State")
    if not isinstance(states, dict) or not states:
        raise SystemExit('No encontré "State" (o está vacío).')

    # icon paths relative to Common when possible
    try:
        out_dir_rel = str(out_dir.relative_to(assets_root)).replace("\\", "/")
    except Exception:
        out_dir_rel = None

    renderer = Path(__file__).parent / "software_render_icon.py"
    icons_map = {}
    count = 0

    for state_id, state_obj in states.items():
        if args.only and state_id != args.only:
            continue
        if not isinstance(state_obj, dict):
            continue

        model_rel, texture_rel = resolve_blocktype_model_and_texture(state_obj)
        if not model_rel or not texture_rel:
            print(f"[SKIP] {state_id}: no pude resolver CustomModel/CustomModelTexture")
            continue

        model_abs = (assets_root / model_rel) if not Path(model_rel).is_absolute() else Path(model_rel)
        tex_abs = (assets_root / texture_rel) if not Path(texture_rel).is_absolute() else Path(texture_rel)

        if not model_abs.exists():
            print(f"[SKIP] {state_id}: model no existe -> {model_abs}")
            continue
        if not tex_abs.exists():
            print(f"[SKIP] {state_id}: texture no existe -> {tex_abs}")
            continue

        out_png = out_dir / f"{safe_filename(state_id)}.png"

        cmd = [
            sys.executable, str(renderer),
            "--model", str(model_abs),
            "--texture", str(tex_abs),
            "--out", str(out_png),
            "--config", str(args.config),
            "--assets-root", str(assets_root),
        ]

        print("Rendering[software]:", state_id, "->", out_png.name)
        subprocess.run(cmd)

        # FAIL-FAST
        if (not out_png.exists()) or (out_png.stat().st_size < 200):
            print(f"[FAIL] {state_id}: no se generó PNG válido -> {out_png}")
            continue

        icon_path = str(out_png).replace("\\", "/") if out_dir_rel is None else f"{out_dir_rel}/{out_png.name}"
        icons_map[state_id] = icon_path

        if args.patch_json:
            state_obj["Icon"] = icon_path

        count += 1
        if args.limit is not None and count >= args.limit:
            break

    (out_dir / "icons_map.json").write_text(json.dumps(icons_map, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Done. Wrote:", out_dir / "icons_map.json")

    if args.patch_json:
        item_path.write_text(json.dumps(doc, indent=2, ensure_ascii=False), encoding="utf-8")
        print("Patched item json with per-state Icon:", item_path)

if __name__ == "__main__":
    main()
