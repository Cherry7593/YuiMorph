#!/usr/bin/env python3
"""向本地测试服发送 RCON 命令。密码和端口从测试服 server.properties 读取。

用法: scripts/rcon.py "list" ["yuimorph reload" ...]
环境变量 YUIMORPH_SERVER_DIR 可覆盖测试服目录。
"""
import os
import socket
import struct
import sys

SERVER_DIR = os.environ.get("YUIMORPH_SERVER_DIR", "/Users/yui/Workspace/servers/mc-server")


def load_props():
    props = {}
    with open(os.path.join(SERVER_DIR, "server.properties"), encoding="utf-8") as f:
        for line in f:
            if "=" in line and not line.startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                props[k] = v
    return props


def packet(req_id, kind, body):
    data = struct.pack("<ii", req_id, kind) + body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(data)) + data


def recv(sock):
    raw_len = sock.recv(4)
    (length,) = struct.unpack("<i", raw_len)
    data = b""
    while len(data) < length:
        data += sock.recv(length - len(data))
    req_id, _ = struct.unpack("<ii", data[:8])
    return req_id, data[8:-2].decode("utf-8", errors="replace")


def main():
    props = load_props()
    with socket.create_connection(("127.0.0.1", int(props.get("rcon.port", "25575"))), timeout=10) as s:
        s.sendall(packet(1, 3, props["rcon.password"]))
        if recv(s)[0] == -1:
            sys.exit("RCON 认证失败")
        for cmd in sys.argv[1:]:
            s.sendall(packet(2, 2, cmd))
            print(recv(s)[1])


if __name__ == "__main__":
    main()
