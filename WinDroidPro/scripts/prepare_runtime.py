#!/usr/bin/env python3
"""Prepare a pinned Winlator library and relocate its guest ELF paths.

The replacement prefix MUST have identical length: changing ELF string lengths
would corrupt program headers, offsets and instructions. The rfs directory keeps
com.windroidpro's prefix the same length as upstream's com.winlator/rootfs prefix.
"""
import argparse
import copy
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import tarfile

import zstandard

PINNED_COMMIT = "3981d86efa4f333b2a34a7da8b6521476cd8c8b9"
OLD_PREFIX = b"/data/data/com.winlator/files/rootfs"
NEW_PREFIX = b"/data/data/com.windroidpro/files/rfs"
assert len(OLD_PREFIX) == len(NEW_PREFIX)


def relocate_bytes(data):
    return data.replace(OLD_PREFIX, NEW_PREFIX)


def relocate_archive(source, destination):
    replacements = 0
    with source.open("rb") as raw, destination.open("wb") as output:
        with zstandard.ZstdDecompressor().stream_reader(raw) as reader:
            with zstandard.ZstdCompressor(level=9).stream_writer(output) as writer:
                with tarfile.open(fileobj=reader, mode="r|") as src:
                    with tarfile.open(fileobj=writer, mode="w|", format=tarfile.GNU_FORMAT) as dst:
                        for original in src:
                            entry = copy.copy(original)
                            entry.linkname = entry.linkname.replace(OLD_PREFIX.decode(), NEW_PREFIX.decode())
                            if entry.isfile():
                                stream = src.extractfile(original)
                                data = stream.read()
                                replacements += data.count(OLD_PREFIX)
                                data = relocate_bytes(data)
                                dst.addfile(entry, io.BytesIO(data))
                            else:
                                dst.addfile(entry)
    return replacements


def replace_checked(path, old, new):
    text = path.read_text()
    if old not in text:
        raise RuntimeError(f"Pinned upstream patch no longer applies: {path}")
    path.write_text(text.replace(old, new))


def adapt_resource_switches(java_root):
    # Library R IDs are assigned by the consuming app and cannot be Java case
    # constants. Map them at runtime to stable local integers, preserving switch
    # fallthrough, break, return and default behavior without hardcoding R values.
    files = list(java_root.rglob("*.java"))
    identifiers = sorted({value for path in files for value in
                          re.findall(r"case R\.id\.(\w+)\s*:", path.read_text())})
    codes = {value: index for index, value in enumerate(identifiers)}
    cases = switches = 0
    for path in files:
        text = path.read_text()
        if "case R.id." not in text:
            continue
        text, count = re.subn(r"switch\s*\(([^\n]+)\)(\s*\{\s*case R\.id\.)",
                              r"switch (com.winlator.core.RuntimeMenuIds.resolve(\1))\2", text)
        switches += count
        text, count = re.subn(r"case R\.id\.(\w+)\s*:",
                              lambda match: f"case {codes[match[1]]}:", text)
        cases += count
        path.write_text(text)
    if (cases, switches) != (43, 9):
        raise RuntimeError(f"Resource switch adapter needs review: {cases} cases in {switches} switches")
    lines = ["package com.winlator.core;", "public final class RuntimeMenuIds {",
             "public static int resolve(int id) {"]
    lines += [f"if (id == com.winlator.R.id.{value}) return {code};" for value, code in codes.items()]
    lines += ["return -1;", "}", "}"]
    (java_root / "com/winlator/core/RuntimeMenuIds.java").write_text("\n".join(lines) + "\n")


def prepare(source, output):
    commit = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if commit != PINNED_COMMIT:
        raise RuntimeError(f"Expected upstream {PINNED_COMMIT}, found {commit}. Update the pin and review patches together.")
    subprocess.run(["git", "-C", str(source), "diff", "--quiet", "HEAD"], check=True)
    fingerprint = hashlib.sha256(Path(__file__).read_bytes() + commit.encode()).hexdigest()
    marker = output / "prepared.json"
    if marker.is_file() and json.loads(marker.read_text()).get("fingerprint") == fingerprint:
        print("Pinned runtime already prepared")
        return
    staging = output.with_name(output.name + ".staging")
    shutil.rmtree(staging, ignore_errors=True)
    staging.mkdir(parents=True)
    main = source / "app/src/main"
    for directory in ["java", "cpp", "res", "jniLibs", "assets"]:
        shutil.copytree(main / directory, staging / directory)
    # Use the consuming app's NDK C++ runtime, avoiding a duplicate older copy.
    (staging / "jniLibs/arm64-v8a/libc++_shared.so").unlink()

    # Native caches are compiled from source; guest binaries use equal-length
    # replacement above. Keep JNI class names unchanged in the library namespace.
    for path in (staging / "cpp").rglob("*"):
        if path.suffix in {".h", ".c", ".cpp"}:
            text = path.read_text()
            patched = text.replace(OLD_PREFIX.decode(), NEW_PREFIX.decode()).replace(
                "/data/data/com.winlator/cache", "/data/data/com.windroidpro/cache")
            if patched != text:
                path.write_text(patched)
    replace_checked(staging / "cpp/winlator/include/time_utils.h",
                    '#include <sys/time.h>', '#include <sys/time.h>\n#include <time.h>\n#include <stdint.h>')
    replace_checked(staging / "cpp/winlator/include/string_utils.h",
                    '#define WINLATOR_STRING_UTILS_H',
                    '#define WINLATOR_STRING_UTILS_H\n#include <string.h>\n#include <stdlib.h>')
    replace_checked(staging / "cpp/gladiorenderer/src/compressed_texture.c",
                    '#define STB_DXT_IMPLEMENTATION',
                    '#include <string.h>\n#define STB_DXT_IMPLEMENTATION')
    replace_checked(staging / "java/com/winlator/core/AppUtils.java",
                    "/data/data/com.winlator/storage", "/data/data/com.windroidpro/storage")
    # Runtime migration versions belong to upstream, independent of our app's
    # versionCode. The pinned engine uses 33 in its container metadata.
    replace_checked(staging / "java/com/winlator/core/AppUtils.java",
                    'return pInfo.versionCode;', 'return 33;')
    replace_checked(staging / "java/com/winlator/xenvironment/RootFS.java",
                    'new File(context.getFilesDir(), "rootfs")', 'new File(context.getFilesDir(), "rfs")')
    replace_checked(staging / "java/com/winlator/core/FileUtils.java",
                    '"com.winlator.FileProvider"', 'activity.getPackageName()+".runtime.fileprovider"')
    replace_checked(staging / "java/com/winlator/MainActivity.java",
                    'if (!requestAppPermissions()) RootFSInstaller.installIfNeeded(this);',
                    'RootFSInstaller.installIfNeeded(this);')
    replace_checked(staging / "java/com/winlator/SettingsFragment.java",
                    'resetPreferenceVersions(AppCompatActivity activity)',
                    'resetPreferenceVersions(Context activity)')
    adapt_resource_switches(staging / "java")
    # JNI callbacks are addressed by their original names, so consumer rules keep
    # them intact. Avoid the upstream launcher and its external-storage gate;
    # WinDroid Pro installs privately and imports through Android's document picker.
    provider = staging / "java/com/winlator/RuntimeFileProvider.java"
    provider.write_text("package com.winlator;\npublic class RuntimeFileProvider extends androidx.core.content.FileProvider {}\n")
    shutil.copyfile(staging / "res/xml/file_paths.xml", staging / "res/xml/runtime_file_paths.xml")
    # Upstream export/import settings write ':'-delimited extension names.
    replace_checked(staging / "java/com/winlator/xenvironment/components/VortekRendererComponent.java",
                    'exposedDeviceExtensions.split("\\\\|")', 'exposedDeviceExtensions.split(":")')
    assets_report = {}
    for path in sorted((staging / "assets").rglob("*.tzst")):
        temp = path.with_suffix(".relocated")
        count = relocate_archive(path, temp)
        temp.replace(path)
        assets_report[str(path.relative_to(staging / "assets"))] = count
    # Ship the runtime's license and build provenance in the actual APK.
    shutil.copyfile(source / "LICENSE", staging / "assets/WINLATOR-LICENSE.txt")
    for path in (main / "cpp").rglob("LICENSE"):
        destination = staging / "assets/licenses" / path.relative_to(main / "cpp")
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, destination)
    report = {"upstream": commit, "fingerprint": fingerprint, "oldPrefix": OLD_PREFIX.decode(),
              "newPrefix": NEW_PREFIX.decode(), "archiveReplacements": assets_report}
    (staging / "prepared.json").write_text(json.dumps(report, indent=2) + "\n")
    (staging / "assets/windroid-runtime.json").write_text(json.dumps(report, indent=2) + "\n")
    shutil.rmtree(output, ignore_errors=True)
    staging.rename(output)
    print(f"Prepared Winlator {commit[:12]}; relocated {sum(assets_report.values())} guest paths")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.source.resolve(), args.output.resolve())
