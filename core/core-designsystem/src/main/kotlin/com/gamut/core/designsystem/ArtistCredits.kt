package com.gamut.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.CaretRight
import kotlinx.coroutines.launch

private val SPLIT = Regex("(?i)\\s+(featuring|feat\\.?|ft\\.?|with|x|&|and)\\s+|\\s*,\\s*|\\s*/\\s*|\\s*;\\s*")

private val KEEP_TOGETHER = listOf(
    "tyler, the creator", "earth, wind & fire", "crosby, stills & nash", "crosby, stills, nash & young",
    "simon & garfunkel", "hall & oates", "salt-n-pepa", "florence + the machine", "years & years",
    "mumford & sons", "of monsters and men", "king gizzard & the lizard wizard", "belle & sebastian",
    "brooks & dunn", "ac/dc", "kc and the sunshine band", "bell biv devoe", "dan + shay", "coheed and cambria",
    "panic! at the disco", "iron & wine", "matt and kim", "me first and the gimme gimmes",
)

fun splitArtists(credit: String): List<String> {
    val trimmed = credit.trim()
    if (trimmed.isEmpty()) return emptyList()
    if (KEEP_TOGETHER.any { it.equals(trimmed, ignoreCase = true) }) return listOf(trimmed)
    val unwrapped = trimmed.replace(Regex("[(\\[]"), " feat. ").replace(Regex("[)\\]]"), "")
    return unwrapped.split(SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .ifEmpty { listOf(trimmed) }
}

val LocalArtistPhotoResolver = compositionLocalOf<suspend (name: String, albumTitle: String?) -> String?> { { _, _ -> null } }

@Composable
fun rememberArtistPhotos(names: List<String>, albumTitle: String?): Map<String, String?> {
    val resolver = LocalArtistPhotoResolver.current
    var photos by remember(names) { mutableStateOf<Map<String, String?>>(emptyMap()) }
    LaunchedEffect(names, albumTitle) {
        names.forEach { name ->
            if (name !in photos) {
                val url = runCatching { resolver(name, albumTitle) }.getOrNull()
                photos = photos + (name to url)
            }
        }
    }
    return photos
}

@Composable
fun ArtistStack(
    names: List<String>,
    photos: Map<String, String?>,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    overlap: Dp = 10.dp,
    maxShown: Int = 3,
    onClick: (() -> Unit)? = null,
) {
    val shown = names.take(maxShown)
    val extra = names.size - shown.size
    val discs = shown.size + if (extra > 0) 1 else 0
    val step = size - overlap
    val width = size + step * (discs - 1).coerceAtLeast(0)
    Box(
        modifier
            .size(width = width, height = size)
            .let { if (onClick != null) it.pressScale(onClick = onClick, scaleDown = 0.94f) else it },
    ) {
        for (i in discs - 1 downTo 0) {
            val isMore = i >= shown.size
            val fade = 1f - 0.22f * i
            Box(
                Modifier
                    .offset(x = step * i)
                    .size(size)
                    .clip(CircleShape)
                    .border(BorderStroke(1.5.dp, ink(0.18f)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isMore) {
                    Box(Modifier.size(size).background(ink(0.16f)), contentAlignment = Alignment.Center) {
                        Text("+$extra", style = MaterialTheme.typography.labelSmall, color = ink(0.9f), maxLines = 1)
                    }
                } else {
                    val name = shown[i]
                    val url = photos[name]
                    if (url != null) {
                        ArtImage(
                            url = url,
                            modifier = Modifier.size(size).alpha(fade),
                            contentDescription = name,
                            fallback = { Initial(name, size) },
                        )
                    } else {
                        Box(Modifier.size(size).alpha(fade)) { Initial(name, size) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Initial(name: String, size: Dp) {
    Box(Modifier.size(size).background(ink(0.14f)), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ink(0.85f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPickerSheet(
    names: List<String>,
    albumTitle: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val photos = rememberArtistPhotos(names, albumTitle)

    GamutSheet(
        onDismiss = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle(color = ink(0.3f)) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                "Artists on this song",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = ink(),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
            LazyColumn {
                items(names, key = { it }) { name ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressScale(
                                onClick = {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        onDismiss()
                                        onPick(name)
                                    }
                                },
                                scaleDown = 0.98f,
                            )
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val url = photos[name]
                        Box(Modifier.size(48.dp).clip(CircleShape).border(1.dp, ink(0.12f), CircleShape)) {
                            if (url != null) {
                                ArtImage(url = url, modifier = Modifier.size(48.dp), contentDescription = name, fallback = { Initial(name, 48.dp) })
                            } else {
                                Initial(name, 48.dp)
                            }
                        }
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            color = ink(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(PhosphorIcons.Bold.CaretRight, null, tint = ink(0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

class ArtistTap internal constructor(val onClick: () -> Unit, internal val sheet: @Composable () -> Unit) {
    @Composable fun Sheet() = sheet()
}

@Composable
fun rememberArtistTap(
    credit: String,
    albumTitle: String?,
    onArtist: (String) -> Unit,
): ArtistTap {
    var open by remember { mutableStateOf(false) }
    val names = remember(credit) { splitArtists(credit) }
    val currentOnArtist by androidx.compose.runtime.rememberUpdatedState(onArtist)
    return remember(names, albumTitle, open) {
        ArtistTap(
            onClick = {
                when {
                    names.isEmpty() -> Unit
                    names.size == 1 -> currentOnArtist(names[0])
                    else -> open = true
                }
            },
            sheet = {
                if (open) ArtistPickerSheet(names, albumTitle, onPick = { currentOnArtist(it) }, onDismiss = { open = false })
            },
        )
    }
}

@Composable
fun ArtistLine(
    credit: String,
    albumTitle: String?,
    onArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
    stackSize: Dp = 18.dp,
    showSingle: Boolean = false,
    singlePhoto: String? = null,
    label: @Composable (Modifier) -> Unit,
) {
    val names = remember(credit) { splitArtists(credit) }
    val multi = names.size > 1
    val tap = rememberArtistTap(credit, albumTitle, onArtist)
    val resolved = rememberArtistPhotos(if (multi) names else emptyList(), albumTitle)
    val photos = when {
        multi -> resolved
        showSingle && names.isNotEmpty() -> mapOf(names[0] to singlePhoto)
        else -> emptyMap()
    }
    Row(
        modifier.pressScale(onClick = tap.onClick, scaleDown = 0.97f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (multi || (showSingle && names.isNotEmpty())) {
            ArtistStack(names, photos, size = stackSize, overlap = stackSize * 0.35f)
            Box(Modifier.width(8.dp))
        }
        label(Modifier.weight(1f, fill = false))
    }
    tap.Sheet()
}
