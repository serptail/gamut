package com.gamut.core.designsystem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.DotsThree
import com.adamglin.phosphoricons.bold.DownloadSimple
import com.adamglin.phosphoricons.bold.Image as ImageIcon
import com.adamglin.phosphoricons.bold.Link
import com.adamglin.phosphoricons.bold.LinkSimpleHorizontal
import com.adamglin.phosphoricons.bold.MusicNote
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CardW = 280.dp
private val CardH = 370.dp

private val PriorityPackages = listOf(
    "com.whatsapp",
    "org.telegram.messenger",
    "com.instagram.android",
    "com.facebook.orca",
    "com.snapchat.android",
    "com.google.android.apps.messaging",
    "com.twitter.android",
    "com.discord",
    "com.google.android.gm",
)

private class ShareItem(val title: String, val artist: String, val artUrl: String?)

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

private fun Modifier.bounceClick(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce",
    )
    val tint by animateColorAsState(
        targetValue = ink().copy(
            alpha = if (pressed) {
                0.10f
            } else if (hovered) {
                0.06f
            } else {
                0f
            },
        ),
        animationSpec = tween(140),
        label = "tint",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(RoundedCornerShape(16.dp))
        .background(tint)
        .hoverable(source)
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

private class ShareApp(val label: String, val component: ComponentName, val icon: ImageBitmap)

private class ShareCardStyle(val top: Color, val bottom: Color, val text: Color)

@Composable
private fun rememberShareStyles(): List<ShareCardStyle> {
    val primary = MaterialTheme.colorScheme.primary
    return remember(primary) {
        listOf(
            ShareCardStyle(lerp(primary, Color.Black, 0.35f), lerp(primary, Color.Black, 0.8f), Color.White),
            ShareCardStyle(Color(0xFF2A2A2E), Color(0xFF0A0A0B), Color.White),
            ShareCardStyle(Color(0xFFFAF7F2), Color(0xFFE6E0D4), Color(0xFF141414)),
            ShareCardStyle(Color(0xFF7B4BC4), Color(0xFF22103D), Color.White),
            ShareCardStyle(Color(0xFF2A7DE1), Color(0xFF0A2552), Color.White),
            ShareCardStyle(Color(0xFFEC6B3A), Color(0xFF8E1B4F), Color.White),
            ShareCardStyle(Color(0xFF2F9E67), Color(0xFF0B3022), Color.White),
            ShareCardStyle(Color(0xFFE5487F), Color(0xFF5E1030), Color.White),
        )
    }
}

fun defaultShareUrl(title: String, artist: String): String = "https://music.youtube.com/search?q=" + Uri.encode("$title $artist")

@Composable
fun ShareSheet(
    title: String,
    artist: String,
    artUrl: String?,
    onDismiss: () -> Unit,
    shareUrl: String = defaultShareUrl(title, artist),
    instagramAppId: String? = null,
) {
    val item = remember(title, artist, artUrl) { ShareItem(title, artist, artUrl) }
    ShareSheetContent(item, onDismiss, shareUrl, instagramAppId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheetContent(
    track: ShareItem,
    onDismiss: () -> Unit,
    shareUrl: String,
    instagramAppId: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val styles = rememberShareStyles()
    val pager = rememberPagerState { styles.size }
    val layers = styles.indices.map { rememberGraphicsLayer() }
    var includeImage by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val text = "${track.title} \u2013 ${track.artist}\n$shareUrl"

    val apps by produceState(emptyList<ShareApp>(), includeImage) {
        value = withContext(Dispatchers.IO) { resolveShareApps(context, includeImage) }
    }
    val instagramInstalled = remember { context.isInstalled("com.instagram.android") }

    fun withCard(block: suspend (Bitmap?) -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            val bmp = if (includeImage) captureCard(layers[pager.currentPage]) else null
            block(bmp)
            busy = false
        }
    }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            context.toast("Couldn't open that app")
        }
        onDismiss()
    }

    fun shareIntent(uri: Uri?): Intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TITLE, track.title)
        if (uri != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }

    fun sendTo(app: ShareApp?) = withCard { bmp ->
        val uri = bmp?.let { writeToCache(context, it, track) }
        val intent = shareIntent(uri)
        open(if (app == null) Intent.createChooser(intent, null) else intent.setComponent(app.component))
    }

    GamutSheet(onDismiss = onDismiss, shape = SheetShape) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip(includeImage, onClick = { includeImage = true }) {
                    ChipIcon(PhosphorIcons.Bold.ImageIcon)
                    ChipLabel("Image and")
                    ChipIcon(PhosphorIcons.Bold.LinkSimpleHorizontal)
                    ChipLabel("URL")
                }
                ModeChip(!includeImage, onClick = { includeImage = false }) {
                    ChipIcon(PhosphorIcons.Bold.LinkSimpleHorizontal)
                    ChipLabel("URL Only")
                }
            }

            AnimatedVisibility(includeImage) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        HorizontalPager(
                            state = pager,
                            contentPadding = PaddingValues(horizontal = ((maxWidth - CardW) / 2).coerceAtLeast(0.dp)),
                            pageSize = PageSize.Fixed(CardW),
                            pageSpacing = 16.dp,
                            beyondViewportPageCount = styles.size,
                        ) { page -> ShareCard(track, styles[page], layers[page]) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                        styles.forEachIndexed { i, st ->
                            val selected = pager.currentPage == i
                            Box(
                                Modifier.padding(horizontal = 4.dp).size(30.dp).clip(CircleShape)
                                    .background(Brush.verticalGradient(listOf(st.top, st.bottom)))
                                    .border(if (selected) 2.dp else 1.dp, if (selected) ink() else ink(0.25f), CircleShape)
                                    .clickable { scope.launch { pager.animateScrollToPage(i) } },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(!includeImage) {
                LinkPreview(track, shareUrl)
            }

            HorizontalDivider(Modifier.padding(top = 16.dp), color = ink(0.1f))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ShareTarget("Copy link", onClick = {
                        context.copyText(track.title, shareUrl)
                        onDismiss()
                    }) { Icon(PhosphorIcons.Bold.Link, null, tint = ink(), modifier = Modifier.size(28.dp)) }
                }
                if (includeImage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    item {
                        ShareTarget("Save image", onClick = {
                            withCard { bmp ->
                                val ok = bmp != null && saveToGallery(context, bmp, track)
                                context.toast(if (ok) "Saved to Pictures/Gamut" else "Couldn't save the image")
                                onDismiss()
                            }
                        }) { Icon(PhosphorIcons.Bold.DownloadSimple, null, tint = ink(), modifier = Modifier.size(28.dp)) }
                    }
                }
                if (includeImage && instagramAppId != null && instagramInstalled) {
                    item {
                        ShareTarget("Story", onClick = {
                            withCard { bmp ->
                                val uri = bmp?.let { writeToCache(context, it, track) }
                                if (uri == null) {
                                    context.toast("Couldn't create the image")
                                    return@withCard
                                }
                                context.grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                open(
                                    Intent("com.instagram.share.ADD_TO_STORY").apply {
                                        setDataAndType(uri, "image/png")
                                        setPackage("com.instagram.android")
                                        putExtra("source_application", instagramAppId)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                )
                            }
                        }) { AppIcon(context.appIcon("com.instagram.android")) }
                    }
                }
                items(apps.size) { i ->
                    val app = apps[i]
                    ShareTarget(app.label, onClick = { sendTo(app) }) { AppIcon(app.icon) }
                }
                item {
                    ShareTarget("More", onClick = { sendTo(null) }) {
                        Icon(PhosphorIcons.Bold.DotsThree, null, tint = ink(), modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(ink(if (selected) 0.14f else 0.05f))
            .border(BorderStroke(1.dp, ink(if (selected) 0.35f else 0.12f)), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun ChipIcon(icon: ImageVector) {
    Icon(icon, null, tint = ink(), modifier = Modifier.size(16.dp))
}

@Composable
private fun ChipLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = ink(), maxLines = 1)
}

@Composable
private fun LinkPreview(track: ShareItem, url: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(
            url = remember(track.artUrl) { squareArtUrl(track.artUrl, 256) },
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
            fallback = { Icon(PhosphorIcons.Bold.MusicNote, null, tint = ink(0.5f)) },
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = ink(0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, style = MaterialTheme.typography.labelSmall, color = ink(0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShareCard(track: ShareItem, style: ShareCardStyle, layer: GraphicsLayer) {
    Column(
        Modifier
            .size(CardW, CardH)
            .clip(RoundedCornerShape(20.dp))
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .background(Brush.verticalGradient(listOf(style.top, style.bottom)))
            .padding(22.dp),
    ) {
        ArtImage(
            url = remember(track.artUrl) { squareArtUrl(track.artUrl, 600) },
            modifier = Modifier.fillMaxWidth().height(CardW - 44.dp).clip(RoundedCornerShape(10.dp)),
            fallback = { Icon(PhosphorIcons.Bold.MusicNote, null, tint = style.text.copy(alpha = 0.5f), modifier = Modifier.size(48.dp)) },
        )
        Spacer(Modifier.height(18.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = style.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            track.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = style.text.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Gamut",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = style.text.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun ShareTarget(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        Modifier.width(76.dp).bounceClick(onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(ink(0.1f)), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink(), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AppIcon(icon: ImageBitmap?) {
    if (icon != null) {
        Image(icon, null, Modifier.size(56.dp).clip(CircleShape))
    } else {
        Icon(PhosphorIcons.Bold.DotsThree, null, tint = ink(), modifier = Modifier.size(28.dp))
    }
}

private suspend fun captureCard(layer: GraphicsLayer): Bitmap? = try {
    if (layer.size.width == 0 || layer.size.height == 0) {
        null
    } else {
        val bmp = layer.toImageBitmap().asAndroidBitmap()
        if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
    }
} catch (_: Exception) {
    null
}

private fun fileName(track: ShareItem): String {
    val slug = "${track.artist}-${track.title}".lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
    return "gamut-$slug"
}

private suspend fun writeToCache(context: Context, bmp: Bitmap, track: ShareItem): Uri? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "${fileName(track)}-${System.currentTimeMillis()}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    } catch (_: Exception) {
        null
    }
}

private suspend fun saveToGallery(context: Context, bmp: Bitmap, track: ShareItem): Boolean = withContext(Dispatchers.IO) {
    try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${fileName(track)}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Gamut")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext false
        resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } != null
    } catch (_: Exception) {
        false
    }
}

private fun resolveShareApps(context: Context, withImage: Boolean): List<ShareApp> {
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_SEND).setType(if (withImage) "image/png" else "text/plain")
    val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(probe, 0)
    }
    return infos
        .filter { it.activityInfo.exported && it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .sortedBy { PriorityPackages.indexOf(it.activityInfo.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        .take(8)
        .mapNotNull { ri ->
            try {
                val ai = ri.activityInfo
                ShareApp(
                    label = pm.getApplicationLabel(ai.applicationInfo).toString(),
                    component = ComponentName(ai.packageName, ai.name),
                    icon = ai.applicationInfo.loadIcon(pm).toBitmap(112, 112).asImageBitmap(),
                )
            } catch (_: Exception) {
                null
            }
        }
}

private fun Context.isInstalled(pkg: String): Boolean = try {
    packageManager.getApplicationInfo(pkg, 0)
    true
} catch (_: Exception) {
    false
}

private fun Context.appIcon(pkg: String): ImageBitmap? = try {
    packageManager.getApplicationIcon(pkg).toBitmap(112, 112).asImageBitmap()
} catch (_: Exception) {
    null
}

private fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

private fun Context.copyText(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) toast("Link copied")
}
