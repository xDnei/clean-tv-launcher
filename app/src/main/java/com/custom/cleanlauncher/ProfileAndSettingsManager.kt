package com.custom.cleanlauncher

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.core.content.edit

data class UserProfile(
    val id: String,
    val name: String,
    val favoriteGenres: String = "28,878,18",
    val avatarType: String = "PRESET_PERSON",
    val customAvatarPath: String? = null
)

class ProfileAndSettingsManager(
    private val context: Context,
    private val onRequestGalleryPick: ((String) -> Unit) -> Unit,
    private val onStateChanged: () -> Unit
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("clean_launcher_prefs", Context.MODE_PRIVATE)

    fun getActiveProfile(): UserProfile {
        val id = prefs.getString("active_profile_id", "default") ?: "default"
        return getProfileById(id)
    }

    private fun getProfileById(id: String): UserProfile {
        val name = prefs.getString("profile_name_$id", "Perfil Principal") ?: "Perfil Principal"
        val genres = prefs.getString("profile_genres_$id", "28,878,18") ?: "28,878,18"
        val avatarType = prefs.getString("profile_avatar_type_$id", "PRESET_PERSON") ?: "PRESET_PERSON"
        val customPath = prefs.getString("profile_avatar_uri_$id", null)
        return UserProfile(id, name, genres, avatarType, customPath)
    }

    fun getAllProfiles(): List<UserProfile> {
        val ids = prefs.getStringSet("profile_ids", setOf("default")) ?: setOf("default")
        val sortedIds = ids.toList().sortedBy { if (it == "default") "" else it }
        return sortedIds.map { getProfileById(it) }
    }

    fun getAppIconScale(): Float {
        val pct = prefs.getInt("app_icon_scale_pct", -1)
        if (pct != -1) return (pct / 100f).coerceIn(0.80f, 1.35f)
        val legacyOffset = prefs.getInt("icon_scale_offset", 15)
        return (0.85f + legacyOffset / 100f).coerceIn(0.80f, 1.35f)
    }

    fun getPosterCardScale(): Float {
        val pct = prefs.getInt("poster_card_scale_pct", 100)
        return (pct / 100f).coerceIn(0.80f, 1.35f)
    }

    /**
     * Pop-up cinematográfico com as opções de streaming disponíveis e trailers para o filme selecionado.
     */
    fun showMediaStreamsPopup(item: MediaItem, streams: List<StreamOption>, onDismissRestoreFocus: (() -> Unit)? = null) {
        val palette = ThemeManager.getPalette(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiUtils.dp(context, 36), UiUtils.dp(context, 28), UiUtils.dp(context, 36), UiUtils.dp(context, 32))
            background = UiUtils.createAdaptiveDrawable(context, palette.bgDialog, palette.cardBorderNormal, 1.5f, 22f)
            layoutParams = ViewGroup.LayoutParams(UiUtils.dp(context, 480), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val titleTv = TextView(context).apply {
            text = item.title
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            maxLines = 1
        }
        val overviewTv = TextView(context).apply {
            text = item.overview.ifEmpty { "Nenhuma descrição disponível." }
            textSize = 12.5f
            setTextColor(palette.textSecondary)
            maxLines = 3
            setPadding(0, UiUtils.dp(context, 4), 0, UiUtils.dp(context, 16))
        }

        root.addView(titleTv)
        root.addView(overviewTv)

        val listScroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(context, 240))
        }
        val optionsCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        streams.forEach { opt ->
            val optBtn = Button(context).apply {
                text = opt.title
                isAllCaps = false
                textSize = 14f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setPadding(UiUtils.dp(context, 18), UiUtils.dp(context, 12), UiUtils.dp(context, 18), UiUtils.dp(context, 12))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(context, 48)).apply {
                    bottomMargin = UiUtils.dp(context, 8)
                }
                UiUtils.attachSingleFocus(
                    this,
                    palette,
                    cornerRadiusDp = 12f,
                    scaleUp = 1.03f,
                    normalFill = palette.cardSurface,
                    focusedFill = palette.accentCyan
                )
                setOnClickListener {
                    try {
                        context.startActivity(opt.actionIntent)
                        dialog.dismiss()
                    } catch (_: Exception) {
                        Toast.makeText(context, "Não foi possível abrir o aplicativo selecionado.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            optionsCol.addView(optBtn)
        }
        listScroll.addView(optionsCol)
        root.addView(listScroll)

        dialog.setContentView(root)
        dialog.setOnDismissListener { onDismissRestoreFocus?.invoke() }
        dialog.show()
    }

    fun showProfileDropdownMenu(onDismissRestoreFocus: (() -> Unit)? = null) {
        val palette = ThemeManager.getPalette(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiUtils.dp(context, 40), UiUtils.dp(context, 34), UiUtils.dp(context, 40), UiUtils.dp(context, 34))
            background = UiUtils.createAdaptiveDrawable(context, palette.bgDialog, palette.cardBorderNormal, 1.5f, 22f)
        }

        val title = TextView(context).apply {
            text = "Quem está assistindo?"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val profilesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(UiUtils.dp(context, 12), UiUtils.dp(context, 28), UiUtils.dp(context, 12), UiUtils.dp(context, 24))
            clipChildren = false
            clipToPadding = false
        }

        val profiles = getAllProfiles()
        val activeProfile = getActiveProfile()

        profiles.forEach { prof ->
            val isActive = prof.id == activeProfile.id
            val itemCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(UiUtils.dp(context, 14), UiUtils.dp(context, 12), UiUtils.dp(context, 14), UiUtils.dp(context, 12))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = UiUtils.dp(context, 8)
                    marginEnd = UiUtils.dp(context, 8)
                }
            }

            val avatarImg = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(UiUtils.dp(context, 82), UiUtils.dp(context, 82))
            }
            AvatarRenderer.renderInto(context, avatarImg, prof.avatarType, prof.customAvatarPath, 82, isActive)

            val nameTv = TextView(context).apply {
                text = prof.name
                textSize = 14f
                setTypeface(null, if (isActive) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (isActive) palette.textPrimary else palette.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, UiUtils.dp(context, 10), 0, 0)
            }

            itemCol.addView(avatarImg)
            itemCol.addView(nameTv)

            UiUtils.attachSingleFocus(itemCol, palette, cornerRadiusDp = 18f, scaleUp = 1.06f) { focused ->
                AvatarRenderer.renderInto(context, avatarImg, prof.avatarType, prof.customAvatarPath, 82, focused || isActive)
                nameTv.setTextColor(if (focused || isActive) palette.textPrimary else palette.textSecondary)
            }

            itemCol.setOnClickListener {
                prefs.edit { putString("active_profile_id", prof.id) }
                dialog.dismiss()
                onStateChanged()
            }

            itemCol.setOnLongClickListener {
                dialog.dismiss()
                showProfileEditorDialog(prof, onDismissRestoreFocus)
                true
            }

            profilesRow.addView(itemCol)
        }

        val addCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(UiUtils.dp(context, 14), UiUtils.dp(context, 12), UiUtils.dp(context, 14), UiUtils.dp(context, 12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = UiUtils.dp(context, 8)
                marginEnd = UiUtils.dp(context, 8)
            }
        }
        val addCircle = TextView(context).apply {
            text = "+"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(palette.textPrimary)
            layoutParams = LinearLayout.LayoutParams(UiUtils.dp(context, 82), UiUtils.dp(context, 82))
            background = UiUtils.createAdaptiveDrawable(context, palette.cardSurface, palette.cardBorderNormal, 1.5f, 41f)
        }
        val addLabel = TextView(context).apply {
            text = "Adicionar"
            textSize = 14f
            setTextColor(palette.textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, UiUtils.dp(context, 10), 0, 0)
        }
        addCol.addView(addCircle)
        addCol.addView(addLabel)

        UiUtils.attachSingleFocus(addCol, palette, cornerRadiusDp = 18f, scaleUp = 1.06f) { focused ->
            addLabel.setTextColor(if (focused) palette.textPrimary else palette.textSecondary)
        }
        addCol.setOnClickListener {
            dialog.dismiss()
            showProfileEditorDialog(null, onDismissRestoreFocus)
        }
        profilesRow.addView(addCol)

        val horizScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(profilesRow)
        }
        root.addView(horizScroll)

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, UiUtils.dp(context, 8), 0, 0)
        }

        val btnEditActive = createDialogButton(palette, "Editar Perfil (${activeProfile.name})", false) {
            dialog.dismiss()
            showProfileEditorDialog(activeProfile, onDismissRestoreFocus)
        }
        val btnSettings = createDialogButton(palette, "Configurações", false) {
            dialog.dismiss()
            showLauncherSettingsDialog(onDismissRestoreFocus)
        }

        actionsRow.addView(btnEditActive)
        actionsRow.addView(btnSettings)
        root.addView(actionsRow)

        dialog.setContentView(root)
        dialog.setOnDismissListener { onDismissRestoreFocus?.invoke() }
        dialog.show()
    }

    fun showProfileEditorDialog(existingProfile: UserProfile?, onDismissRestoreFocus: (() -> Unit)? = null) {
        val palette = ThemeManager.getPalette(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        var selectedAvatarType = existingProfile?.avatarType ?: "PRESET_PERSON"
        var selectedCustomPath: String? = existingProfile?.customAvatarPath

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiUtils.dp(context, 36), UiUtils.dp(context, 30), UiUtils.dp(context, 36), UiUtils.dp(context, 30))
            background = UiUtils.createAdaptiveDrawable(context, palette.bgDialog, palette.cardBorderNormal, 1.5f, 22f)
        }

        val leftCol = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(UiUtils.dp(context, 130), UiUtils.dp(context, 130)).apply {
                marginEnd = UiUtils.dp(context, 32)
            }
        }
        val avatarPreview = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(UiUtils.dp(context, 120), UiUtils.dp(context, 120), Gravity.CENTER)
        }
        leftCol.addView(avatarPreview)

        fun refreshPreview() {
            AvatarRenderer.renderInto(context, avatarPreview, selectedAvatarType, selectedCustomPath, 120, true)
        }
        refreshPreview()

        val rightCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(UiUtils.dp(context, 340), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val lblName = TextView(context).apply {
            text = "Nome do perfil"
            textSize = 13f
            setTextColor(palette.textSecondary)
        }
        val inputName = EditText(context).apply {
            hint = "Digite o nome"
            setHintTextColor(palette.textSecondary)
            setText(existingProfile?.name ?: "")
            setTextColor(palette.textPrimary)
            textSize = 15f
            isSingleLine = true
            setPadding(UiUtils.dp(context, 14), UiUtils.dp(context, 10), UiUtils.dp(context, 14), UiUtils.dp(context, 10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = UiUtils.dp(context, 6)
                bottomMargin = UiUtils.dp(context, 16)
            }
        }
        UiUtils.attachSingleFocus(inputName, palette, cornerRadiusDp = 10f, scaleUp = 1.02f)

        val lblAvatars = TextView(context).apply {
            text = "Escolha uma foto ou avatar"
            textSize = 13f
            setTextColor(palette.textSecondary)
        }

        val avatarsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(0, UiUtils.dp(context, 10), 0, UiUtils.dp(context, 22))
            clipChildren = false
            clipToPadding = false
        }

        AvatarRenderer.PRESET_KEYS.forEach { presetKey ->
            val presetBtn = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(UiUtils.dp(context, 44), UiUtils.dp(context, 44)).apply {
                    marginEnd = UiUtils.dp(context, 10)
                }
                setPadding(UiUtils.dp(context, 2), UiUtils.dp(context, 2), UiUtils.dp(context, 2), UiUtils.dp(context, 2))
            }
            AvatarRenderer.renderInto(context, presetBtn, presetKey, null, 44, selectedAvatarType == presetKey)
            UiUtils.attachSingleFocus(presetBtn, palette, cornerRadiusDp = 22f, scaleUp = 1.08f)
            presetBtn.setOnClickListener {
                selectedAvatarType = presetKey
                selectedCustomPath = null
                refreshPreview()
            }
            avatarsRow.addView(presetBtn)
        }

        val btnGallery = TextView(context).apply {
            text = "+"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(palette.textPrimary)
            layoutParams = LinearLayout.LayoutParams(UiUtils.dp(context, 44), UiUtils.dp(context, 44))
        }
        UiUtils.attachSingleFocus(btnGallery, palette, cornerRadiusDp = 22f, scaleUp = 1.08f)
        btnGallery.setOnClickListener {
            onRequestGalleryPick { savedPath ->
                selectedAvatarType = "CUSTOM"
                selectedCustomPath = savedPath
                refreshPreview()
            }
        }
        avatarsRow.addView(btnGallery)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        if (existingProfile != null && existingProfile.id != "default") {
            val btnDelete = createDialogButton(palette, "Excluir", false) {
                AlertDialog.Builder(context)
                    .setTitle("Excluir Perfil")
                    .setMessage("Deseja excluir o perfil '${existingProfile.name}'?")
                    .setPositiveButton("Excluir") { _, _ ->
                        val ids = prefs.getStringSet("profile_ids", setOf("default"))?.toMutableSet() ?: mutableSetOf("default")
                        ids.remove(existingProfile.id)
                        prefs.edit {
                            putStringSet("profile_ids", ids)
                            remove("profile_name_${existingProfile.id}")
                            remove("profile_avatar_type_${existingProfile.id}")
                            remove("profile_avatar_uri_${existingProfile.id}")
                            if (prefs.getString("active_profile_id", "default") == existingProfile.id) {
                                putString("active_profile_id", "default")
                            }
                        }
                        dialog.dismiss()
                        onStateChanged()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            buttonsRow.addView(btnDelete)
        }

        val btnCancel = createDialogButton(palette, "Cancelar", false) { dialog.dismiss() }
        val btnSave = createDialogButton(palette, "Salvar", true) {
            val finalName = inputName.text.toString().trim().ifEmpty { "Perfil" }
            val profId = existingProfile?.id ?: "prof_${System.currentTimeMillis()}"
            val ids = prefs.getStringSet("profile_ids", setOf("default"))?.toMutableSet() ?: mutableSetOf("default")
            ids.add(profId)

            prefs.edit {
                putStringSet("profile_ids", ids)
                putString("profile_name_$profId", finalName)
                putString("profile_avatar_type_$profId", selectedAvatarType)
                if (selectedCustomPath != null) {
                    putString("profile_avatar_uri_$profId", selectedCustomPath)
                } else {
                    remove("profile_avatar_uri_$profId")
                }
                putString("active_profile_id", profId)
            }
            Toast.makeText(context, "Perfil '$finalName' salvo!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onStateChanged()
        }

        buttonsRow.addView(btnCancel)
        buttonsRow.addView(btnSave)

        rightCol.addView(lblName)
        rightCol.addView(inputName)
        rightCol.addView(lblAvatars)
        rightCol.addView(avatarsRow)
        rightCol.addView(buttonsRow)

        root.addView(leftCol)
        root.addView(rightCol)

        dialog.setContentView(root)
        dialog.setOnDismissListener { onDismissRestoreFocus?.invoke() }
        dialog.show()
    }

    fun showLauncherSettingsDialog(onDismissRestoreFocus: (() -> Unit)? = null) {
        val palette = ThemeManager.getPalette(context)
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val outerScroll = ScrollView(context).apply {
            background = UiUtils.createAdaptiveDrawable(context, palette.bgDialog, palette.cardBorderNormal, 1.5f, 22f)
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiUtils.dp(context, 36), UiUtils.dp(context, 28), UiUtils.dp(context, 36), UiUtils.dp(context, 28))
            layoutParams = ViewGroup.LayoutParams(UiUtils.dp(context, 520), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(context).apply {
            text = "Configurações do Launcher"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            setPadding(0, 0, 0, UiUtils.dp(context, 16))
        }
        container.addView(title)

        val lblTmdb = TextView(context).apply {
            text = "Chave da API TMDb (Privada):"
            textSize = 13f
            setTextColor(palette.textSecondary)
        }
        val inputTmdb = EditText(context).apply {
            hint = "Insira sua chave API TMDb"
            setHintTextColor(palette.textSecondary)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("tmdb_api_key", ""))
            setTextColor(palette.textPrimary)
            textSize = 14f
            setPadding(UiUtils.dp(context, 12), UiUtils.dp(context, 8), UiUtils.dp(context, 12), UiUtils.dp(context, 8))
        }
        UiUtils.attachSingleFocus(inputTmdb, palette, cornerRadiusDp = 10f, scaleUp = 1.02f)
        container.addView(lblTmdb)
        container.addView(inputTmdb)

        fun createThemedSwitch(label: String, checked: Boolean): Switch {
            return Switch(context).apply {
                text = label
                isChecked = checked
                setTextColor(palette.textPrimary)
                textSize = 14f
                setPadding(UiUtils.dp(context, 10), UiUtils.dp(context, 10), UiUtils.dp(context, 10), UiUtils.dp(context, 10))
                UiUtils.attachSingleFocus(this, palette, cornerRadiusDp = 10f, scaleUp = 1.02f)
            }
        }

        val switchContinue = createThemedSwitch("Exibir fileira 'Continuar Assistindo'", prefs.getBoolean("show_continue_watching", true))
        val switchPersonalized = createThemedSwitch("Recomendações personalizadas por perfil", prefs.getBoolean("personalized_recs", true))
        val switchWhiteTheme = createThemedSwitch("Modo Claro (Tema Claro Completo)", prefs.getString("theme_mode", "BLACK") == "WHITE")

        container.addView(switchContinue)
        container.addView(switchPersonalized)
        container.addView(switchWhiteTheme)

        var currentAppPct = (getAppIconScale() * 100).toInt().coerceIn(80, 135)
        val lblAppScale = TextView(context).apply {
            text = "Tamanho dos Ícones de Aplicativos: $currentAppPct%"
            textSize = 13f
            setTextColor(palette.textSecondary)
            setPadding(0, UiUtils.dp(context, 12), 0, UiUtils.dp(context, 4))
        }
        val seekAppScale = SeekBar(context).apply {
            max = 55
            progress = currentAppPct - 80
            setPadding(UiUtils.dp(context, 12), UiUtils.dp(context, 8), UiUtils.dp(context, 12), UiUtils.dp(context, 8))
            UiUtils.attachSingleFocus(this, palette, cornerRadiusDp = 10f, scaleUp = 1.02f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentAppPct = 80 + progress
                    lblAppScale.text = "Tamanho dos Ícones de Aplicativos: $currentAppPct%"
                    if (fromUser) {
                        prefs.edit { putInt("app_icon_scale_pct", currentAppPct) }
                        onStateChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(lblAppScale)
        container.addView(seekAppScale)

        var currentPosterPct = (getPosterCardScale() * 100).toInt().coerceIn(80, 135)
        val lblPosterScale = TextView(context).apply {
            text = "Tamanho dos Pôsteres e Cards: $currentPosterPct%"
            textSize = 13f
            setTextColor(palette.textSecondary)
            setPadding(0, UiUtils.dp(context, 10), 0, UiUtils.dp(context, 4))
        }
        val seekPosterScale = SeekBar(context).apply {
            max = 55
            progress = currentPosterPct - 80
            setPadding(UiUtils.dp(context, 12), UiUtils.dp(context, 8), UiUtils.dp(context, 12), UiUtils.dp(context, 8))
            UiUtils.attachSingleFocus(this, palette, cornerRadiusDp = 10f, scaleUp = 1.02f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    currentPosterPct = 80 + progress
                    lblPosterScale.text = "Tamanho dos Pôsteres e Cards: $currentPosterPct%"
                    if (fromUser) {
                        prefs.edit { putInt("poster_card_scale_pct", currentPosterPct) }
                        onStateChanged()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(lblPosterScale)
        container.addView(seekPosterScale)

        val auxRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            setPadding(0, UiUtils.dp(context, 14), 0, UiUtils.dp(context, 14))
        }

        val btnEditProf = createDialogButton(palette, "Editar Perfil", false) {
            dialog.dismiss()
            showProfileEditorDialog(getActiveProfile(), onDismissRestoreFocus)
        }

        val btnHomeSetup = createDialogButton(palette, "Botão Home TV", false) {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Toast.makeText(context, "Ative 'Clean Launcher - Botão Home'", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        val btnResetDefaults = createDialogButton(palette, "Restaurar Padrão", false) {
            AlertDialog.Builder(context)
                .setTitle("Restaurar Preferências Visuais")
                .setMessage("Voltar tema e tamanhos para o padrão (100% / Modo Escuro)?")
                .setPositiveButton("Restaurar") { _, _ ->
                    prefs.edit {
                        putString("theme_mode", "BLACK")
                        putInt("app_icon_scale_pct", 100)
                        putInt("poster_card_scale_pct", 100)
                        putInt("icon_scale_offset", 15)
                    }
                    Toast.makeText(context, "Preferências restauradas!", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    onStateChanged()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        auxRow.addView(btnEditProf)
        auxRow.addView(btnHomeSetup)
        auxRow.addView(btnResetDefaults)
        container.addView(auxRow)

        val footerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val btnCancel = createDialogButton(palette, "Cancelar", false) { dialog.dismiss() }
        val btnSave = createDialogButton(palette, "Salvar", true) {
            prefs.edit {
                putString("tmdb_api_key", inputTmdb.text.toString().trim())
                putBoolean("show_continue_watching", switchContinue.isChecked)
                putBoolean("personalized_recs", switchPersonalized.isChecked)
                putString("theme_mode", if (switchWhiteTheme.isChecked) "WHITE" else "BLACK")
                putInt("app_icon_scale_pct", currentAppPct)
                putInt("poster_card_scale_pct", currentPosterPct)
            }
            Toast.makeText(context, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            onStateChanged()
        }
        footerRow.addView(btnCancel)
        footerRow.addView(btnSave)
        container.addView(footerRow)

        outerScroll.addView(container)
        dialog.setContentView(outerScroll)
        dialog.setOnDismissListener { onDismissRestoreFocus?.invoke() }
        dialog.show()
    }

    private fun createDialogButton(
        palette: ThemePalette,
        label: String,
        isPrimary: Boolean,
        onClick: () -> Unit
    ): Button {
        val fill = if (isPrimary) palette.accentCyan else palette.cardSurface
        val textCol = if (isPrimary) Color.WHITE else palette.textPrimary
        return Button(context).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            setTextColor(textCol)
            setPadding(UiUtils.dp(context, 16), UiUtils.dp(context, 8), UiUtils.dp(context, 16), UiUtils.dp(context, 8))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                UiUtils.dp(context, 40)
            ).apply {
                marginEnd = UiUtils.dp(context, 10)
            }
            UiUtils.attachSingleFocus(
                this,
                palette,
                cornerRadiusDp = 20f,
                scaleUp = 1.05f,
                normalFill = fill,
                focusedFill = if (isPrimary) palette.accentCyan else palette.cardSurfaceFocused
            )
            setOnClickListener { onClick() }
        }
    }
}
