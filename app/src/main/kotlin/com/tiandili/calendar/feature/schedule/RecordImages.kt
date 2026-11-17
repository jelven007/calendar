package com.tiandili.calendar.feature.schedule

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
fun RecordImages(uris: List<String>, onRemove: ((Int) -> Unit)? = null, onReselect: ((Int) -> Unit)? = null) {
    var viewing by remember { mutableStateOf<Int?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        uris.forEachIndexed { index, uri ->
            Column(Modifier.width(102.dp)) {
                RecordImage(uri, 256, Modifier.size(102.dp).clickable { viewing = index },
                    "图片 ${index + 1}", { onReselect?.invoke(index) })
                onRemove?.let { TextButton(onClick = { it(index) }) { Text("移除") } }
            }
        }
    }
    viewing?.let { index ->
        uris.getOrNull(index)?.let { uri ->
            AlertDialog(onDismissRequest = { viewing = null },
                title = { Text("图片 ${index + 1} / ${uris.size}") },
                text = { RecordImage(uri, 1600, Modifier.fillMaxWidth().height(340.dp),
                    "浏览图片 ${index + 1}", { viewing = null; onReselect?.invoke(index) }) },
                confirmButton = { TextButton(onClick = { viewing = null }) { Text("关闭") } })
        }
    }
}

@Composable
private fun RecordImage(uri: String, maxSize: Int, modifier: Modifier, description: String, onReselect: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var loading by remember(uri) { mutableStateOf(true) }
    val image by produceState<Bitmap?>(null, uri, maxSize) {
        value = withContext(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > maxSize) {
                    options.inSampleSize *= 2
                }
                resolver.openInputStream(uri.toUri())?.use { BitmapFactory.decodeStream(it, null, options) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) { null }
        }
        loading = false
    }
    if (image != null) {
        Image(image!!.asImageBitmap(), description, modifier, contentScale = ContentScale.Fit)
    } else {
        Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(if (loading) "读取中…" else "图片不可读", style = MaterialTheme.typography.labelMedium)
                if (!loading) TextButton(onClick = onReselect) { Text("重新选择") }
            }
        }
    }
}
