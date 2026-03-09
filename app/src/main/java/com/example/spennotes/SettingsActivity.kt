package com.example.spennotes

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var localIpEdit: TextInputEditText
    private lateinit var tailscaleIpEdit: TextInputEditText
    private lateinit var serverPortEdit: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        findViewById<MaterialToolbar>(R.id.settings_toolbar).setNavigationOnClickListener { finish() }

        localIpEdit = findViewById(R.id.edit_local_ip)
        tailscaleIpEdit = findViewById(R.id.edit_tailscale_ip)
        serverPortEdit = findViewById(R.id.edit_server_port)

        setupSPenSettings()
        setupServerSettings()
    }

    private fun setupSPenSettings() {
        val spenOnlySwitch = findViewById<SwitchMaterial>(R.id.switch_spen_only)
        spenOnlySwitch.isChecked = prefs.getBoolean(KEY_SPEN_ONLY, false)
        spenOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SPEN_ONLY, isChecked).apply()
        }
    }

    private fun setupServerSettings() {
        localIpEdit.setText(prefs.getString(KEY_LOCAL_IP, DEFAULT_LOCAL_IP))
        tailscaleIpEdit.setText(prefs.getString(KEY_TAILSCALE_IP, DEFAULT_TAILSCALE_IP))
        serverPortEdit.setText(prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT).toString())
    }

    override fun onPause() {
        super.onPause()
        val port = serverPortEdit.text.toString().toIntOrNull() ?: DEFAULT_SERVER_PORT
        prefs.edit()
            .putString(KEY_LOCAL_IP, localIpEdit.text.toString().ifBlank { DEFAULT_LOCAL_IP })
            .putString(KEY_TAILSCALE_IP, tailscaleIpEdit.text.toString().ifBlank { DEFAULT_TAILSCALE_IP })
            .putInt(KEY_SERVER_PORT, port)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "spennotes_settings"

        const val KEY_SPEN_ONLY = "spen_only"
        const val KEY_LOCAL_IP = "local_ip"
        const val KEY_TAILSCALE_IP = "tailscale_ip"
        const val KEY_SERVER_PORT = "server_port"

        const val DEFAULT_LOCAL_IP = ""
        const val DEFAULT_TAILSCALE_IP = ""
        const val DEFAULT_SERVER_PORT = 5000
    }
}
