package com.audioshare.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var deviceList: LinearLayout
    private lateinit var status: TextView
    private val selected = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestBluetoothPermissions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            setBackgroundColor(Color.rgb(248, 249, 252))
        }

        root.addView(TextView(this).apply {
            text = "AudioShare"
            textSize = 30f
            setTextColor(Color.rgb(25, 28, 35))
        })
        root.addView(TextView(this).apply {
            text = "Share audio to multiple Bluetooth devices"
            textSize = 16f
            setTextColor(Color.DKGRAY)
        })

        status = TextView(this).apply {
            text = "Select up to 5 paired devices"
            textSize = 15f
            setPadding(0, 18, 0, 12)
        }
        root.addView(status)

        deviceList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(deviceList, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(Button(this).apply {
            text = "Bluetooth Settings"
            setOnClickListener {
                startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        })

        root.addView(Button(this).apply {
            text = "Start Sharing"
            setOnClickListener {
                val count = selected.size
                if (count == 0) {
                    Toast.makeText(this@MainActivity, "Select at least one device", Toast.LENGTH_SHORT).show()
                } else {
                    status.text = "Sharing prototype active • " + count + " device(s) selected"
                    Toast.makeText(this@MainActivity, "UI ready. Actual multi-output audio requires compatible Android/Bluetooth hardware.", Toast.LENGTH_LONG).show()
                }
            }
        })

        setContentView(root)
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            ), 100)
        } else {
            loadPairedDevices()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadPairedDevices()
            } else {
                status.text = "Bluetooth permission is required to show paired devices."
            }
        }
    }

    private fun loadPairedDevices() {
        deviceList.removeAllViews()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            status.text = "Bluetooth is not available on this phone."
            return
        }

        val devices: Set<BluetoothDevice> = try {
            adapter.bondedDevices
        } catch (_: SecurityException) {
            emptySet()
        }

        if (devices.isEmpty()) {
            deviceList.addView(TextView(this).apply {
                text = "No paired Bluetooth devices found. Pair earbuds/speakers in Bluetooth Settings first."
                textSize = 16f
                setPadding(0, 20, 0, 20)
            })
            return
        }

        devices.take(5).forEach { device -> addDeviceRow(device) }
        status.text = "Showing " + minOf(devices.size, 5) + " paired device(s). Select up to 5."
    }

    private fun addDeviceRow(device: BluetoothDevice) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(Color.WHITE)
        }

        val check = CheckBox(this).apply {
            text = try { device.name ?: "Bluetooth device" } catch (_: SecurityException) { "Bluetooth device" }
            textSize = 17f
            setOnCheckedChangeListener { _, checked ->
                if (checked && selected.size >= 5) {
                    isChecked = false
                    Toast.makeText(this@MainActivity, "Maximum 5 devices", Toast.LENGTH_SHORT).show()
                } else {
                    val key = device.address
                    if (checked) selected.add(key) else selected.remove(key)
                    status.text = selected.size.toString() + "/5 devices selected"
                }
            }
        }
        row.addView(check)

        val volume = SeekBar(this).apply {
            max = 100
            progress = 80
        }
        row.addView(volume, ViewGroup.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))

        val label = TextView(this).apply {
            text = "Individual volume: 80%"
            gravity = Gravity.END
            setTextColor(Color.GRAY)
        }
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "Individual volume: " + progress + "%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        row.addView(label)

        val params = LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.setMargins(0, 0, 0, 14)
        deviceList.addView(row, params)
    }
}
