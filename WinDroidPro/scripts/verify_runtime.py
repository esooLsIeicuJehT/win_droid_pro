#!/usr/bin/env python3
"""Validate the runtime actually packaged in an APK, including ELF interpreters."""
import argparse
import io
import json
from pathlib import Path
import posixpath
import struct
import tarfile
import zipfile

import zstandard
from prepare_runtime import NEW_PREFIX, OLD_PREFIX, PINNED_COMMIT, application_prefix


def interpreter(data):
    if not data.startswith(b"\x7fELF\x02\x01") or len(data) < 64:
        return None
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    for index in range(count):
        start = offset + size * index
        if start + 56 > len(data):
            raise ValueError("Truncated ELF program header")
        kind, _, file_offset, _, _, file_size, _, _ = struct.unpack_from("<IIQQQQQQ", data, start)
        if kind == 3:
            return data[file_offset:file_offset + file_size].rstrip(b"\0")
    return None


def resolve_archive_path(name, links, new_prefix=NEW_PREFIX):
    for _ in range(40):
        parts = name.split("/")
        for length in range(1, len(parts) + 1):
            prefix = "/".join(parts[:length])
            if prefix not in links:
                continue
            target = links[prefix]
            if target.startswith(new_prefix.decode() + "/"):
                target = target[len(new_prefix) + 1:]
            elif target.startswith("/"):
                target = target.lstrip("/")
            else:
                target = posixpath.join(posixpath.dirname(prefix), target)
            name = posixpath.normpath(posixpath.join(target, *parts[length:]))
            break
        else:
            return name
    raise ValueError("Symlink cycle in guest ELF interpreter path")


def verify(apk):
    totals = {"archives": 0, "files": 0, "elfInterpreters": 0, "extractedBytes": 0}
    with zipfile.ZipFile(apk) as package:
        report = json.loads(package.read("assets/windroid-runtime.json"))
        assert report["upstream"] == PINNED_COMMIT, "Unexpected engine version"
        new_prefix = application_prefix(report.get("applicationId", "com.windroidpro"))
        assert report["newPrefix"] == new_prefix.decode(), "Incorrect app directory"
        assert package.getinfo("assets/WINLATOR-LICENSE.txt").file_size > 20000
        for library in ["winlator", "vortekrenderer", "gladiorenderer", "virglrenderer", "midihandler", "windroidpro"]:
            assert package.getinfo(f"lib/arm64-v8a/lib{library}.so").file_size > 0, library
        assert not any(name.startswith("lib/") and not name.startswith("lib/arm64-v8a/") for name in package.namelist()), "Unexpected ABI"
        assert not any(name.endswith(".tar.xz") and package.getinfo(name).file_size == 0 for name in package.namelist()), "Placeholder asset"
        rootfs_files, required_interpreters, rootfs_links = set(), set(), {}
        for name in package.namelist():
            if not name.startswith("assets/") or not name.endswith(".tzst"):
                continue
            assert package.getinfo(name).file_size > 0, name
            totals["archives"] += 1
            with package.open(name) as stream, zstandard.ZstdDecompressor().stream_reader(stream) as reader:
                with tarfile.open(fileobj=reader, mode="r|") as archive:
                    for member in archive:
                        assert OLD_PREFIX.decode() not in member.linkname, f"Unrelocated link: {member.name}"
                        if name == "assets/rootfs.tzst":
                            rootfs_files.add(member.name.removeprefix("./"))
                            if member.issym():
                                rootfs_links[member.name.removeprefix("./")] = member.linkname
                        if not member.isfile():
                            continue
                        data = archive.extractfile(member).read()
                        totals["files"] += 1
                        totals["extractedBytes"] += len(data)
                        assert OLD_PREFIX not in data, f"Unrelocated guest path in {name}:{member.name}"
                        value = interpreter(data)
                        if value:
                            totals["elfInterpreters"] += 1
                            if value.startswith(new_prefix + b"/"):
                                required_interpreters.add(value[len(new_prefix) + 1:].decode())
        for name in ["opt/wine/bin/wine", "usr/lib/libc.so.6", "usr/lib/ld-linux-aarch64.so.1"]:
            assert name in rootfs_files, f"Missing runtime file: {name}"
        resolved = {resolve_archive_path(name, rootfs_links, new_prefix) for name in required_interpreters}
        assert resolved <= rootfs_files, f"Missing ELF loaders: {resolved - rootfs_files}"
        for name in ["assets/box64/box64-0.4.4.tzst", "assets/container_pattern.tzst", "assets/graphics_driver/vortek-2.1.tzst"]:
            assert package.getinfo(name).file_size > 0, name
        assert totals["elfInterpreters"] > 10, "Guest executable coverage too small"
    print(json.dumps(totals, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    verify(parser.parse_args().apk)
