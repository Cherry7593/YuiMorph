#!/usr/bin/env python3
"""生成实体元数据布局表（每次升级 MC 版本后重新运行）。

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


def main():
    jar = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else "plugin/src/main/resources/entity-layouts.tsv"
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

    version = os.path.basename(jar)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(f"# 由 scripts/gen-entity-layouts.py 从 {version} 生成，请勿手改\n")
        f.write("# 实体ID<TAB>字段0<TAB>字段1...（字段格式：类.字段名:序列化器，第 N 列即索引 N）\n")
        f.write("\n".join(rows) + "\n")
    print(f"写入 {out_path}：{len(rows)} 种实体（映射 {len(classes)} / ID {len(ids)}）")


if __name__ == "__main__":
    main()
