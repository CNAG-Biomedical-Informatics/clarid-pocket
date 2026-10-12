package org.cnag.clarid.pocket

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cnag.clarid.core.IdentifierRecord
import org.cnag.clarid.core.IdentifierDecoder
import org.cnag.clarid.core.IdentifierEntity
import org.cnag.clarid.core.ClarIdException
import org.cnag.clarid.core.DecodeSettings
import org.cnag.clarid.core.IdentifierFormat
import org.cnag.clarid.core.ReferenceData

data class PocketState(
    val checkingUpdates: Boolean = false,
    val appUpdate: AppUpdate? = null,
    val loading: Boolean = true,
    val ready: Boolean = false,
    val settings: DecodeSettings = DecodeSettings(),
    val revision: String = "",
    val specificationVersion: String = "",
    val scanning: Boolean = false,
    val processing: Boolean = false,
    val result: IdentifierRecord? = null,
    val identifier: String? = null,
    val detectedFormat: IdentifierFormat? = null,
    val error: String? = null,
)

class PocketViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("decode-settings", 0)
    private val mutableState = MutableStateFlow(PocketState(settings = restoredSettings()))
    val state = mutableState.asStateFlow()
    private var decoder: IdentifierDecoder? = null

    init {
        viewModelScope.launch {
            try {
                val reference = withContext(Dispatchers.IO) { ReferenceData.bundled() }
                decoder = IdentifierDecoder(reference)
                mutableState.update {
                    it.copy(loading = false, ready = true,
                        specificationVersion = reference.codebook.version,
                        revision = reference.codebook.localVersion ?: "Bundled codebook")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(loading = false,
                        error = "The bundled codebook could not be loaded. Restart or reinstall the app.")
                }
            }
        }
    }

    fun checkForUpdates() {
        if (state.value.checkingUpdates) return
        mutableState.update { it.copy(checkingUpdates = true, appUpdate = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { AppUpdates().check(BuildConfig.VERSION_NAME) }
                mutableState.update { it.copy(appUpdate = result) }
            } finally {
                mutableState.update { it.copy(checkingUpdates = false) }
            }
        }
    }

    fun configure(settings: DecodeSettings) {
        if (state.value.scanning || state.value.processing) return
        settings.validate()
        preferences.edit {
            putString("format-v2", settings.format.name)
            putString("entity", settings.entity.name)
            putInt("human-width", settings.humanSubjectWidth)
            putInt("stub-width", settings.stubSubjectWidth)
        }
        mutableState.update { it.copy(settings = settings, result = null, identifier = null,
            detectedFormat = null, error = null) }
    }

    fun startScan() {
        if (!state.value.ready || state.value.processing) return
        mutableState.update { it.copy(scanning = true, result = null, identifier = null,
            detectedFormat = null, error = null) }
    }

    fun stopScan() { mutableState.update { it.copy(scanning = false) } }

    fun scanError(message: String) {
        mutableState.update { it.copy(scanning = false, error = message) }
    }

    fun scanned(text: String) {
        val before = state.value
        val activeDecoder = decoder ?: return
        if (!before.scanning || before.processing) return
        mutableState.update { it.copy(scanning = false, processing = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    activeDecoder.decode(text, before.settings)
                }
                mutableState.update { it.copy(processing = false, result = result.record,
                    detectedFormat = result.format, identifier = text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ClarIdException) {
                mutableState.update { it.copy(processing = false, error = e.message) }
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(processing = false,
                        error = "Could not decode this QR. Check the format, width, and codebook.")
                }
            }
        }
    }

    private fun restoredSettings(): DecodeSettings = DecodeSettings(
        format = IdentifierFormat.entries.firstOrNull {
            it.name == preferences.getString("format-v2", null)
        } ?: IdentifierFormat.AUTO,
        humanSubjectWidth = preferences.getInt("human-width", 5).coerceIn(1, 18),
        stubSubjectWidth = preferences.getInt("stub-width", 3).coerceIn(1, 10),
        entity = IdentifierEntity.entries.firstOrNull {
            it.name == preferences.getString("entity", null)
        } ?: IdentifierEntity.AUTO,
    )
}
