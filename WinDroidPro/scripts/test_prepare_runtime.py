import io
from pathlib import Path
import struct
import tarfile
import tempfile
import unittest

import zstandard
from prepare_runtime import NEW_PREFIX, OLD_PREFIX, relocate_archive, relocate_bytes
from verify_runtime import resolve_archive_path


class RuntimeRelocationTest(unittest.TestCase):
    def test_guest_loader_lookup_follows_directory_symlinks(self):
        self.assertEqual("usr/lib/ld-linux-aarch64.so.1", resolve_archive_path(
            "lib/ld-linux-aarch64.so.1", {"lib": "usr/lib"}))
        with self.assertRaises(ValueError):
            resolve_archive_path("lib/loader", {"lib": "lib"})

    def test_elf_interpreter_offsets_remain_valid(self):
        # ELF64 little-endian header and PT_INTERP with a guest absolute path.
        interpreter = OLD_PREFIX + b"/usr/lib/ld-linux-aarch64.so.1\0"
        header = struct.pack("<16sHHIQQQIHHHHHH", b"\x7fELF\x02\x01\x01" + b"\0" * 9,
                             3, 183, 1, 0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
        program = struct.pack("<IIQQQQQQ", 3, 4, 120, 120, 120,
                              len(interpreter), len(interpreter), 1)
        original = header + program + interpreter + bytes(range(256))
        patched = relocate_bytes(original)
        self.assertEqual(len(original), len(patched))
        self.assertEqual(original[:120], patched[:120])
        self.assertEqual(NEW_PREFIX + b"/usr/lib/ld-linux-aarch64.so.1\0", patched[120:120 + len(interpreter)])
        self.assertEqual(original[120 + len(interpreter):], patched[120 + len(interpreter):])

    def test_tar_permissions_symlinks_and_content_survive(self):
        with tempfile.TemporaryDirectory() as directory:
            source, destination = Path(directory) / "in.tzst", Path(directory) / "out.tzst"
            raw = io.BytesIO()
            with tarfile.open(fileobj=raw, mode="w") as archive:
                regular = tarfile.TarInfo("usr/bin/guest")
                payload = b"\x7fELF\0" + OLD_PREFIX + b"/usr/lib/libc.so.6\0"
                regular.size, regular.mode, regular.mtime = len(payload), 0o755, 12345
                archive.addfile(regular, io.BytesIO(payload))
                link = tarfile.TarInfo("usr/bin/link")
                link.type, link.linkname = tarfile.SYMTYPE, OLD_PREFIX.decode() + "/usr/bin/guest"
                archive.addfile(link)
            source.write_bytes(zstandard.ZstdCompressor().compress(raw.getvalue()))
            self.assertEqual(1, relocate_archive(source, destination))
            with destination.open("rb") as file, zstandard.ZstdDecompressor().stream_reader(file) as reader:
                with tarfile.open(fileobj=reader, mode="r|") as archive:
                    regular = archive.next()
                    self.assertEqual((0o755, 12345, len(payload)), (regular.mode, regular.mtime, regular.size))
                    self.assertEqual(relocate_bytes(payload), archive.extractfile(regular).read())
                    link = archive.next()
                    self.assertTrue(link.issym())
                    self.assertEqual(NEW_PREFIX.decode() + "/usr/bin/guest", link.linkname)


if __name__ == "__main__":
    unittest.main()
