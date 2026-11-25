package com.example.player

import android.content.Context
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.data.model.OutputTransportType

data class AudioOutputRouteInfo(
    val deviceName: String = "Built-in Speaker",
    val transport: OutputTransportType = OutputTransportType.SPEAKER,
    val supportedCodecs: List<String> = listOf("PCM 16-bit"),
    val supportedSampleRatesHz: List<Int> = listOf(44100, 48000)
)

/**
 * Zero-permission live audio output route monitor using Android AudioManager & AudioDeviceCallback.
 * Detects Bluetooth A2DP, Bluetooth LE Audio, USB-C DACs, 3.5mm wired headsets, and built-in speakers
 * along with hardware-reported encodings and supported sample rates without requiring Bluetooth permissions.
 */
class AudioOutputMonitor(
    context: Context,
    private val onRouteChanged: (AudioOutputRouteInfo) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }
    }

    fun start() {
        runCatching {
            audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        }
        refresh()
    }

    fun stop() {
        runCatching {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
        }
    }

    fun refresh(): AudioOutputRouteInfo {
        val info = queryCurrentRoute()
        onRouteChanged(info)
        return info
    }

    private fun queryCurrentRoute(): AudioOutputRouteInfo {
        val activeDevice = resolveActiveOutputDevice() ?: return AudioOutputRouteInfo()
        val transport = mapDeviceTypeToTransport(activeDevice.type)
        val rawName = activeDevice.productName?.toString()?.trim().orEmpty()
        val friendlyName = when {
            rawName.isNotBlank() && !rawName.equals(Build.MODEL, ignoreCase = true) -> rawName
            transport == OutputTransportType.SPEAKER -> "Built-in Speaker"
            transport == OutputTransportType.WIRED_HEADSET -> "Wired Headphones"
            transport == OutputTransportType.USB_DAC -> "USB-C Audio Output"
            transport == OutputTransportType.BLUETOOTH_A2DP -> "Bluetooth Audio (A2DP)"
            transport == OutputTransportType.BLUETOOTH_LE -> "Bluetooth LE Audio"
            else -> rawName.ifBlank { "System Audio Output" }
        }

        val encodings = extractSupportedCodecs(activeDevice, transport)
        val sampleRates = extractSampleRates(activeDevice)

        return AudioOutputRouteInfo(
            deviceName = friendlyName,
            transport = transport,
            supportedCodecs = encodings,
            supportedSampleRatesHz = sampleRates
        )
    }

    private fun resolveActiveOutputDevice(): AudioDeviceInfo? {
        // On Android 13+ (API 33), query the exact routed output device for media music playback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = AndroidAudioAttributes.Builder()
                .setUsage(AndroidAudioAttributes.USAGE_MEDIA)
                .setContentType(AndroidAudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val routed = runCatching { audioManager.getAudioDevicesForAttributes(attrs) }.getOrNull()
            val firstRouted = routed?.firstOrNull()
            if (firstRouted != null) return firstRouted
        }

        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        if (outputs.isEmpty()) return null

        // Priority mirrors Android's standard media routing hierarchy
        val priorityOrder = listOf(
            OutputTransportType.BLUETOOTH_LE,
            OutputTransportType.BLUETOOTH_A2DP,
            OutputTransportType.USB_DAC,
            OutputTransportType.WIRED_HEADSET,
            OutputTransportType.SPEAKER,
            OutputTransportType.OTHER
        )
        return outputs.minByOrNull { device ->
            priorityOrder.indexOf(mapDeviceTypeToTransport(device.type)).let { if (it < 0) 99 else it }
        }
    }

    private fun mapDeviceTypeToTransport(type: Int): OutputTransportType {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputTransportType.BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputTransportType.WIRED_HEADSET
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> OutputTransportType.USB_DAC
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> OutputTransportType.SPEAKER
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
                ) {
                    OutputTransportType.BLUETOOTH_LE
                } else {
                    OutputTransportType.OTHER
                }
            }
        }
    }

    private fun extractSupportedCodecs(device: AudioDeviceInfo, transport: OutputTransportType): List<String> {
        val rawEncodings = linkedSetOf<Int>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                device.audioProfiles.forEach { profile -> rawEncodings.add(profile.format) }
            }
        }
        runCatching {
            device.encodings.forEach { rawEncodings.add(it) }
        }

        val labels = rawEncodings.mapNotNull { encodingToLabel(it) }.distinct().toMutableList()

        if (labels.isEmpty()) {
            when (transport) {
                OutputTransportType.BLUETOOTH_A2DP -> labels.addAll(listOf("A2DP Stereo", "AAC / SBC"))
                OutputTransportType.BLUETOOTH_LE -> labels.addAll(listOf("LC3", "PCM 16/24-bit"))
                OutputTransportType.USB_DAC -> labels.addAll(listOf("PCM 16-bit", "PCM 24-bit", "PCM Float"))
                OutputTransportType.WIRED_HEADSET -> labels.addAll(listOf("PCM 16-bit", "PCM 24-bit"))
                else -> labels.addAll(listOf("PCM 16-bit", "PCM Float"))
            }
        } else if (transport == OutputTransportType.BLUETOOTH_A2DP && labels.none { it.contains("A2DP") || it.contains("AAC") || it.contains("SBC") }) {
            labels.add(0, "A2DP Wireless")
        } else if (transport == OutputTransportType.BLUETOOTH_LE && labels.none { it.contains("LC3") }) {
            labels.add(0, "LE Audio (LC3)")
        }

        return labels.take(6)
    }

    private fun extractSampleRates(device: AudioDeviceInfo): List<Int> {
        val rates = linkedSetOf<Int>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                device.audioProfiles.forEach { profile ->
                    profile.sampleRates.filter { it > 0 }.forEach { rates.add(it) }
                }
            }
        }
        runCatching {
            device.sampleRates.filter { it > 0 }.forEach { rates.add(it) }
        }
        return if (rates.isEmpty()) listOf(44100, 48000) else rates.sorted().take(6)
    }

    private fun encodingToLabel(encoding: Int): String? {
        return when (encoding) {
            AndroidAudioFormat.ENCODING_PCM_16BIT -> "PCM 16-bit"
            AndroidAudioFormat.ENCODING_PCM_FLOAT -> "PCM 32-bit Float"
            AndroidAudioFormat.ENCODING_PCM_8BIT -> "PCM 8-bit"
            AndroidAudioFormat.ENCODING_MP3 -> "MP3"
            AndroidAudioFormat.ENCODING_AAC_LC -> "AAC-LC"
            AndroidAudioFormat.ENCODING_AAC_HE_V1 -> "HE-AAC"
            AndroidAudioFormat.ENCODING_AAC_HE_V2 -> "HE-AAC v2"
            AndroidAudioFormat.ENCODING_AAC_ELD -> "AAC-ELD"
            AndroidAudioFormat.ENCODING_AAC_XHE -> "xHE-AAC"
            AndroidAudioFormat.ENCODING_AC3 -> "Dolby AC-3"
            AndroidAudioFormat.ENCODING_E_AC3 -> "Dolby Digital+"
            AndroidAudioFormat.ENCODING_DOLBY_TRUEHD -> "Dolby TrueHD"
            AndroidAudioFormat.ENCODING_DTS -> "DTS"
            AndroidAudioFormat.ENCODING_DTS_HD -> "DTS-HD"
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when (encoding) {
                        AndroidAudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM 24-bit"
                        AndroidAudioFormat.ENCODING_PCM_32BIT -> "PCM 32-bit"
                        AndroidAudioFormat.ENCODING_OPUS -> "Opus"
                        else -> null
                    }
                } else {
                    null
                }
            }
        }
    }
}
