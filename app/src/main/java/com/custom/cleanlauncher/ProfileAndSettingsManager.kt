package com.custom.cleanlauncher

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.*
import androidx.core.content.edit

data class UserProfile(
    val id: String,
    val name: String,
    val favoriteGenres: String = "28,878,18"
)

class ProfileAndSettingsManager(
    private val context: Context,
    private val onStateChanged: () -> Unit
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("clean_launcher_prefs", Context.MODE_PRIVATE)

    fun getActiveProfile(): UserProfile {
        val id = prefs.getString("active_profile_id", "default") ?: "default"
        val name = prefs.getString("profile_name_$id", "Perfil Principal") ?: "Perfil Principal"
        val genres = prefs.getString("profile_genres_$id", "28,878,18") ?: "28,878,18"
        return UserProfile(id, name, genres)
    }

    fun getAllProfiles(): List<UserProfile> {
        val ids = prefs.getStringSet("profile_ids", setOf("default")) ?: setOf("default")
        return ids.map { id ->
            val name = prefs.getString("profile_name_$id", "Perfil Principal") ?: "Perfil Principal"
            val genres = prefs.getString("profile_genres_$id", "28,878,18") ?: "28,878,18"
            UserProfile(id, name, genres)
        }
    }

    fun showProfileDropdownMenu() {
        val profiles = getAllProfiles()
        val activeProfile = getActiveProfile()

        val menuItems = mutableListOf<String>()
        profiles.forEach { profile ->
            val prefix = if (profile.id == activeProfile.id) "● " else "○ "
            menuItems.add("$prefix${profile.name}")
        }
        menuItems.add("+ Criar Perfil")
        menuItems.add("⚙ Configurações do Launcher")

        AlertDialog.Builder(context)
            .setTitle("Perfis")
            .setItems(menuItems.toTypedArray()) { _, which ->
                when {
                    which < profiles.size -> {
                        val selected = profiles[which]
                        prefs.edit { putString("active_profile_id", selected.id) }
                        onStateChanged()
                    }
                    which == profiles.size -> showCreateProfileDialog()
                    which == profiles.size + 1 -> showLauncherSettingsDialog()
                }
            }
            .show()
    }

    private fun showCreateProfileDialog() {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val inputName = EditText(context).apply {
            hint = "Nome do Perfil (ex: Cinema, Família)"
        }
        layout.addView(inputName)

        AlertDialog.Builder(context)
            .setTitle("Criar Novo Perfil")
            .setView(layout)
            .setPositiveButton("Criar") { _, _ ->
                val newName = inputName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newId = "prof_${System.currentTimeMillis()}"
                    val currentIds = prefs.getStringSet("profile_ids", setOf("default"))?.toMutableSet()
                        ?: mutableSetOf("default")
                    currentIds.add(newId)
                    prefs.edit {
                        putStringSet("profile_ids", currentIds)
                        putString("profile_name_$newId", newName)
                        putString("active_profile_id", newId)
                    }
                    onStateChanged()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showLauncherSettingsDialog() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
        }

        val inputTmdb = EditText(context).apply {
            hint = "PIN / Chave API do TMDb"
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("tmdb_api_key", ""))
        }
        container.addView(TextView(context).apply { text = "PIN / Chave TMDb:" })
        container.addView(inputTmdb)

        val switchContinue = Switch(context).apply {
            text = "Aba Continuar Assistindo"
            isChecked = prefs.getBoolean("show_continue_watching", true)
            setPadding(0, 24, 0, 12)
        }
        container.addView(switchContinue)

        val switchPersonalized = Switch(context).apply {
            text = "Recomendações baseadas no que assisto"
            isChecked = prefs.getBoolean("personalized_recs", true)
            setPadding(0, 12, 0, 24)
        }
        container.addView(switchPersonalized)

        val switchWhiteTheme = Switch(context).apply {
            text = "Modo Claro (White) / Desligado = Modo Escuro (Black)"
            isChecked = prefs.getString("theme_mode", "BLACK") == "WHITE"
            setPadding(0, 12, 0, 24)
        }
        container.addView(switchWhiteTheme)

        container.addView(TextView(context).apply { text = "Tamanho dos Ícones e Cards:" })
        val seekIconSize = SeekBar(context).apply {
            max = 50
            progress = prefs.getInt("icon_scale_offset", 20)
        }
        container.addView(seekIconSize)

        val inputWallpaperUrl = EditText(context).apply {
            hint = "URL do Papel de Parede (vazio = cor sólida)"
            setText(prefs.getString("wallpaper_uri", ""))
        }
        container.addView(inputWallpaperUrl)

        AlertDialog.Builder(context)
            .setTitle("Configurações do Launcher")
            .setView(container)
            .setPositiveButton("Salvar") { _, _ ->
                prefs.edit {
                    putString("tmdb_api_key", inputTmdb.text.toString().trim())
                    putBoolean("show_continue_watching", switchContinue.isChecked)
                    putBoolean("personalized_recs", switchPersonalized.isChecked)
                    putString("theme_mode", if (switchWhiteTheme.isChecked) "WHITE" else "BLACK")
                    putInt("icon_scale_offset", seekIconSize.progress)
                    putString("wallpaper_uri", inputWallpaperUrl.text.toString().trim())
                }
                onStateChanged()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
