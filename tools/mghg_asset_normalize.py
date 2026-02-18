import argparse
import json
from pathlib import Path
from typing import Any, Dict, Optional

DEFAULT_CONFIG_REL = Path('Server/Farming/Visuals/Mghg_MutationVisuals.json')
LEGACY_CONFIG_REL = Path('Server/Farming/Mutations/Mghg_MutationVisuals.json')
DEFAULT_GLOB = 'Server/Item/Items/Plant/Crop/**/*Plant_Crop*_Item.json'


def deep_merge(base: Dict[str, Any], override: Dict[str, Any]) -> Dict[str, Any]:
    out = dict(base)
    for k, v in override.items():
        if isinstance(v, dict) and isinstance(out.get(k), dict):
            out[k] = deep_merge(out[k], v)
        else:
            out[k] = v
    return out


def load_config(path: Path) -> Dict[str, Any]:
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding='utf-8'))


def resolve_config_path(root: Path, explicit: Optional[str]) -> Path:
    if explicit:
        return root / explicit
    primary = root / DEFAULT_CONFIG_REL
    if primary.exists():
        return primary
    return root / LEGACY_CONFIG_REL


def normalize_file(path: Path, cfg: Dict[str, Any]) -> bool:
    data = json.loads(path.read_text(encoding='utf-8'))
    states = data.get('State')
    if not isinstance(states, dict):
        return False

    base_blocktype = data.get('BlockType', {}) if isinstance(data.get('BlockType'), dict) else {}
    defaults = cfg.get('Defaults', {}).get('BlockType', {}) if isinstance(cfg.get('Defaults', {}), dict) else {}
    overrides = cfg.get('Overrides', {}) if isinstance(cfg.get('Overrides', {}), dict) else {}

    changed = False
    for state_key, state in states.items():
        if not isinstance(state, dict):
            continue
        state_bt = state.get('BlockType', {}) if isinstance(state.get('BlockType'), dict) else {}
        override_bt = {}
        if isinstance(overrides.get(state_key), dict):
            override_bt = overrides[state_key].get('BlockType', {}) if isinstance(overrides[state_key].get('BlockType'), dict) else {}

        merged = deep_merge(base_blocktype, defaults)
        merged = deep_merge(merged, state_bt)
        merged = deep_merge(merged, override_bt)

        if state_bt != merged:
            state['BlockType'] = merged
            changed = True

    if changed:
        path.write_text(json.dumps(data, ensure_ascii=True, indent=2), encoding='utf-8')
    return changed


def main() -> None:
    parser = argparse.ArgumentParser(description='Normalize MGHG crop item state BlockType overrides.')
    parser.add_argument('--src', default='src/main/resources', help='Source resources root')
    parser.add_argument('--build', default='build/resources/main', help='Build resources root')
    parser.add_argument('--config', default=None, help='Optional override config path (relative to resource root)')
    parser.add_argument('--glob', default=DEFAULT_GLOB, help='Glob for assets to normalize')
    args = parser.parse_args()

    roots = [Path(args.src), Path(args.build)]
    total_changed = 0

    for root in roots:
        if not root.exists():
            continue
        cfg_path = resolve_config_path(root, args.config)
        cfg = load_config(cfg_path)
        for path in root.glob(args.glob):
            if normalize_file(path, cfg):
                print(f'patched {path}')
                total_changed += 1
    print(f'done. files changed: {total_changed}')


if __name__ == '__main__':
    main()
