package com.awesmoe.spennotes

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var profilesContainer: LinearLayout
    private var profiles = mutableListOf<ServerProfile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        findViewById<MaterialToolbar>(R.id.settings_toolbar).setNavigationOnClickListener { finish() }

        setupSPenSettings()

        profilesContainer = findViewById(R.id.profiles_container)
        findViewById<Button>(R.id.add_server_button).setOnClickListener { showProfileDialog(-1) }

        profiles = ServerProfile.loadAll(prefs)
        migrateOldSettings()
        refreshProfileList()
    }

    private fun setupSPenSettings() {
        val spenOnlySwitch = findViewById<SwitchMaterial>(R.id.switch_spen_only)
        spenOnlySwitch.isChecked = prefs.getBoolean(KEY_SPEN_ONLY, false)
        spenOnlySwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_SPEN_ONLY, isChecked).apply()
        }
    }

    // Migrate old single-server settings to profile format (one-time)
    private fun migrateOldSettings() {
        if (profiles.isNotEmpty()) return
        val oldLocal = prefs.getString(KEY_LOCAL_IP, "") ?: ""
        val oldTailscale = prefs.getString(KEY_TAILSCALE_IP, "") ?: ""
        val oldPort = prefs.getInt(KEY_SERVER_PORT, DEFAULT_SERVER_PORT)
        if (oldLocal.isNotBlank() || oldTailscale.isNotBlank()) {
            profiles.add(ServerProfile("Pi", oldLocal, oldTailscale, oldPort))
            ServerProfile.saveAll(prefs, profiles)
            ServerProfile.setActive(prefs, 0)
        }
    }

    private fun refreshProfileList() {
        profilesContainer.removeAllViews()
        val activeIdx = ServerProfile.getActive(prefs)

        for ((i, profile) in profiles.withIndex()) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_server_profile, profilesContainer, false)

            val nameText = row.findViewById<TextView>(R.id.profile_name)
            val detailText = row.findViewById<TextView>(R.id.profile_detail)
            val activeMarker = row.findViewById<View>(R.id.active_marker)

            nameText.text = profile.name
            detailText.text = buildString {
                append(profile.localIp)
                if (profile.tailscaleIp.isNotBlank()) append(" / ${profile.tailscaleIp}")
                append(":${profile.port}")
            }
            activeMarker.visibility = if (i == activeIdx) View.VISIBLE else View.INVISIBLE

            row.setOnClickListener { showProfileDialog(i) }
            row.setOnLongClickListener {
                if (profiles.size > 1) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete ${profile.name}?")
                        .setPositiveButton("Delete") { _, _ ->
                            profiles.removeAt(i)
                            ServerProfile.saveAll(prefs, profiles)
                            if (activeIdx >= profiles.size)
                                ServerProfile.setActive(prefs, profiles.size - 1)
                            refreshProfileList()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
                true
            }

            profilesContainer.addView(row)
        }
    }

    private fun showProfileDialog(editIndex: Int) {
        val isEdit = editIndex >= 0
        val existing = if (isEdit) profiles[editIndex] else null

        val view = LayoutInflater.from(this).inflate(R.layout.dialog_server_profile, null)
        val nameEdit = view.findViewById<TextInputEditText>(R.id.edit_profile_name)
        val localEdit = view.findViewById<TextInputEditText>(R.id.edit_profile_local_ip)
        val tailscaleEdit = view.findViewById<TextInputEditText>(R.id.edit_profile_tailscale_ip)
        val portEdit = view.findViewById<TextInputEditText>(R.id.edit_profile_port)

        if (isEdit) {
            nameEdit.setText(existing!!.name)
            localEdit.setText(existing.localIp)
            tailscaleEdit.setText(existing.tailscaleIp)
            portEdit.setText(existing.port.toString())
        } else {
            portEdit.setText(DEFAULT_SERVER_PORT.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Server" else "Add Server")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val profile = ServerProfile(
                    name = nameEdit.text.toString().ifBlank { "Pi" },
                    localIp = localEdit.text.toString().trim(),
                    tailscaleIp = tailscaleEdit.text.toString().trim(),
                    port = portEdit.text.toString().toIntOrNull() ?: DEFAULT_SERVER_PORT
                )
                if (isEdit) {
                    profiles[editIndex] = profile
                } else {
                    profiles.add(profile)
                }
                ServerProfile.saveAll(prefs, profiles)
                refreshProfileList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        const val PREFS_NAME = "spennotes_settings"
        const val KEY_SPEN_ONLY = "spen_only"

        // Legacy keys (kept for migration)
        const val KEY_LOCAL_IP = "local_ip"
        const val KEY_TAILSCALE_IP = "tailscale_ip"
        const val KEY_SERVER_PORT = "server_port"

        const val DEFAULT_LOCAL_IP = ""
        const val DEFAULT_TAILSCALE_IP = ""
        const val DEFAULT_SERVER_PORT = 5000
    }
}
