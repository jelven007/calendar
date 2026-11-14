package com.tiandili.calendar.data.widget

import com.squareup.moshi.Moshi
import com.tiandili.calendar.domain.widget.WidgetSnapshot
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.time.LocalDate

class FileWidgetSnapshotStore(private val file: File, moshi: Moshi) {
    private val adapter = moshi.adapter(WidgetSnapshot::class.java)

    @Synchronized
    fun read(): WidgetSnapshot? = runCatching {
        if (!file.isFile || file.length() > MAX_BYTES) return null
        adapter.fromJson(file.readText())?.also { LocalDate.parse(it.date) }
    }.getOrNull()

    @Synchronized
    fun write(snapshot: WidgetSnapshot) {
        val bytes = adapter.toJson(snapshot).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            FileOutputStream(temporary).use {
                it.write(bytes)
                it.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private companion object { const val MAX_BYTES = 65_536 }
}
