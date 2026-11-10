package com.gamut.feature.playerui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.Bluetooth
import com.adamglin.phosphoricons.bold.BluetoothConnected
import com.adamglin.phosphoricons.bold.BluetoothSlash
import com.adamglin.phosphoricons.bold.Devices
import com.gamut.core.designsystem.GamutSheet
import com.gamut.core.designsystem.LocalCoverPalette
import com.gamut.core.designsystem.ink

data class OutputDevice(
    val id: Int,
    val name: String,
    val isBluetooth: Boolean,
    val isActive: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSheet(
    accent: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val btOn = rememberBluetoothEnabled()
    val devices = rememberOutputDevices()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connectedBt = devices.firstOrNull { it.isBluetooth }

    GamutSheet(onDismiss = onDismiss, sheetState = sheetState) {
        Text(
            "Connect",
            style = MaterialTheme.typography.titleLarge,
            color = ink(),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(ink(0.06f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (connectedBt != null) accent else ink(if (btOn) 0.9f else 0.5f)
            Icon(
                when {
                    !btOn -> PhosphorIcons.Bold.BluetoothSlash
                    connectedBt != null -> PhosphorIcons.Bold.BluetoothConnected
                    else -> PhosphorIcons.Bold.Bluetooth
                },
                null,
                tint = tint,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !btOn -> "Bluetooth is off"
                        connectedBt != null -> "Connected"
                        else -> "Bluetooth is on"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = ink(),
                )
                Text(
                    when {
                        !btOn -> "Turn it on to play on headphones or speakers"
                        connectedBt != null -> connectedBt.name
                        else -> "No Bluetooth device connected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink(0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        LazyColumn(Modifier.weight(1f, fill = false), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(devices, key = { it.id }) { d ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tint = if (d.isActive) accent else ink(0.75f)
                    Icon(
                        if (d.isBluetooth) PhosphorIcons.Bold.BluetoothConnected else PhosphorIcons.Bold.Devices,
                        null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (d.isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (d.isActive) accent else ink(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (d.isActive) {
                            Text("Playing here", style = MaterialTheme.typography.bodySmall, color = ink(0.6f))
                        }
                    }
                    if (d.isActive) Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(accent)
                .clickable {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(PhosphorIcons.Bold.Bluetooth, null, tint = LocalCoverPalette.current.base, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Find Bluetooth devices",
                    style = MaterialTheme.typography.titleSmall,
                    color = LocalCoverPalette.current.base,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun rememberBluetoothEnabled(): Boolean {
    val context = LocalContext.current
    fun read(): Boolean = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
    var enabled by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                enabled = i?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        enabled = read()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}

@Composable
fun rememberOutputDevices(): List<OutputDevice> {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    fun read(): List<OutputDevice> {
        val raw = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.type in SHOWN_TYPES }
        val active = raw.minByOrNull { priority(it.type) }?.id
        return raw.sortedBy { priority(it.type) }.map {
            OutputDevice(
                id = it.id,
                name = it.label(),
                isBluetooth = it.type in BT_TYPES,
                isActive = it.id == active,
            )
        }
    }
    var devices by remember { mutableStateOf(read()) }
    DisposableEffect(am) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                devices = read()
            }
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                devices = read()
            }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    return devices
}

private val BT_TYPES = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER)
private val SHOWN_TYPES = BT_TYPES + setOf(
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HDMI,
)

private fun priority(type: Int) = when (type) {
    in BT_TYPES -> 0
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 2
    else -> 1
}

private fun AudioDeviceInfo.label(): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "This phone"
    else -> productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio device"
}
