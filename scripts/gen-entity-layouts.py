#!/usr/bin/env python3
"""生成实体数据表（每次升级 MC 版本后重新运行）。

输出两个文件：
  - entity-layouts.tsv：元数据字段布局（见下）
  - entity-traits.tsv：原版尺寸（宽、高）和默认最大生命值，用于碰撞箱和血量缩放

从服务端 jar 的字节码计算每种实体的 SynchedEntityData 字段（索引、所属类.字段名、序列化器），
输出到 plugin/src/main/resources/entity-layouts.tsv，插件运行时据此：
  1. 按字段名定位属性对应的索引（不手写数字索引）
  2. 校验发出的每个索引都在该实体的字段范围内（发错索引会导致客户端断线）

原理：
  - EntityTypeIds 的 static 块：字段名 -> "sheep"
  - EntityTypes 的 static 块：EntityTypeIds.SHEEP + invokedynamic(Sheep::new) -> 实体类
  - 实体类 static 块按顺序 defineId，索引 = 父类字段数 + 本类顺序

用法: scripts/gen-entity-layouts.py <server.jar> [输出文件]
"""
import os
import re
import subprocess
import sys
import tempfile
import zipfile

ENTITY_PKG = "net/minecraft/world/entity/"
ACCESSOR = "net/minecraft/network/syncher/EntityDataAccessor"


def javap(workdir, cls, verbose=False):
    args = ["javap", "-p", "-c", "-cp", workdir, cls.replace("/", ".")]
    if verbose:
        args.insert(1, "-v")
    return subprocess.run(args, capture_output=True, text=True).stdout


def static_block(out):
    parts = out.split("static {};", 1)
    return parts[1] if len(parts) == 2 else ""


def type_ids(workdir):
    """EntityTypeIds 字段名 -> 实体 ID。"""
    ids, pending = {}, None
    for line in static_block(javap(workdir, ENTITY_PKG + "EntityTypeIds")).splitlines():
        s = re.search(r"// String (\S+)$", line)
        if s:
            pending = s.group(1)
        p = re.search(r"putstatic\s+#\d+\s+// Field (\w+):", line)
        if p and pending:
            ids[p.group(1)] = pending
            pending = None
    return ids


def type_classes(workdir):
    """EntityTypeIds 字段名 -> 实体类（来自 invokedynamic 的构造器引用）。"""
    verbose = javap(workdir, ENTITY_PKG + "EntityTypes", verbose=True)
    # 常量池里的 MethodHandle：#123 = MethodHandle 8:#456 // REF_newInvokeSpecial a/b/C."<init>":...
    handles = {}
    for m in re.finditer(r"#(\d+) = MethodHandle\s+\S+\s+// REF_newInvokeSpecial ([\w/$]+)\.\"<init>\"", verbose):
        handles[m.group(1)] = m.group(2)
    # 静态工厂（例如 Mannequin::create）：取返回类型
    for m in re.finditer(r"#(\d+) = MethodHandle\s+\S+\s+// REF_invokeStatic [\w/$]+\.\w+:\([^)]*\)L(net/minecraft/world/entity/[\w/$]+);", verbose):
        handles[m.group(1)] = m.group(2)
    # BootstrapMethods：序号 -> 参数里的构造器
    bootstrap = {}
    bm = verbose.split("BootstrapMethods:", 1)[1] if "BootstrapMethods:" in verbose else ""
    current = None
    for line in bm.splitlines():
        h = re.match(r"\s+(\d+): #\d+ REF_invokeStatic", line)
        if h:
            current = h.group(1)
            continue
        a = re.match(r"\s+#(\d+) REF_newInvokeSpecial ([\w/$]+)\.\"<init>\"", line)
        if a and current is not None:
            bootstrap[current] = a.group(2)
        a2 = re.match(r"\s+#(\d+)\s", line)
        if a2 and current is not None and a2.group(1) in handles and current not in bootstrap:
            bootstrap[current] = handles[a2.group(1)]

    result, pending_id = {}, None
    for line in static_block(javap(workdir, ENTITY_PKG + "EntityTypes")).splitlines():
        g = re.search(r"getstatic\s+#\d+\s+// Field net/minecraft/world/entity/EntityTypeIds\.(\w+):", line)
        if g:
            pending_id = g.group(1)
            continue
        d = re.search(r"invokedynamic\s+#\d+,\s+0\s+// InvokeDynamic #(\d+):", line)
        if d and pending_id and pending_id not in result and d.group(1) in bootstrap:
            result[pending_id] = bootstrap[d.group(1)]
    return result


FLOAT_PUSH = re.compile(r"\s(?:ldc(?:_w)?\s+#\d+\s+// float ([\d.E-]+)f|fconst_(\d))")
DOUBLE_PUSH = re.compile(r"\s(?:ldc2_w\s+#\d+\s+// double ([\d.E-]+)d|dconst_(\d))")


def type_sizes(workdir):
    """EntityTypeIds 字段名 -> (宽, 高)。取 Builder.sized(FF) 前面的两个 float；没有调用时是默认 0.6x1.8。"""
    sizes, current, floats = {}, None, []
    for line in static_block(javap(workdir, ENTITY_PKG + "EntityTypes")).splitlines():
        g = re.search(r"getstatic\s+#\d+\s+// Field net/minecraft/world/entity/EntityTypeIds\.(\w+):", line)
        if g:
            current, floats = g.group(1), []
            sizes.setdefault(current, (0.6, 1.8))
            continue
        f = FLOAT_PUSH.search(line)
        if f:
            floats.append(float(f.group(1) if f.group(1) is not None else f.group(2)))
            continue
        if current and "EntityType$Builder.sized:(FF)" in line and len(floats) >= 2:
            sizes[current] = (floats[-2], floats[-1])
        if "invoke" in line:
            floats = []
    return sizes


def method_body(out, name):
    m = re.search(r"static \S+ " + re.escape(name) + r"\(\);\n\s+Code:\n(.*?)(?:\n\n|\n  [a-z])", out, re.S)
    return m.group(1) if m else ""


def max_health(workdir, cls, method, depth=0):
    """在 createAttributes 类方法里找 MAX_HEALTH；本方法没有就顺着父类 builder 往上找。"""
    if depth > 8:
        return None
    body = method_body(javap(workdir, cls), method)
    lines = body.splitlines()
    for i, line in enumerate(lines):
        if "Attributes.MAX_HEALTH" in line:
            for nxt in lines[i + 1:i + 3]:
                d = DOUBLE_PUSH.search(nxt)
                if d:
                    return float(d.group(1) if d.group(1) is not None else d.group(2))
    for line in lines:
        p = re.search(r"invokestatic\s+#\d+\s+// Method ([\w/$]+)\.(\w+):\(\)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier\$Builder;", line)
        if p:
            return max_health(workdir, p.group(1), p.group(2), depth + 1)
    return None


def type_health(workdir):
    """EntityTypes 字段名 -> 默认最大生命值（来自 DefaultAttributes 的映射）。"""
    result, pending = {}, None
    out = javap(workdir, ENTITY_PKG + "ai/attributes/DefaultAttributes")
    for line in static_block(out).splitlines():
        g = re.search(r"getstatic\s+#\d+\s+// Field net/minecraft/world/entity/EntityTypes\.(\w+):", line)
        if g:
            pending = g.group(1)
            continue
        m = re.search(r"invokestatic\s+#\d+\s+// Method ([\w/$]+)\.(\w+):\(\)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier\$Builder;", line)
        if m and pending:
            # LivingEntity 的默认最大生命值是 20
            result[pending] = max_health(workdir, m.group(1), m.group(2)) or 20.0
            pending = None
    return result


def main():
    jar = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else "plugin/src/main/resources/entity-layouts.tsv"
    traits_path = os.path.join(os.path.dirname(out_path), "entity-traits.tsv")
    with tempfile.TemporaryDirectory() as tmp:
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.startswith(ENTITY_PKG) and n.endswith(".class"):
                    z.extract(n, tmp)
        ids, classes = type_ids(tmp), type_classes(tmp)
        # 玩家没有工厂方法，手动指定
        player_field = next((f for f, k in ids.items() if k == "player"), None)
        if player_field:
            classes.setdefault(player_field, ENTITY_PKG + "player/Player")
        cache = {}

        def chain(cls):
            if cls in cache:
                return cache[cls]
            out = javap(tmp, cls)
            m = re.search(r"class \S+ extends ([\w.$]+)", out)
            parent = m.group(1).replace(".", "/") if m else None
            fields, serializer = [], None
            for line in static_block(out).splitlines():
                s = re.search(r"// Field net/minecraft/network/syncher/EntityDataSerializers\.(\w+)", line)
                if s:
                    serializer = s.group(1)
                p = re.search(r"putstatic\s+#\d+\s+// Field (\w+):L" + ACCESSOR, line)
                if p:
                    fields.append((cls.rsplit("/", 1)[-1] + "." + p.group(1), serializer or "?"))
                    serializer = None
            base = chain(parent) if parent and parent.startswith(ENTITY_PKG) else []
            cache[cls] = base + fields
            return cache[cls]

        rows = []
        for field, cls in sorted(classes.items(), key=lambda kv: ids.get(kv[0], kv[0])):
            key = ids.get(field)
            if not key:
                continue
            layout = chain(cls)
            if not layout:
                continue
            rows.append("minecraft:" + key + "\t" + "\t".join(f"{n}:{s}" for n, s in layout))

        sizes, health = type_sizes(tmp), type_health(tmp)
        traits = []
        for field, key in sorted(ids.items(), key=lambda kv: kv[1]):
            if field in health:
                w, h = sizes.get(field, (0.6, 1.8))
                traits.append(f"minecraft:{key}\t{w:g}\t{h:g}\t{health[field]:g}")

    version = os.path.basename(jar)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(f"# 由 scripts/gen-entity-layouts.py 从 {version} 生成，请勿手改\n")
        f.write("# 实体ID<TAB>字段0<TAB>字段1...（字段格式：类.字段名:序列化器，第 N 列即索引 N）\n")
        f.write("\n".join(rows) + "\n")
    with open(traits_path, "w", encoding="utf-8") as f:
        f.write(f"# 由 scripts/gen-entity-layouts.py 从 {version} 生成，请勿手改\n")
        f.write("# 实体ID<TAB>宽<TAB>高<TAB>默认最大生命值\n")
        f.write("\n".join(traits) + "\n")
    print(f"写入 {out_path}：{len(rows)} 种实体（映射 {len(classes)} / ID {len(ids)}）")
    print(f"写入 {traits_path}：{len(traits)} 种生物")


if __name__ == "__main__":
    main()
