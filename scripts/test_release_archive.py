"""Verify reflection and persisted-data contracts in the actual minified APK."""

from pathlib import Path
import struct
import unittest
import zipfile


ROOT = Path(__file__).resolve().parent.parent
APK = ROOT / "app/build/outputs/apk/release/app-release.apk"


def dex_classes_and_fields(data):
    def uleb(position):
        value = 0
        shift = 0
        while True:
            byte = data[position]
            position += 1
            value |= (byte & 0x7f) << shift
            if not byte & 0x80:
                return value, position
            shift += 7

    def section(offset):
        return struct.unpack_from("<II", data, offset)

    string_count, string_offset = section(0x38)
    strings = []
    for index in range(string_count):
        position = struct.unpack_from("<I", data, string_offset + index * 4)[0]
        _, position = uleb(position)
        end = data.index(b"\0", position)
        strings.append(data[position:end].decode("utf-8", errors="replace"))
    type_count, type_offset = section(0x40)
    types = [strings[struct.unpack_from("<I", data, type_offset + index * 4)[0]]
             for index in range(type_count)]
    field_count, field_offset = section(0x50)
    fields = [struct.unpack_from("<HHI", data, field_offset + index * 8)
              for index in range(field_count)]
    class_count, class_offset = section(0x60)
    classes = {}
    for index in range(class_count):
        definition = struct.unpack_from("<8I", data, class_offset + index * 32)
        members = set()
        position = definition[6]
        if position:
            static_count, position = uleb(position)
            instance_count, position = uleb(position)
            _, position = uleb(position)
            _, position = uleb(position)
            for count in (static_count, instance_count):
                field_index = 0
                for _ in range(count):
                    difference, position = uleb(position)
                    _, position = uleb(position)
                    field_index += difference
                    members.add(strings[fields[field_index][2]])
        classes[types[definition[0]]] = members
    return classes


class ReleaseArchiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.classes = {}
        with zipfile.ZipFile(APK) as archive:
            for entry in archive.namelist():
                if entry.startswith("classes") and entry.endswith(".dex"):
                    cls.classes.update(dex_classes_and_fields(archive.read(entry)))

    def test_cache_freshness_enum_retains_fields_required_by_moshi_reflection(self):
        mapping = (ROOT / "app/build/outputs/mapping/release/mapping.txt").read_text()
        original = "com.tiandili.calendar.domain.weather.CacheFreshness"
        line = next(line for line in mapping.splitlines()
                    if line.startswith(original + " -> "))
        renamed = line.split(" -> ", 1)[1].removesuffix(":")
        descriptor = "L" + renamed.replace(".", "/") + ";"
        self.assertTrue({"FRESH", "STALE", "EXPIRED"} <= self.classes[descriptor],
                        f"Moshi needs named enum fields; APK contains {self.classes[descriptor]}")

    def test_room_workers_and_generated_widget_adapters_are_present_for_reflection(self):
        names = [
            "com.tiandili.calendar.data.local.TiandiliDatabase_Impl",
            "com.tiandili.calendar.platform.background.WeatherRefreshWorker",
            "com.tiandili.calendar.platform.background.ReminderCompensationWorker",
            "com.tiandili.calendar.platform.background.WidgetRefreshWorker",
            "com.tiandili.calendar.domain.widget.WidgetSnapshotJsonAdapter",
            "com.tiandili.calendar.domain.widget.WidgetWeatherSummaryJsonAdapter",
            "com.tiandili.calendar.domain.widget.WidgetScheduleSummaryJsonAdapter",
            "com.tiandili.calendar.domain.schedule.ChecklistItemJsonAdapter",
            "com.tiandili.calendar.feature.settings.ReleaseMetadataJsonAdapter",
            "com.tiandili.calendar.platform.alarm.AlarmSoundService",
            "com.tiandili.calendar.platform.alarm.AlarmRingingActivity",
        ]
        for name in names:
            self.assertTrue("L" + name.replace(".", "/") + ";" in self.classes,
                            f"Missing runtime contract: {name}")

    def test_apk_contains_the_current_complete_notice_file(self):
        with zipfile.ZipFile(APK) as archive:
            actual = archive.read("assets/open_source_licenses.txt")
        expected = (ROOT / "app/src/main/assets/open_source_licenses.txt").read_bytes()
        self.assertTrue(actual == expected, "APK licenses differ from the current reviewed notices")

    def test_release_does_not_ship_the_compose_development_preview_activity(self):
        self.assertFalse("Landroidx/compose/ui/tooling/PreviewActivity;" in self.classes,
                         "Release APK still contains the development preview activity")

    def test_universal_apk_supports_four_abis_and_android_8_dex(self):
        with zipfile.ZipFile(APK) as archive:
            abis = {name.split("/")[1] for name in archive.namelist() if name.startswith("lib/")}
            self.assertEqual(abis, {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"})
            dex_files = [name for name in archive.namelist()
                         if name.startswith("classes") and name.endswith(".dex")]
            self.assertTrue(dex_files)
            for name in dex_files:
                self.assertEqual(archive.read(name)[:8], b"dex\n038\x00",
                                 "Android 8 must be able to load every DEX")

    def test_every_native_load_segment_is_aligned_for_16kb_pages(self):
        with zipfile.ZipFile(APK) as archive:
            for name in archive.namelist():
                if not name.startswith("lib/") or not name.endswith(".so"):
                    continue
                data = archive.read(name)
                self.assertEqual(data[:4], b"\x7fELF")
                self.assertEqual(data[5], 1, "Expected little-endian ELF")
                if data[4] == 2:
                    phoff = struct.unpack_from("<Q", data, 32)[0]
                    entsize, count = struct.unpack_from("<HH", data, 54)
                    alignment_offset, alignment_format = 48, "<Q"
                else:
                    phoff = struct.unpack_from("<I", data, 28)[0]
                    entsize, count = struct.unpack_from("<HH", data, 42)
                    alignment_offset, alignment_format = 28, "<I"
                load_segments = 0
                for index in range(count):
                    entry = phoff + index * entsize
                    if struct.unpack_from("<I", data, entry)[0] == 1:
                        alignment = struct.unpack_from(alignment_format, data, entry + alignment_offset)[0]
                        self.assertGreaterEqual(alignment, 16384, name)
                        self.assertEqual(alignment % 16384, 0, name)
                        load_segments += 1
                self.assertGreater(load_segments, 0, name)


if __name__ == "__main__":
    unittest.main()
