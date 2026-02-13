import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

# ========= Basis / helpers =========
# Nos quedamos en el basis del .blockymodel (FILE) para evitar Blender/convert.
# Proyección ortográfica: X->screen, Y->screen, Z->depth

def euler_deg_to_matrix(rx, ry, rz, order="XYZ"):
    rx, ry, rz = map(lambda a: math.radians(float(a)), (rx, ry, rz))

    def Rx(a):
        ca, sa = math.cos(a), math.sin(a)
        return np.array([[1, 0, 0],
                         [0, ca, -sa],
                         [0, sa, ca]], dtype=np.float32)

    def Ry(a):
        ca, sa = math.cos(a), math.sin(a)
        return np.array([[ca, 0, sa],
                         [0, 1, 0],
                         [-sa, 0, ca]], dtype=np.float32)

    def Rz(a):
        ca, sa = math.cos(a), math.sin(a)
        return np.array([[ca, -sa, 0],
                         [sa, ca, 0],
                         [0, 0, 1]], dtype=np.float32)

    mats = {"X": Rx(rx), "Y": Ry(ry), "Z": Rz(rz)}
    R = np.eye(3, dtype=np.float32)
    for c in order.upper():
        R = R @ mats[c]
    return R

def quat_to_matrix(q):
    # q JSON: {x,y,z,w}
    x, y, z, w = map(float, (q.get("x", 0), q.get("y", 0), q.get("z", 0), q.get("w", 1)))
    n = math.sqrt(x*x + y*y + z*z + w*w)
    if n > 0:
        x, y, z, w = x/n, y/n, z/n, w/n

    xx, yy, zz = x*x, y*y, z*z
    xy, xz, yz = x*y, x*z, y*z
    wx, wy, wz = w*x, w*y, w*z

    return np.array([
        [1 - 2*(yy+zz),     2*(xy-wz),     2*(xz+wy)],
        [    2*(xy+wz), 1 - 2*(xx+zz),     2*(yz-wx)],
        [    2*(xz-wy),     2*(yz+wx), 1 - 2*(xx+yy)],
    ], dtype=np.float32)

def v3(d, default=(0, 0, 0)):
    if not isinstance(d, dict):
        return np.array(default, dtype=np.float32)
    return np.array([float(d.get("x", default[0])),
                     float(d.get("y", default[1])),
                     float(d.get("z", default[2]))], dtype=np.float32)

def uv_rect_to_corners(offset_xy, size_wh, img_w, img_h, mirror_xy=(False, False), angle=0):
    ox, oy = float(offset_xy[0]), float(offset_xy[1])
    w, h = float(size_wh[0]), float(size_wh[1])

    # offsets son top-left y v hacia abajo
    u1 = ox / img_w
    v1 = oy / img_h
    u2 = (ox + w) / img_w
    v2 = (oy + h) / img_h

    TL = (u1, v1)
    TR = (u2, v1)
    BR = (u2, v2)
    BL = (u1, v2)

    mx, my = mirror_xy
    if mx:
        TL, TR = TR, TL
        BL, BR = BR, BL
    if my:
        TL, BL = BL, TL
        TR, BR = BR, TR

    a = int(angle) % 360
    if a == 90:
        TL, TR, BR, BL = BL, TL, TR, BR
    elif a == 180:
        TL, TR, BR, BL = BR, BL, TL, TR
    elif a == 270:
        TL, TR, BR, BL = TR, BR, BL, TL

    # orden BR,BL,TL,TR
    return [BR, BL, TL, TR]

def apply_stretch_about_center(p, center, stretch):
    d = p - center
    return center + d * stretch

# ========= Mesh building from blockymodel =========

def build_box_tris(center, size_xyz, stretch, texture_layout, tex_w, tex_h):
    sx, sy, sz = map(float, size_xyz)
    hx, hy, hz = sx / 2.0, sy / 2.0, sz / 2.0

    cx, cy, cz = center
    x1, x2 = cx - hx, cx + hx
    y1, y2 = cy - hy, cy + hy
    z1, z2 = cz - hz, cz + hz

    corners = {
        "x1y1z1": np.array([x1, y1, z1], np.float32),
        "x2y1z1": np.array([x2, y1, z1], np.float32),
        "x2y2z1": np.array([x2, y2, z1], np.float32),
        "x1y2z1": np.array([x1, y2, z1], np.float32),
        "x1y1z2": np.array([x1, y1, z2], np.float32),
        "x2y1z2": np.array([x2, y1, z2], np.float32),
        "x2y2z2": np.array([x2, y2, z2], np.float32),
        "x1y2z2": np.array([x1, y2, z2], np.float32),
    }

    # BR,BL,TL,TR (FILE basis)
    faces_def = {
        "front":  [corners["x2y1z2"], corners["x1y1z2"], corners["x1y2z2"], corners["x2y2z2"]],  # +Z
        "back":   [corners["x1y1z1"], corners["x2y1z1"], corners["x2y2z1"], corners["x1y2z1"]],  # -Z
        "right":  [corners["x2y1z1"], corners["x2y1z2"], corners["x2y2z2"], corners["x2y2z1"]],  # +X
        "left":   [corners["x1y1z2"], corners["x1y1z1"], corners["x1y2z1"], corners["x1y2z2"]],  # -X
        "top":    [corners["x2y2z1"], corners["x1y2z1"], corners["x1y2z2"], corners["x2y2z2"]],  # +Y
        "bottom": [corners["x2y1z2"], corners["x1y1z2"], corners["x1y1z1"], corners["x2y1z1"]],  # -Y
    }

    dims = {
        "front": (sx, sy), "back": (sx, sy),
        "right": (sz, sy), "left": (sz, sy),
        "top": (sx, sz), "bottom": (sx, sz),
    }

    tris = []
    center_np = np.array(center, np.float32)
    stretch_np = np.array(stretch, np.float32)

    # orden estable (no debería afectar con z-buffer, pero ayuda a reproducibilidad)
    for fk in ("back", "right", "front", "left", "top", "bottom"):
        layout = (texture_layout or {}).get(fk)
        if not isinstance(layout, dict):
            continue

        off = layout.get("offset", {}) or {}
        mir = layout.get("mirror", {}) or {}
        ang = layout.get("angle", 0)

        fw, fh = dims[fk]
        uv4 = uv_rect_to_corners(
            (float(off.get("x", 0)), float(off.get("y", 0))),
            (fw, fh),
            tex_w, tex_h,
            (bool(mir.get("x", False)), bool(mir.get("y", False))),
            ang
        )

        quad = faces_def[fk]
        quad = [apply_stretch_about_center(p, center_np, stretch_np) for p in quad]

        tris.append((quad[0], quad[1], quad[2], uv4[0], uv4[1], uv4[2]))
        tris.append((quad[0], quad[2], quad[3], uv4[0], uv4[2], uv4[3]))

    return tris

def build_quad_tris(center, size_xy, stretch, texture_layout, tex_w, tex_h):
    w, h = map(float, size_xy)
    cx, cy, cz = center

    BR = np.array([cx + w/2, cy - h/2, cz], np.float32)
    BL = np.array([cx - w/2, cy - h/2, cz], np.float32)
    TL = np.array([cx - w/2, cy + h/2, cz], np.float32)
    TR = np.array([cx + w/2, cy + h/2, cz], np.float32)

    quad = [BR, BL, TL, TR]
    center_np = np.array(center, np.float32)
    stretch_np = np.array(stretch, np.float32)
    quad = [apply_stretch_about_center(p, center_np, stretch_np) for p in quad]

    layout = (texture_layout or {}).get("front") or (texture_layout or {}).get("Front")
    if not isinstance(layout, dict):
        uv4 = [(1, 1), (0, 1), (0, 0), (1, 0)]
    else:
        off = layout.get("offset", {}) or {}
        mir = layout.get("mirror", {}) or {}
        ang = layout.get("angle", 0)
        uv4 = uv_rect_to_corners(
            (float(off.get("x", 0)), float(off.get("y", 0))),
            (w, h),
            tex_w, tex_h,
            (bool(mir.get("x", False)), bool(mir.get("y", False))),
            ang
        )

    return [
        (quad[0], quad[1], quad[2], uv4[0], uv4[1], uv4[2]),
        (quad[0], quad[2], quad[3], uv4[0], uv4[2], uv4[3]),
    ]

def traverse_nodes(node, parent_R, parent_t, tex_w, tex_h):
    t = v3(node.get("position"))
    R = quat_to_matrix(node.get("orientation") or {})
    Rw = parent_R @ R
    tw = parent_R @ t + parent_t

    local_tris = []

    shape = node.get("shape")
    if isinstance(shape, dict) and bool(shape.get("visible", True)):
        stype = shape.get("type")
        center = v3(shape.get("offset"))
        stretch = v3(shape.get("stretch"), default=(1, 1, 1))
        tex_layout = shape.get("textureLayout") if isinstance(shape.get("textureLayout"), dict) else {}

        if stype == "box":
            settings = shape.get("settings", {}) or {}
            size = settings.get("size", {}) or {}
            size_xyz = (float(size.get("x", 1)), float(size.get("y", 1)), float(size.get("z", 1)))
            local_tris.extend(build_box_tris(center, size_xyz, stretch, tex_layout, tex_w, tex_h))

        elif stype == "quad":
            settings = shape.get("settings", {}) or {}
            size = settings.get("size", {}) or {}
            size_xy = (float(size.get("x", 1)), float(size.get("y", 1)))
            # Nota: normals/rotación de quad no implementada aquí (no la necesitas para el cabbage_flat.box)
            local_tris.extend(build_quad_tris(center, size_xy, stretch, tex_layout, tex_w, tex_h))

    out = []
    for p0, p1, p2, uv0, uv1, uv2 in local_tris:
        P0 = Rw @ p0 + tw
        P1 = Rw @ p1 + tw
        P2 = Rw @ p2 + tw
        out.append((P0, P1, P2, uv0, uv1, uv2))

    for ch in (node.get("children") or []):
        if isinstance(ch, dict):
            out.extend(traverse_nodes(ch, Rw, tw, tex_w, tex_h))

    return out

# ========= Rasterizer =========

def sample_texture(tex_rgba, u, v, filter_mode="nearest", nearest_mode="floor", uv_bias_px=(-0.5, -0.5)):
    """
    GPU-like sampling:
      x = u*w + bias, y = v*h + bias
    nearest_mode: "floor" (recomendado) o "round"
    uv_bias_px: típicamente [-0.5, -0.5] para alinear texels de atlas.
    """
    h, w, _ = tex_rgba.shape
    u = max(0.0, min(1.0, float(u)))
    v = max(0.0, min(1.0, float(v)))

    bx, by = float(uv_bias_px[0]), float(uv_bias_px[1])

    x = u * w + bx
    y = v * h + by

    if filter_mode == "linear":
        x0 = int(math.floor(x)); x1 = x0 + 1
        y0 = int(math.floor(y)); y1 = y0 + 1

        x0 = max(0, min(w - 1, x0)); x1 = max(0, min(w - 1, x1))
        y0 = max(0, min(h - 1, y0)); y1 = max(0, min(h - 1, y1))

        tx = x - math.floor(x)
        ty = y - math.floor(y)

        c00 = tex_rgba[y0, x0].astype(np.float32)
        c10 = tex_rgba[y0, x1].astype(np.float32)
        c01 = tex_rgba[y1, x0].astype(np.float32)
        c11 = tex_rgba[y1, x1].astype(np.float32)

        c0 = c00 * (1 - tx) + c10 * tx
        c1 = c01 * (1 - tx) + c11 * tx
        c = c0 * (1 - ty) + c1 * ty
        return c.astype(np.uint8)

    # nearest
    if str(nearest_mode).lower() == "round":
        xi = int(round(x))
        yi = int(round(y))
    else:
        xi = int(math.floor(x))
        yi = int(math.floor(y))

    xi = max(0, min(w - 1, xi))
    yi = max(0, min(h - 1, yi))
    return tex_rgba[yi, xi]

def rasterize(tris, tex_rgba, out_size, padding, pos, pos_scale, alpha_thresh, filter_mode, nearest_mode, uv_bias_px):
    W = H = int(out_size)
    color = np.zeros((H, W, 4), dtype=np.uint8)
    zbuf = np.full((H, W), 1e30, dtype=np.float32)

    pts = []
    for P0, P1, P2, *_ in tris:
        pts.append(P0); pts.append(P1); pts.append(P2)
    pts = np.stack(pts, axis=0)

    min_xy = pts[:, :2].min(axis=0)
    max_xy = pts[:, :2].max(axis=0)
    ext = max(max_xy[0] - min_xy[0], max_xy[1] - min_xy[1])
    if ext <= 1e-6:
        ext = 1.0

    fit = (out_size / (ext * float(padding)))
    center_xy = (min_xy + max_xy) * 0.5

    off_px = np.array([float(pos[0]), float(pos[1])], dtype=np.float32) * (out_size * float(pos_scale))

    def world_to_screen(P):
        xy = (P[:2] - center_xy) * fit
        x = (W * 0.5) + xy[0] + off_px[0]
        y = (H * 0.5) - xy[1] + off_px[1]
        z = -float(P[2])  # depth
        return x, y, z

    def edge(ax, ay, bx, by, cx, cy):
        return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax)

    alpha_cut = float(alpha_thresh) * 255.0

    for (P0, P1, P2, uv0, uv1, uv2) in tris:
        x0, y0, z0 = world_to_screen(P0)
        x1, y1, z1 = world_to_screen(P1)
        x2, y2, z2 = world_to_screen(P2)

        minx = max(0, int(math.floor(min(x0, x1, x2))))
        maxx = min(W - 1, int(math.ceil(max(x0, x1, x2))))
        miny = max(0, int(math.floor(min(y0, y1, y2))))
        maxy = min(H - 1, int(math.ceil(max(y0, y1, y2))))
        if minx > maxx or miny > maxy:
            continue

        area = edge(x0, y0, x1, y1, x2, y2)
        if abs(area) < 1e-8:
            continue

        for py in range(miny, maxy + 1):
            for px in range(minx, maxx + 1):
                cx = px + 0.5
                cy = py + 0.5

                w0 = edge(x1, y1, x2, y2, cx, cy)
                w1 = edge(x2, y2, x0, y0, cx, cy)
                w2 = edge(x0, y0, x1, y1, cx, cy)

                # inside test (ambos windings)
                if (w0 >= 0 and w1 >= 0 and w2 >= 0) or (w0 <= 0 and w1 <= 0 and w2 <= 0):
                    b0 = w0 / area
                    b1 = w1 / area
                    b2 = w2 / area

                    z = b0 * z0 + b1 * z1 + b2 * z2
                    if z >= zbuf[py, px]:
                        continue

                    u = b0 * uv0[0] + b1 * uv1[0] + b2 * uv2[0]
                    v = b0 * uv0[1] + b1 * uv1[1] + b2 * uv2[1]

                    c = sample_texture(tex_rgba, u, v, filter_mode, nearest_mode, uv_bias_px)
                    a = float(c[3])
                    if a < alpha_cut:
                        continue

                    color[py, px] = c
                    zbuf[py, px] = z

    return color

# ========= main =========

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--texture", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--config", required=True)
    ap.add_argument("--assets-root", default=".")
    args = ap.parse_args()

    cfg = json.loads(Path(args.config).read_text("utf-8"))

    assets_root = Path(args.assets_root)
    model_path = Path(args.model)
    tex_path = Path(args.texture)
    if not model_path.is_absolute():
        model_path = assets_root / model_path
    if not tex_path.is_absolute():
        tex_path = assets_root / tex_path

    model = json.loads(model_path.read_text("utf-8"))
    img = Image.open(tex_path).convert("RGBA")
    tex_rgba = np.array(img, dtype=np.uint8)
    tex_h, tex_w = tex_rgba.shape[0], tex_rgba.shape[1]

    tris = []
    for n in model.get("nodes", []) or []:
        if isinstance(n, dict):
            tris.extend(traverse_nodes(n, np.eye(3, dtype=np.float32), np.zeros(3, dtype=np.float32), tex_w, tex_h))

    if not tris:
        raise SystemExit("No triangles generated from blockymodel.")

    # ICON_ROOT transform (en FILE basis)
    rx, ry, rz = cfg.get("rotation_deg", [0, 0, 0])
    order = cfg.get("rotation_order", "XYZ")
    Rroot = euler_deg_to_matrix(rx, ry, rz, order)
    s = float(cfg.get("scale", 1.0))

    # centrar primero (como editor)
    pts = []
    for P0, P1, P2, *_ in tris:
        pts.append(P0); pts.append(P1); pts.append(P2)
    pts = np.stack(pts, axis=0)
    center = (pts.min(axis=0) + pts.max(axis=0)) * 0.5

    tris2 = []
    for P0, P1, P2, uv0, uv1, uv2 in tris:
        Q0 = (Rroot @ (P0 - center)) * s
        Q1 = (Rroot @ (P1 - center)) * s
        Q2 = (Rroot @ (P2 - center)) * s
        tris2.append((Q0, Q1, Q2, uv0, uv1, uv2))

    out_size = int(cfg.get("size", 64))
    padding = float(cfg.get("padding", 1.12))
    pos = cfg.get("pos", [0.0, 0.0])
    pos_scale = float(cfg.get("pos_scale", 0.02))

    # CLIP threshold: si quieres “solo alpha==0 desaparece”, usa 0.0
    alpha_thresh = float(cfg.get("alpha_clip_threshold", 0.08))

    filt = str(cfg.get("texture_filter", "closest")).lower()
    filter_mode = "linear" if filt == "linear" else "nearest"

    nearest_mode = str(cfg.get("nearest_mode", "floor")).lower()
    uv_bias_px = cfg.get("uv_sample_bias_px", [-0.5, -0.5])
    if not (isinstance(uv_bias_px, (list, tuple)) and len(uv_bias_px) == 2):
        uv_bias_px = [-0.5, -0.5]

    out_rgba = rasterize(
        tris2, tex_rgba,
        out_size=out_size,
        padding=padding,
        pos=pos,
        pos_scale=pos_scale,
        alpha_thresh=alpha_thresh,
        filter_mode=filter_mode,
        nearest_mode=nearest_mode,
        uv_bias_px=uv_bias_px
    )

    Image.fromarray(out_rgba, mode="RGBA").save(args.out)

if __name__ == "__main__":
    main()
