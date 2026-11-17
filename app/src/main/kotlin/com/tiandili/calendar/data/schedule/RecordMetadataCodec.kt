package com.tiandili.calendar.data.schedule

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.tiandili.calendar.domain.schedule.ChecklistItem

internal object RecordMetadataCodec {
    private val moshi = Moshi.Builder().build()
    private val checklistAdapter = moshi.adapter<List<ChecklistItem>>(
        Types.newParameterizedType(List::class.java, ChecklistItem::class.java))
    private val mediaAdapter = moshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java))

    fun encodeChecklist(items: List<ChecklistItem>): String = checklistAdapter.toJson(items)
    fun decodeChecklist(json: String): List<ChecklistItem> = checklistAdapter.fromJson(json).orEmpty()
    fun encodeMedia(items: List<String>): String = mediaAdapter.toJson(items)
    fun decodeMedia(json: String): List<String> = mediaAdapter.fromJson(json).orEmpty()
}
