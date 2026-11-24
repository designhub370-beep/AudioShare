package com.audioshare.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class AudioShareService : Service() {
    companion object {
        const val ACTION_START = "com.audioshare.app.START"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_DEVICE_ADDRESSES = "device_addresses"
        const val EXTRA_VOLUMES = "volumes"
        private const val CHANNEL = "audioshare"
    }
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private val running = AtomicBoolean(false)
    private val tracks = mutableListOf<Pair<AudioTrack, Float>>()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "AudioShare", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notification.Builder(this, CHANNEL)
            .setContentTitle("AudioShare")
            .setContentText("Trying multi-device audio sharing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(77, n)
        val data = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA) ?: return START_NOT_STICKY
        val code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val addresses = intent.getStringArrayListExtra(EXTRA_DEVICE_ADDRESSES) ?: arrayListOf()
        val volumes = intent.getIntegerArrayListExtra(EXTRA_VOLUMES) ?: arrayListOf()
        startCapture(code, data, addresses, volumes)
        return START_STICKY
    }

    private fun startCapture(code: Int, data: Intent, addresses: ArrayList<String>, volumes: ArrayList<Int>) {
        val pmm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = pmm.getMediaProjection(code, data)
        val rate = 48000
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val inMask = AudioFormat.CHANNEL_IN_STEREO
        val minBuf = AudioRecord.getMinBufferSize(rate, inMask, encoding)
        if (minBuf <= 0 || projection == null) { stopSelf(); return }

        val config = AudioPlaybackCaptureConfiguration.Builder(projection!!)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME).build()
        recorder = AudioRecord.Builder()
            .setAudioFormat(AudioFormat.Builder().setEncoding(encoding).setSampleRate(rate).setChannelMask(inMask).build())
            .setBufferSizeInBytes(max(minBuf * 2, 8192))
            .setAudioPlaybackCaptureConfig(config).build()

        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothOutputs = outputs.filter {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= 31 && (
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
            ))
        }

        addresses.forEachIndexed { i, address ->
            val device = bluetoothOutputs.firstOrNull {
                it.address.equals(address, ignoreCase = true)
            } ?: run {
                val bt = try {
                    android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)
                } catch (_: Exception) { null }
                val wantedName = try { bt?.name?.trim() } catch (_: Exception) { null }
                if (wantedName.isNullOrEmpty()) null else bluetoothOutputs.firstOrNull {
                    it.productName?.toString()?.trim().equals(wantedName, ignoreCase = true)
                }
            }
            if (device != null) {
                val track = try {
                    AudioTrack.Builder()
                        .setAudioAttributes(android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(encoding).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                        .setBufferSizeInBytes(max(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, encoding) * 2, 8192))
                        .build()
                } catch (_: Exception) { null }
                if (track != null && track.setPreferredDevice(device)) {
                    track.play()
                    try { Thread.sleep(80) } catch (_: InterruptedException) {}
                    tracks.add(track to ((volumes.getOrNull(i) ?: 100) / 100f))
                } else track?.release()
            }
        }
        if (tracks.isEmpty()) { stopSelf(); return }

        running.set(true)
        recorder!!.startRecording()
        thread(name = "AudioShare-Capture") {
            val buffer = ShortArray(4096)
            while (running.get()) {
                val count = recorder?.read(buffer, 0, buffer.size) ?: -1
                if (count > 0) {
                    tracks.toList().forEach { (track, gain) ->
                        if (gain >= 0.999f) track.write(buffer, 0, count)
                        else {
                            val copy = ShortArray(count)
                            for (j in 0 until count) copy[j] = min(32767, max(-32768, (buffer[j] * gain).toInt())).toShort()
                            track.write(copy, 0, count)
                        }
                    }
                } else break
            }
        }
    }

    override fun onDestroy() {
        running.set(false)
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        tracks.forEach { (t, _) -> try { t.stop() } catch (_: Exception) {}; t.release() }
        tracks.clear()
        projection?.stop(); projection = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}