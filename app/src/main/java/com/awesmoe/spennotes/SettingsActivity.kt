package com.awesmoe.spennotes

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
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
                            val newActive = when {
                                activeIdx == i -> 0
                                activeIdx > i  -> activeIdx - 1
                                else           -> activeIdx
                            }
                            ServerProfile.setActive(prefs, newActive)
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
        val portEdit = view.findViewById<TextInputEditText>(R.id.edit_profile_port)
        val portLayout = view.findViewById<TextInputLayout>(R.id.layout_profile_port)
        val errorLocalIp = view.findViewById<TextView>(R.id.error_local_ip)
        val errorTailscaleIp = view.findViewById<TextView>(R.id.error_tailscale_ip)
        val errorIpRequired = view.findViewById<TextView>(R.id.error_ip_required)

        val localOctets = listOf(
            view.findViewById<EditText>(R.id.edit_local_1),
            view.findViewById<EditText>(R.id.edit_local_2),
            view.findViewById<EditText>(R.id.edit_local_3),
            view.findViewById<EditText>(R.id.edit_local_4)
        )
        val tailscaleOctets = listOf(
            view.findViewById<EditText>(R.id.edit_tailscale_1),
            view.findViewById<EditText>(R.id.edit_tailscale_2),
            view.findViewById<EditText>(R.id.edit_tailscale_3),
            view.findViewById<EditText>(R.id.edit_tailscale_4)
        )

        setupOctetFields(localOctets)
        setupOctetFields(tailscaleOctets)

        if (isEdit) {
            nameEdit.setText(existing!!.name)
            fillOctets(localOctets, existing.localIp)
            fillOctets(tailscaleOctets, existing.tailscaleIp)
            portEdit.setText(existing.port.toString())
        } else {
            portEdit.setText(DEFAULT_SERVER_PORT.toString())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Server" else "Add Server")
            .setView(view)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            errorLocalIp.visibility = View.GONE
            errorTailscaleIp.visibility = View.GONE
            errorIpRequired.visibility = View.GONE
            portLayout.error = null

            val localParts = readOctets(localOctets)
            val tailscaleParts = readOctets(tailscaleOctets)
            val localIp = octetsToIp(localParts)
            val tailscaleIp = octetsToIp(tailscaleParts)
            val portVal = portEdit.text.toString().toIntOrNull() ?: 0

            var valid = true
            if (localIp == null && tailscaleIp == null) {
                errorIpRequired.visibility = View.VISIBLE
                valid = false
            }
            if (localIp != null && !isValidIp(localParts)) {
                errorLocalIp.visibility = View.VISIBLE
                valid = false
            }
            if (tailscaleIp != null && !isValidIp(tailscaleParts)) {
                errorTailscaleIp.visibility = View.VISIBLE
                valid = false
            }
            if (portVal !in 1..65535) {
                portLayout.error = "Port must be 1–65535"
                valid = false
            }
            if (!valid) return@setOnClickListener

            val profile = ServerProfile(
                name = nameEdit.text.toString().ifBlank { "Pi" },
                localIp = localIp ?: "",
                tailscaleIp = tailscaleIp ?: "",
                port = portVal
            )
            if (isEdit) profiles[editIndex] = profile else profiles.add(profile)
            ServerProfile.saveAll(prefs, profiles)
            refreshProfileList()
            dialog.dismiss()
        }
    }

    private fun setupOctetFields(fields: List<EditText>) {
        for (i in fields.indices) {
            fields[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if ((s?.length ?: 0) == 3 && i < fields.lastIndex)
                        fields[i + 1].requestFocus()
                }
            })
            if (i > 0) {
                fields[i].setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_DEL &&
                        event.action == KeyEvent.ACTION_DOWN &&
                        fields[i].text.isEmpty()
                    ) {
                        fields[i - 1].requestFocus()
                        true
                    } else false
                }
            }
        }
    }

    private fun fillOctets(fields: List<EditText>, ip: String) {
        if (ip.isBlank()) return
        val parts = ip.split(".")
        if (parts.size == 4) parts.forEachIndexed { i, part -> fields[i].setText(part) }
    }

    private fun readOctets(fields: List<EditText>) = fields.map { it.text.toString().trim() }

    private fun octetsToIp(parts: List<String>): String? =
        if (parts.all { it.isEmpty() }) null else parts.joinToString(".")

    private fun isValidIp(parts: List<String>) = parts.all {
        it.toIntOrNull()?.let { v -> v in 0..255 } ?: false
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
