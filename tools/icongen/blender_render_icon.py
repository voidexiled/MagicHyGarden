import bpy
import json
import os
import math
import argparse
from pathlib import Path
from mathutils import Vector, Matrix, Quaternion, Euler

def parse_args():
    argv = []
    if "--" in os.sys.argv:
        argv = os.sys.argv[os.sys.argv.index("--") + 1:]
    p = argparse.ArgumentParser()
    p.add_argument("--model", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--assets-root", required=True)
    p.add_argument("--config", required=True)
    p.add_argument("--texture", required=True)
    return p.parse_args(argv)

# (x,y,z)_file -> (x, -z, y)_blender
B = Matrix(((1, 0, 0),
            (0, 0, -1),
            (0, 1, 0)))
BT = B.transposed()

def v3(obj, default=(0.0, 0.0, 0.0)) -> Vector:
    if not isinstance(obj, dict):
        return Vector(default)
    return Vector((float(obj.get("x", default[0])),
                   float(obj.get("y", default[1])),
                   float(obj.get("z", default[2]))))

def map_vec(v: Vector) -> Vector:
    return B @ v

def euler_file_deg_to_blender_quat(rx_deg: float, ry_deg: float, rz_deg: float, order: str = "XYZ") -> Quaternion:
    # Rotación en ejes del engine (file basis) -> convertir a Blender basis
    e = Euler((math.radians(rx_deg), math.radians(ry_deg), math.radians(rz_deg)), order)
    Rf = e.to_matrix()
    Rb = B @ Rf @ BT
    return Rb.to_quaternion()

def quat_from_file(qd: dict) -> Quaternion:
    if not isinstance(qd, dict):
        return Quaternion((1, 0, 0, 0))
    x = float(qd.get("x", 0.0))
    y = float(qd.get("y", 0.0))
    z = float(qd.get("z", 0.0))
    w = float(qd.get("w", 1.0))
    qf = Quaternion((w, x, y, z))
    Rf = qf.to_matrix()
    Rb = B @ Rf @ BT
    return Rb.to_quaternion()

def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)

def configure_render(cfg, out_path):
    scene = bpy.context.scene
    scene.render.engine = cfg.get("engine", "BLENDER_EEVEE")

    base = int(cfg.get("size", 64))
    ss = int(cfg.get("supersample", 1))
    if ss < 1:
        ss = 1

    scene.render.resolution_x = base * ss
    scene.render.resolution_y = base * ss
    scene.render.resolution_percentage = 100

    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.film_transparent = True
    scene.render.filepath = out_path

    # Color management: Standard (sin Filmic)
    vs = scene.view_settings
    try:
        vs.view_transform = cfg.get("view_transform", "Standard")
    except Exception:
        pass
    try:
        vs.exposure = float(cfg.get("exposure", 0.0))
    except Exception:
        pass
    try:
        vs.gamma = float(cfg.get("gamma", 1.0))
    except Exception:
        pass

    # Eevee AA / TAA samples (queremos "sin AA")
    ee = getattr(scene, "eevee", None)
    taa = int(cfg.get("taa_samples", 1))
    if ee:
        # distintos nombres según versión
        if hasattr(ee, "taa_render_samples"):
            ee.taa_render_samples = taa
        if hasattr(ee, "taa_samples"):
            ee.taa_samples = taa
        # desactivar AO si existe
        if hasattr(ee, "use_gtao"):
            ee.use_gtao = False

    # World sin iluminación (full transparent de fondo ya está)
    if scene.world is None:
        scene.world = bpy.data.worlds.new("WORLD")
    scene.world.use_nodes = True
    wn = scene.world.node_tree.nodes
    bg = wn.get("Background")
    if bg:
        try:
            bg.inputs["Strength"].default_value = 0.0
        except Exception:
            pass

def setup_camera(cfg, ortho_scale):
    cam_data = bpy.data.cameras.new("ICON_CAM")
    cam_data.type = "ORTHO"
    cam_data.ortho_scale = ortho_scale

    cam_obj = bpy.data.objects.new("ICON_CAM", cam_data)
    bpy.context.collection.objects.link(cam_obj)

    cam_obj.location = Vector((0, -40, 0))

    target = bpy.data.objects.new("CAM_TARGET", None)
    bpy.context.collection.objects.link(target)
    target.location = Vector((0, 0, 0))

    c = cam_obj.constraints.new(type="TRACK_TO")
    c.target = target
    c.track_axis = "TRACK_NEGATIVE_Z"
    c.up_axis = "UP_Y"

    bpy.context.scene.camera = cam_obj
    return cam_obj

def collect_mesh_objects():
    return [o for o in bpy.context.scene.objects if o.type == "MESH"]

def compute_bbox_world(objs):
    mins = Vector((1e9, 1e9, 1e9))
    maxs = Vector((-1e9, -1e9, -1e9))
    for o in objs:
        if o.type != "MESH":
            continue
        for v in o.bound_box:
            w = o.matrix_world @ Vector(v)
            mins.x = min(mins.x, w.x); mins.y = min(mins.y, w.y); mins.z = min(mins.z, w.z)
            maxs.x = max(maxs.x, w.x); maxs.y = max(maxs.y, w.y); maxs.z = max(maxs.z, w.z)
    return mins, maxs

# --------- Unlit material (Emission + alpha cut) ---------

def make_unlit_material(image_path: str, cfg: dict) -> bpy.types.Material:
    import os
    name = f"MAT_UNLIT_{os.path.basename(image_path)}"
    mat = bpy.data.materials.get(name)
    if mat:
        return mat

    mat = bpy.data.materials.new(name=name)
    if hasattr(mat, "use_nodes"):
        mat.use_nodes = True

    nodes = mat.node_tree.nodes
    links = mat.node_tree.links
    nodes.clear()

    out = nodes.new("ShaderNodeOutputMaterial")
    tex = nodes.new("ShaderNodeTexImage")
    bsdf = nodes.new("ShaderNodeBsdfPrincipled")

    img = bpy.data.images.load(image_path, check_existing=True)
    tex.image = img

    # Alpha correcto del PNG
    try:
        tex.image.alpha_mode = "STRAIGHT"
    except Exception:
        pass

    # Filtrado
    filt = str(cfg.get("texture_filter", "Linear")).lower()
    tex.interpolation = "Closest" if filt == "closest" else "Linear"
    tex.extension = "CLIP"

    # Helper para encontrar sockets por nombre (Blender cambia labels)
    def sock(inputs, *names):
        for n in names:
            s = inputs.get(n)
            if s is not None:
                return s
        return None

    # Debug opcional (si quieres ver cómo se llaman los sockets)
    if cfg.get("debug_sockets", False):
        print("[BSDF inputs]", [s.name for s in bsdf.inputs])

    base_color = sock(bsdf.inputs, "Base Color", "BaseColor")
    alpha = sock(bsdf.inputs, "Alpha")
    emission_color = sock(bsdf.inputs, "Emission", "Emission Color")
    emission_strength = sock(bsdf.inputs, "Emission Strength")

    # Conectar textura
    if base_color is not None:
        links.new(tex.outputs["Color"], base_color)

    # “Unlit”: Emission = textura
    if emission_color is not None:
        links.new(tex.outputs["Color"], emission_color)
    if emission_strength is not None:
        emission_strength.default_value = 1.0

    # Alpha recorte
    if alpha is not None:
        links.new(tex.outputs["Alpha"], alpha)

    # Apaga especular (aunque no haya luces)
    for k in ("Metallic", "Roughness", "Specular", "Specular IOR Level"):
        s = bsdf.inputs.get(k)
        if s is not None:
            if k == "Roughness":
                s.default_value = 1.0
            else:
                s.default_value = 0.0

    links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])

    # Alpha mode
    mode = str(cfg.get("alpha_mode", "CLIP")).upper()
    if hasattr(mat, "blend_method"):
        mat.blend_method = mode if mode in ("CLIP", "BLEND", "HASHED") else "CLIP"

    thr = float(cfg.get("alpha_clip_threshold", 0.08))
    if hasattr(mat, "alpha_threshold"):
        mat.alpha_threshold = thr

    if hasattr(mat, "use_backface_culling"):
        mat.use_backface_culling = False

    return mat



def write_uvs(mesh, uvs_per_vert):
    uv_layer = mesh.uv_layers.new(name="UVMap")
    for poly in mesh.polygons:
        for li in range(poly.loop_start, poly.loop_start + poly.loop_total):
            vi = mesh.loops[li].vertex_index
            u, v_down = uvs_per_vert[vi]
            uv_layer.data[li].uv = (u, 1.0 - v_down)

def uv_rect_to_corners(offset_xy, size_wh, img_w, img_h, mirror_xy=(False, False), angle=0):
    ox, oy = float(offset_xy[0]), float(offset_xy[1])
    w, h = float(size_wh[0]), float(size_wh[1])

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

    return [BR, BL, TL, TR]  # BR,BL,TL,TR

def apply_stretch_about_center(v, center, stretch):
    d = v - center
    return Vector((center.x + d.x * stretch.x,
                   center.y + d.y * stretch.y,
                   center.z + d.z * stretch.z))

def build_box(name, center_file: Vector, size_xyz, stretch_xyz, texture_layout, img_w, img_h, mat):
    sx, sy, sz = float(size_xyz[0]), float(size_xyz[1]), float(size_xyz[2])
    hx, hy, hz = sx / 2.0, sy / 2.0, sz / 2.0

    cx, cy, cz = center_file.x, center_file.y, center_file.z
    x1, x2 = cx - hx, cx + hx
    y1, y2 = cy - hy, cy + hy
    z1, z2 = cz - hz, cz + hz

    corners = {
        "x1y1z1": Vector((x1, y1, z1)),
        "x2y1z1": Vector((x2, y1, z1)),
        "x2y2z1": Vector((x2, y2, z1)),
        "x1y2z1": Vector((x1, y2, z1)),
        "x1y1z2": Vector((x1, y1, z2)),
        "x2y1z2": Vector((x2, y1, z2)),
        "x2y2z2": Vector((x2, y2, z2)),
        "x1y2z2": Vector((x1, y2, z2)),
    }

    stretch = Vector((float(stretch_xyz[0]), float(stretch_xyz[1]), float(stretch_xyz[2])))

    faces_def = {
        "front": [corners["x2y1z2"], corners["x1y1z2"], corners["x1y2z2"], corners["x2y2z2"]],
        "back":  [corners["x1y1z1"], corners["x2y1z1"], corners["x2y2z1"], corners["x1y2z1"]],
        "right": [corners["x2y1z1"], corners["x2y1z2"], corners["x2y2z2"], corners["x2y2z1"]],
        "left":  [corners["x1y1z2"], corners["x1y1z1"], corners["x1y2z1"], corners["x1y2z2"]],
        "top":   [corners["x2y2z1"], corners["x1y2z1"], corners["x1y2z2"], corners["x2y2z2"]],
        "bottom":[corners["x2y1z2"], corners["x1y1z2"], corners["x1y1z1"], corners["x2y1z1"]],
    }

    dims = {
        "front": (sx, sy), "back": (sx, sy),
        "right": (sz, sy), "left": (sz, sy),
        "top": (sx, sz), "bottom": (sx, sz),
    }

    verts, uvs, faces = [], [], []

    def add_face(face_key):
        layout = (texture_layout or {}).get(face_key)
        if not layout:
            return
        off = layout.get("offset", {})
        mir = layout.get("mirror", {})
        ang = layout.get("angle", 0)

        offset_xy = (float(off.get("x", 0)), float(off.get("y", 0)))
        fw, fh = dims[face_key]
        mirror_xy = (bool(mir.get("x", False)), bool(mir.get("y", False)))
        uv4 = uv_rect_to_corners(offset_xy, (fw, fh), img_w, img_h, mirror_xy, ang)

        base = len(verts)
        face_verts = faces_def[face_key]
        stretched = [apply_stretch_about_center(v, center_file, stretch) for v in face_verts]
        mapped = [map_vec(v) for v in stretched]

        verts.extend([tuple(v) for v in mapped])
        uvs.extend(uv4)
        faces.append((base + 0, base + 1, base + 2, base + 3))

    for fk in ["front", "back", "right", "left", "top", "bottom"]:
        add_face(fk)

    mesh = bpy.data.meshes.new(name)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)

    mesh.from_pydata(verts, [], faces)
    mesh.update()

    obj.data.materials.append(mat)
    write_uvs(mesh, uvs)
    return obj

def build_quad(name, center_file: Vector, size_xy, stretch_xyz, texture_layout, img_w, img_h, mat):
    w, h = float(size_xy[0]), float(size_xy[1])
    stretch = Vector((float(stretch_xyz[0]), float(stretch_xyz[1]), float(stretch_xyz[2])))

    BR = Vector(( w/2, -h/2, 0))
    BL = Vector((-w/2, -h/2, 0))
    TL = Vector((-w/2,  h/2, 0))
    TR = Vector(( w/2,  h/2, 0))
    local = [BR, BL, TL, TR]

    face_verts = [center_file + v for v in local]
    stretched = [apply_stretch_about_center(v, center_file, stretch) for v in face_verts]
    mapped = [map_vec(v) for v in stretched]

    layout_front = (texture_layout or {}).get("front") or (texture_layout or {}).get("Front")
    if not layout_front:
        uv4 = [(1, 1), (0, 1), (0, 0), (1, 0)]
    else:
        off = layout_front.get("offset", {})
        mir = layout_front.get("mirror", {})
        ang = layout_front.get("angle", 0)
        offset_xy = (float(off.get("x", 0)), float(off.get("y", 0)))
        mirror_xy = (bool(mir.get("x", False)), bool(mir.get("y", False)))
        uv4 = uv_rect_to_corners(offset_xy, (w, h), img_w, img_h, mirror_xy, ang)

    mesh = bpy.data.meshes.new(name)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)

    mesh.from_pydata([tuple(v) for v in mapped], [], [(0, 1, 2, 3)])
    mesh.update()

    obj.data.materials.append(mat)
    write_uvs(mesh, uv4)
    return obj

def build_node_recursive(node, parent_obj, img_w, img_h, mat):
    name = node.get("name") or f"node_{node.get('id','')}"
    pos = map_vec(v3(node.get("position")))
    rot = quat_from_file(node.get("orientation"))

    empty = bpy.data.objects.new(f"NODE_{name}", None)
    bpy.context.collection.objects.link(empty)
    empty.parent = parent_obj
    empty.location = pos
    empty.rotation_mode = "QUATERNION"
    empty.rotation_quaternion = rot

    shape = node.get("shape")
    if isinstance(shape, dict) and bool(shape.get("visible", True)):
        stype = shape.get("type")
        center_file = v3(shape.get("offset"))
        stretch = v3(shape.get("stretch"), default=(1, 1, 1))
        tex_layout = shape.get("textureLayout") if isinstance(shape.get("textureLayout"), dict) else {}

        if stype == "box":
            settings = shape.get("settings", {})
            size = settings.get("size", {})
            size_xyz = (float(size.get("x", 1)), float(size.get("y", 1)), float(size.get("z", 1)))
            obj = build_box(f"BOX_{name}", center_file, size_xyz,
                            (stretch.x, stretch.y, stretch.z), tex_layout, img_w, img_h, mat)
            obj.parent = empty

        elif stype == "quad":
            settings = shape.get("settings", {})
            size = settings.get("size", {})
            size_xy = (float(size.get("x", 1)), float(size.get("y", 1)))
            obj = build_quad(f"QUAD_{name}", center_file, size_xy,
                             (stretch.x, stretch.y, stretch.z), tex_layout, img_w, img_h, mat)
            obj.parent = empty

            normal = settings.get("normal")
            if isinstance(normal, str) and normal != "+Z":
                if normal == "-Z":
                    obj.rotation_euler = Euler((0, math.radians(180), 0), "XYZ")
                elif normal == "+X":
                    obj.rotation_euler = Euler((0, math.radians(-90), 0), "XYZ")
                elif normal == "-X":
                    obj.rotation_euler = Euler((0, math.radians(90), 0), "XYZ")
                elif normal == "+Y":
                    obj.rotation_euler = Euler((math.radians(-90), 0, 0), "XYZ")
                elif normal == "-Y":
                    obj.rotation_euler = Euler((math.radians(90), 0, 0), "XYZ")

    for ch in node.get("children", []) or []:
        if isinstance(ch, dict):
            build_node_recursive(ch, empty, img_w, img_h, mat)

    return empty

def main(args):
    cfg = json.loads(Path(args.config).read_text("utf-8"))
    clear_scene()
    configure_render(cfg, args.out)

    model_path = Path(args.model)
    j = json.loads(model_path.read_text("utf-8"))

    tex_path = Path(args.texture)
    if not tex_path.is_absolute():
        tex_path = Path(args.assets_root) / args.texture
    if not tex_path.exists():
        raise RuntimeError(f"Texture no existe: {tex_path}")

    mat = make_unlit_material(str(tex_path), cfg)

    # image size for UV normalization
    tex_node = None
    for n in mat.node_tree.nodes:
        if n.type == "TEX_IMAGE":
            tex_node = n
            break
    if tex_node is None or tex_node.image is None:
        raise RuntimeError("No pude leer la imagen para UVs.")
    img_w, img_h = float(tex_node.image.size[0]), float(tex_node.image.size[1])

    root = bpy.data.objects.new("ICON_ROOT", None)
    bpy.context.collection.objects.link(root)

    nodes = j.get("nodes", [])
    if not isinstance(nodes, list) or not nodes:
        raise RuntimeError("blockymodel sin nodes[]")

    for n in nodes:
        if isinstance(n, dict):
            build_node_recursive(n, root, img_w, img_h, mat)

    objs = collect_mesh_objects()
    if not objs:
        raise RuntimeError("No se generó geometría (no hay MESH).")

    # flat shading
    for o in objs:
        for p in o.data.polygons:
            p.use_smooth = False

    # Center
    mins, maxs = compute_bbox_world(objs)
    center = (mins + maxs) * 0.5
    root.location -= center

    # Apply editor-like sliders
    rx, ry, rz = cfg.get("rotation_deg", [0, 0, 0])
    order = cfg.get("rotation_order", "XYZ")

    root.rotation_mode = "QUATERNION"
    root.rotation_quaternion = euler_file_deg_to_blender_quat(float(rx), float(ry), float(rz), order)
    sc = float(cfg.get("scale", 1.0))
    root.scale = (sc, sc, sc)

    # Fit camera
    mins2, maxs2 = compute_bbox_world(objs)
    size = max((maxs2 - mins2).x, (maxs2 - mins2).y, (maxs2 - mins2).z)
    ortho_scale = size * float(cfg.get("padding", 1.12))

    cam = setup_camera(cfg, ortho_scale)

    # Position offset
    posx, posy = cfg.get("pos", [0.0, 0.0])
    pos_scale = float(cfg.get("pos_scale", 0.02))
    right = cam.matrix_world.to_quaternion() @ Vector((1, 0, 0))
    up = cam.matrix_world.to_quaternion() @ Vector((0, 0, 1))
    root.location += right * (float(posx) * ortho_scale * pos_scale) + up * (float(posy) * ortho_scale * pos_scale)

    bpy.ops.render.render(write_still=True)

if __name__ == "__main__":
    args = parse_args()
    main(args)
