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
        ]
        for name in names:
            self.assertIn("L" + name.replace(".", "/") + ";", self.classes)

    def test_apk_contains_the_current_complete_notice_file(self):
        with zipfile.ZipFile(APK) as archive:
            actual = archive.read("assets/open_source_licenses.txt")
        expected = (ROOT / "app/src/main/assets/open_source_licenses.txt").read_bytes()
        self.assertTrue(actual == expected, "APK licenses differ from the current reviewed notices")

    def test_release_does_not_ship_the_compose_development_preview_activity(self):
        self.assertFalse("Landroidx/compose/ui/tooling/PreviewActivity;" in self.classes,
                         "Release APK still contains the development preview activity")


if __name__ == "__main__":
    unittest.main()
