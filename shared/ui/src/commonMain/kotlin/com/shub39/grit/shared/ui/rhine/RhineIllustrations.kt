/*
 * Copyright (C) 2026  Shubham Gorai
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.shub39.grit.shared.ui.rhine

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shub39.grit.core.settings.RhineImageFit
import com.shub39.grit.core.tasks.IllustrationChange
import com.shub39.grit.core.tasks.RhineIllustrationRepo
import com.shub39.grit.shared.ui.components.glass.GlassButton
import com.shub39.grit.shared.ui.components.glass.GlassButtonStyle
import com.shub39.grit.shared.ui.theme.GlassShapes
import com.shub39.grit.shared.ui.theme.GlassTokens
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shub39.grit.core.tasks.IllustrationOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Illustration storage for the screens below; null where none is available. */
val LocalRhineIllustrationRepo = staticCompositionLocalOf<RhineIllustrationRepo?> { null }

/**
 * Draft for the illustration of [owner] [id] (0 for a new object), once its stored value is read;
 * null while reading or without storage. Picked-but-unsaved images are released when the draft
 * leaves composition, so call this where the editor lives.
 */
@Composable
fun rememberIllustrationDraft(owner: IllustrationOwner, id: Long): IllustrationDraft? {
    val repo = LocalRhineIllustrationRepo.current ?: return null
    // Saved with the screen: a gallery app may recreate the activity before the pick returns.
    var draft by
        rememberSaveable(owner, id, stateSaver = IllustrationDraft.Saver) {
            mutableStateOf<IllustrationDraft?>(null)
        }
    LaunchedEffect(owner, id, repo) {
        if (draft == null)
            draft = IllustrationDraft(if (id == 0L) null else repo.illustration(owner, id))
    }
    ReleaseOnClose(draft, repo)
    return draft
}

/**
 * Releases [draft]'s picked-but-unsaved images when its editor closes. Not when the whole screen is
 * destroyed: the draft is saved and comes back with the recreated screen, and the next app start
 * releases whatever is left over.
 */
@Composable
fun ReleaseOnClose(draft: IllustrationDraft?, repo: RhineIllustrationRepo?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(draft, repo) {
        onDispose {
            if (draft == null || repo == null) return@onDispose
            if (lifecycle.currentState == Lifecycle.State.DESTROYED) return@onDispose
            // The screen's scope is gone by now; releasing grants is quick and must still run.
            CoroutineScope(Dispatchers.Default).launch { draft.discard(repo) }
        }
    }
}

/** Decodes a picked image to at most [maxEdgePx] on its long edge; null when unreadable. */
fun interface RhineImageDecoder {
    suspend fun decode(uri: String, maxEdgePx: Int): ImageBitmap?
}

/** Platform decoder, or null where gallery images do not exist (JVM/Wasm demo). */
@Composable expect fun rememberRhineImageDecoder(): RhineImageDecoder?

/**
 * System photo picker (images only, no storage permission). Returns the launcher, or null when the
 * platform has no picker.
 */
@Composable expect fun rememberRhineImagePicker(onPicked: (String) -> Unit): (() -> Unit)?

/** Decode sizes, long edge in px. The smallest one at least 1.25× the target is used. */
val IllustrationBuckets = intArrayOf(384, 768, 1280)

fun illustrationBucket(targetPx: Float): Int =
    IllustrationBuckets.firstOrNull { it >= targetPx * 1.25f } ?: IllustrationBuckets.last()

sealed interface IllustrationImage {
    data object Missing : IllustrationImage

    data class Ready(val bitmap: ImageBitmap) : IllustrationImage
}

/**
 * Decoded illustrations by (uri, bucket), least recently used first out above [budgetBytes].
 * Readable from drawing code (snapshot state); unreadable images are remembered as missing until
 * [retry].
 */
@Stable
class RhineIllustrationCache(
    private val decoder: RhineImageDecoder?,
    private val budgetBytes: Long = 48L * 1024 * 1024,
) {
    private val images = mutableStateMapOf<String, IllustrationImage>()
    private val order = ArrayDeque<String>()
    private val loads = Semaphore(2)
    private var bytes = 0L

    private fun key(uri: String, bucket: Int) = "$bucket|$uri"

    operator fun get(uri: String, bucket: Int): IllustrationImage? = images[key(uri, bucket)]

    /** Best decoded version available for drawing: the requested bucket, else any other. */
    fun bitmap(uri: String, bucket: Int): ImageBitmap? =
        (images[key(uri, bucket)] as? IllustrationImage.Ready)?.bitmap
            ?: IllustrationBuckets.asList().firstNotNullOfOrNull {
                (images[key(uri, it)] as? IllustrationImage.Ready)?.bitmap
            }

    suspend fun load(uri: String, bucket: Int) {
        val key = key(uri, bucket)
        if (key in images) {
            order.remove(key)
            order.addLast(key)
            return
        }
        val decoder = decoder ?: return
        val bitmap = loads.withPermit { if (key in images) null else decoder.decode(uri, bucket) }
        if (key in images) return
        if (bitmap == null) {
            images[key] = IllustrationImage.Missing
            return
        }
        images[key] = IllustrationImage.Ready(bitmap)
        order.addLast(key)
        bytes += bitmap.width.toLong() * bitmap.height * 4
        while (bytes > budgetBytes && order.size > 1) {
            val old = order.removeFirst()
            (images.remove(old) as? IllustrationImage.Ready)?.let {
                bytes -= it.bitmap.width.toLong() * it.bitmap.height * 4
            }
        }
    }

    /** Forgets a missing mark so the next request reads the image again. */
    fun retry(uri: String) {
        IllustrationBuckets.forEach {
            val key = key(uri, it)
            if (images[key] == IllustrationImage.Missing) images.remove(key)
        }
    }

    fun clear() {
        images.clear()
        order.clear()
        bytes = 0L
    }
}

/**
 * Which blocks can show an illustration (docs/视觉优化/07): column starts (0), i.e. the Category on
 * the first layer and the Task on the second, every real item block (Tasks on the first layer;
 * Sections have none), and the raised block of an editor or of a create/delete transition.
 */
fun showsIllustration(slot: RhineSlot, raised: Boolean): Boolean =
    raised ||
        (slot.column != null &&
            (slot.kind == RhineBlockKind.Zero ||
                (slot.kind == RhineBlockKind.Real && slot.item != null)))

/**
 * Source and destination rectangles for an image of [imageSize] in [area] (face-local px), never
 * stretched. [RhineImageFit.CropTopStart] fills and crops from the top-left corner.
 */
fun illustrationRects(imageSize: IntSize, area: Rect, fit: RhineImageFit): Pair<Pair<IntOffset, IntSize>, Rect> {
    val iw = imageSize.width.toFloat().coerceAtLeast(1f)
    val ih = imageSize.height.toFloat().coerceAtLeast(1f)
    return when (fit) {
        RhineImageFit.Fit -> {
            val s = minOf(area.width / iw, area.height / ih)
            val w = iw * s
            val h = ih * s
            val dst =
                Rect(
                    area.left + (area.width - w) / 2f,
                    area.top + (area.height - h) / 2f,
                    area.left + (area.width + w) / 2f,
                    area.top + (area.height + h) / 2f,
                )
            (IntOffset.Zero to imageSize) to dst
        }
        RhineImageFit.CropTopStart,
        RhineImageFit.CropCenter -> {
            val s = maxOf(area.width / iw, area.height / ih)
            val sw = (area.width / s).toInt().coerceIn(1, imageSize.width)
            val sh = (area.height / s).toInt().coerceIn(1, imageSize.height)
            val origin =
                if (fit == RhineImageFit.CropTopStart) IntOffset.Zero
                else IntOffset((imageSize.width - sw) / 2, (imageSize.height - sh) / 2)
            (origin to IntSize(sw, sh)) to area
        }
    }
}

/**
 * Illustration chosen in an editor before saving. Every picked image is retained at once (so it
 * survives a restart once saved); [discard] releases the ones that end up unused.
 */
@Stable
class IllustrationDraft(
    val initial: String?,
    current: String? = initial,
    picked: List<String> = emptyList(),
) {
    var current by mutableStateOf(current)
        private set

    private val picked = picked.toMutableList()

    companion object {
        val Saver: Saver<IllustrationDraft?, Any> =
            listSaver<IllustrationDraft?, String?>(
                save = { draft -> draft?.let { listOf(it.initial, it.current) + it.picked } ?: emptyList() },
                restore = { saved ->
                    if (saved.size < 2) null
                    else IllustrationDraft(saved[0], saved[1], saved.drop(2).filterNotNull())
                },
            )
    }

    val change: IllustrationChange
        get() =
            when {
                current == initial -> IllustrationChange.Keep
                current == null -> IllustrationChange.Remove
                else -> IllustrationChange.Set(current!!)
            }

    fun pick(uri: String) {
        picked += uri
        current = uri
    }

    fun remove() {
        current = null
    }

    /** After a save or cancel: release what was picked but is not (or no longer) stored. */
    suspend fun discard(repo: RhineIllustrationRepo) {
        picked.forEach { repo.releaseIfUnused(it) }
        picked.clear()
    }
}

/**
 * Editor row: thumbnail plus pick / replace / remove. Unreadable images (deleted or revoked) say so
 * and can be replaced or removed.
 */
@Composable
fun IllustrationField(
    draft: IllustrationDraft,
    repo: RhineIllustrationRepo?,
    enabled: Boolean = true,
) {
    val decoder = rememberRhineImageDecoder()
    val scope = rememberCoroutineScope()
    var error by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    val pick =
        rememberRhineImagePicker { uri ->
            scope.launch {
                if (repo?.retain(uri) == true) {
                    error = null
                    draft.pick(uri)
                } else error = "无法保持访问这张图片，请选择其他图片"
            }
        }
    if (pick == null || repo == null) return
    val uri = draft.current
    val thumbnail by
        produceState<IllustrationImage?>(null, uri, decoder) {
            value =
                if (uri == null) null
                else
                    decoder?.decode(uri, IllustrationBuckets.first())?.let {
                        IllustrationImage.Ready(it)
                    } ?: IllustrationImage.Missing
        }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("插图", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(72.dp, 48.dp)
                    .clip(GlassShapes.Card)
                    .background(GlassTokens.Shadow.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                when (val image = thumbnail) {
                    is IllustrationImage.Ready ->
                        Image(
                            image.bitmap,
                            contentDescription = "插图",
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopStart,
                            modifier = Modifier.size(72.dp, 48.dp),
                        )
                    IllustrationImage.Missing ->
                        Text("不可用", style = MaterialTheme.typography.labelSmall)
                    null -> if (uri == null) Text("无", style = MaterialTheme.typography.labelSmall)
                }
            }
            GlassButton(onClick = pick, enabled = enabled) {
                Text(if (uri == null) "选择图片" else "更换")
            }
            if (uri != null)
                GlassButton(onClick = { draft.remove() }, enabled = enabled, style = GlassButtonStyle.Plain) {
                    Text("移除")
                }
        }
        val missing = thumbnail == IllustrationImage.Missing
        (error ?: if (missing) "图片已删除或无法读取，可重新选择或移除" else "插图仅保存在本机，不随备份导出").let {
            Text(
                it,
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (error != null || missing) MaterialTheme.colorScheme.error else GlassTokens.SecondaryText,
            )
        }
    }
}
