package com.audioshare.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var deviceList: LinearLayout
    private lateinit var status: TextView
    private lateinit var selectedCount: TextView
    private val selected = linkedSetOf<String>()
    private val volumes = mutableMapOf<String, Int>()
    private val projectionRequest = 200
    private val audioPermissionRequest = 201

    private val navy = Color.rgb(10, 17, 32)
    private val panel = Color.rgb(20, 30, 52)
    private val panel2 = Color.rgb(26, 38, 65)
    private val cyan = Color.rgb(74, 216, 255)
    private val white = Color.rgb(245, 248, 255)
    private val muted = Color.rgb(164, 178, 204)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun outlined(fill: Int, line: Int) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), line); cornerRadius = dp(20).toFloat()
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        buildUi()
        requestBluetoothPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(navy) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(22))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(this).apply {
            text = "AudioShare"; textSize = 29f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white)
        })
        titles.addView(TextView(this).apply {
            text = "Listen together • control every device"; textSize = 13f; setTextColor(muted)
        })
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "⚙"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(white)
            background = rounded(panel2, 16); setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(header)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = outlined(panel, Color.rgb(48, 67, 102)); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val logo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.rgb(250, 246, 244), 18); clipToOutline = true
        }
        hero.addView(logo, LinearLayout.LayoutParams(dp(92), dp(92)))
        hero.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0)
            addView(TextView(this@MainActivity).apply {
                text = "Your sound,\nshared."; textSize = 23f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Experimental: try sharing movie/music audio to up to 5 selected Bluetooth outputs."
                textSize = 13f; setTextColor(muted); setPadding(0, dp(6), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val hp = LinearLayout.LayoutParams(-1, -2); hp.setMargins(0, dp(20), 0, dp(18)); content.addView(hero, hp)
        loadLogo(logo)

        val statusCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.rgb(17, 28, 47), 18); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        statusCard.addView(TextView(this).apply { text = "●"; textSize = 14f; setTextColor(cyan) })
        status = TextView(this).apply {
            text = "Select up to 5 paired devices"; textSize = 14f; setTextColor(white); setPadding(dp(8), 0, 0, 0)
        }
        statusCard.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        selectedCount = TextView(this).apply {
            text = "0/5"; textSize = 13f; typeface = Typeface.DEFAULT_BOLD; setTextColor(cyan)
            background = rounded(Color.rgb(25, 57, 76), 14); setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        statusCard.addView(selectedCount); content.addView(statusCard)

        content.addView(TextView(this).apply {
            text = "DEVICES"; textSize = 12f; typeface = Typeface.DEFAULT_BOLD; setTextColor(muted); letterSpacing = 0.16f
            setPadding(dp(4), dp(22), 0, dp(9))
        })
        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(deviceList)
        content.addView(TextView(this).apply {
            text = "Tip • Pair your earbuds/speakers in Bluetooth Settings first. Android may still limit simultaneous outputs."
            textSize = 12f; setTextColor(muted); setPadding(dp(4), dp(12), dp(4), dp(10))
        })

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "⌕  Search / Pair"; textSize = 13f; isAllCaps = false; setTextColor(white); background = rounded(panel2, 18)
            setOnClickListener { openBluetoothPairing() }
        }, LinearLayout.LayoutParams(0, dp(54), 0.42f))
        val shareParams = LinearLayout.LayoutParams(0, dp(54), 0.62f); shareParams.setMargins(dp(10), 0, 0, 0)
        buttons.addView(Button(this).apply {
            text = "▶  Share Movie Audio"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; isAllCaps = false
            setTextColor(Color.WHITE); background = rounded(Color.rgb(55, 113, 232), 18)
            setOnClickListener { startSharingExperiment() }
        }, shareParams)
        val bp = LinearLayout.LayoutParams(-1, dp(54)); bp.setMargins(0, dp(8), 0, 0); content.addView(buttons, bp)

        scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(root)
    }

    private fun loadLogo(imageView: ImageView) {
        val url = "https://plain-apac-prod-public.komododecks.com/202611/24/jyM9s0qttMZWWIFPYXUj/image.png"
        thread {
            try {
                val bitmap = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                runOnUiThread { if (bitmap != null) imageView.setImageBitmap(bitmap) }
            } catch (_: Exception) {}
        }
    }

    private fun startSharingExperiment() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "Select at least one device", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Movie-audio capture needs Android 10 or newer.", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), audioPermissionRequest)
            return
        }
        val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(pm.createScreenCaptureIntent(), projectionRequest)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == projectionRequest && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, AudioShareService::class.java).apply {
                action = AudioShareService.ACTION_START
                putExtra(AudioShareService.EXTRA_RESULT_CODE, resultCode)
                putExtra(AudioShareService.EXTRA_PROJECTION_DATA, data)
                putStringArrayListExtra(AudioShareService.EXTRA_DEVICE_ADDRESSES, ArrayList(selected))
                putIntegerArrayListExtra(AudioShareService.EXTRA_VOLUMES, ArrayList(selected.map { volumes[it] ?: 100 }))
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
            status.text = "Trying to share audio to " + selected.size + " device(s)…"
            Toast.makeText(this, "AudioShare is trying the multi-output route.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openBluetoothPairing() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            Toast.makeText(this, "Pair the new earbuds/headphones/speaker in Bluetooth, then return to AudioShare.", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        if (::deviceList.isInitialized &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
        ) {
            // Bluetooth Settings can finish pairing a moment after this activity resumes.
            // Refresh once now and again shortly after returning from Settings.
            loadPairedDevices()
            deviceList.postDelayed({ loadPairedDevices() }, 1200)
            deviceList.postDelayed({ loadPairedDevices() }, 3000)
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) requestPermissions(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT), 100
        ) else loadPairedDevices()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == audioPermissionRequest && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            startSharingExperiment()
        } else if (requestCode == 100) {
            if (results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) loadPairedDevices()
            else status.text = "Bluetooth permission is required to show paired devices."
        }
    }

    private fun loadPairedDevices() {
        deviceList.removeAllViews()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) { status.text = "Bluetooth is not available on this phone."; return }
        val devices: Set<BluetoothDevice> = try { adapter.bondedDevices } catch (_: SecurityException) { emptySet() }
        if (devices.isEmpty()) {
            deviceList.addView(TextView(this).apply {
                text = "No paired Bluetooth devices found.\nPair your earbuds/speakers first."
                textSize = 15f; setTextColor(muted); gravity = Gravity.CENTER; background = rounded(panel, 20)
                setPadding(dp(20), dp(30), dp(20), dp(30))
            }); return
        }
        // Show every paired audio device; the 5-device limit applies only to selection.
        devices.sortedBy {
            try { it.name ?: it.address } catch (_: SecurityException) { it.address }
        }.forEach { addDeviceRow(it) }
        status.text = "Showing " + devices.size + " paired device(s) • select up to 5"
    }

    private fun addDeviceRow(device: BluetoothDevice) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rounded(panel, 20); setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "♫"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(cyan); background = rounded(Color.rgb(24, 52, 70), 15)
        }, LinearLayout.LayoutParams(dp(46), dp(46)))
        top.addView(CheckBox(this).apply {
            text = try { device.name ?: "Bluetooth device" } catch (_: SecurityException) { "Bluetooth device" }
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; setTextColor(white); buttonTintList = ColorStateList.valueOf(cyan)
            setPadding(dp(8), 0, 0, 0)
            setOnCheckedChangeListener { _, checked ->
                if (checked && selected.size >= 5) { isChecked = false; Toast.makeText(this@MainActivity, "Maximum 5 devices", Toast.LENGTH_SHORT).show() }
                else {
                    if (checked) selected.add(device.address) else selected.remove(device.address)
                    selectedCount.text = selected.size.toString() + "/5"
                    status.text = if (selected.isEmpty()) "Select up to 5 paired devices" else selected.size.toString() + " device(s) selected"
                }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(top)
        row.addView(TextView(this).apply { text = "Volume"; textSize = 12f; setTextColor(muted); setPadding(dp(4), dp(10), dp(4), 0) })
        val volume = SeekBar(this).apply {
            max = 100; progress = 100; progressTintList = ColorStateList.valueOf(cyan); thumbTintList = ColorStateList.valueOf(Color.WHITE)
        }
        row.addView(volume, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        val label = TextView(this).apply { text = "100%"; textSize = 12f; gravity = Gravity.END; setTextColor(cyan) }
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { label.text = progress.toString() + "%"; volumes[device.address] = progress }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        row.addView(label)
        volumes.putIfAbsent(device.address, 100)
        val p = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT); p.setMargins(0, 0, 0, dp(10)); deviceList.addView(row, p)
    }
}
