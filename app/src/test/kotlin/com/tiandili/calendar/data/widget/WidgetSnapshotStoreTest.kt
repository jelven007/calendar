package com.tiandili.calendar.data.widget

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import com.tiandili.calendar.domain.widget.WidgetSnapshot
import com.tiandili.calendar.domain.widget.WidgetScheduleSummary
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetSnapshotStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val moshi = Moshi.Builder().build()
    private fun snapshot() = WidgetSnapshot(1, "2025-02-02", "星期日", "正月初五",
        null, "出行", "动土", nextSchedule = WidgetScheduleSummary(3, "看牙", 2, "今天 10:00"))

    @Test fun `snapshot survives a new store instance`() {
        val file = File(folder.root, "widget.json")
        FileWidgetSnapshotStore(file, moshi).write(snapshot())
        assertThat(FileWidgetSnapshotStore(file, moshi).read()).isEqualTo(snapshot())
    }

    @Test fun `missing and damaged cache return null`() {
        val file = File(folder.root, "widget.json")
        val store = FileWidgetSnapshotStore(file, moshi)
        assertThat(store.read()).isNull()
        file.writeText("{broken")
        assertThat(store.read()).isNull()
        file.writeText(" ".repeat(65_537))
        assertThat(store.read()).isNull()
    }

    @Test fun `overwriting private snapshot removes all old title data`() {
        val file = File(folder.root, "widget.json")
        val store = FileWidgetSnapshotStore(file, moshi)
        store.write(snapshot())
        store.write(snapshot().copy(nextSchedule = null))
        assertThat(store.read()?.nextSchedule).isNull()
        assertThat(file.readText()).doesNotContain("看牙")
    }
}
