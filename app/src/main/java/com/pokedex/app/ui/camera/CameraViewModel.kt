package com.pokedex.app.ui.camera

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pokedex.app.core.ImageScaling
import com.pokedex.app.data.classifier.ClassificationResult
import com.pokedex.app.data.classifier.ClassifierException
import com.pokedex.app.data.classifier.PokemonClassifier
import com.pokedex.app.data.repository.PokemonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class ScanPhase { Idle, Scanning, Error }

data class CameraUiState(
    val phase: ScanPhase = ScanPhase.Idle,
    val errorMessage: String? = null,
)

sealed interface CameraNavEvent {
    data class OpenPokemon(val idOrName: String, val banner: String) : CameraNavEvent
    data class ShowResult(val payload: String) : CameraNavEvent
}

private const val CONFIDENCE_THRESHOLD = 0.5

@HiltViewModel
class CameraViewModel @Inject constructor(
    application: Application,
    private val classifier: PokemonClassifier,
    private val repository: PokemonRepository,
    private val io: CoroutineDispatcher,
) : AndroidViewModel(application) {

    private val json = Json { encodeDefaults = true }

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _events = Channel<CameraNavEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onPhotoCaptured(jpegBytes: ByteArray) = analyze { ImageScaling.fromBytes(jpegBytes) }

    fun onImagePicked(uri: Uri) = analyze {
        ImageScaling.fromUri(getApplication(), uri)
    }

    fun dismissError() {
        _state.value = CameraUiState(phase = ScanPhase.Idle)
    }

    fun onCaptureFailed(message: String) {
        fail(message)
    }

    private fun analyze(prepare: suspend () -> com.pokedex.app.core.ScaledImage) {
        if (_state.value.phase == ScanPhase.Scanning) return
        _state.value = CameraUiState(phase = ScanPhase.Scanning)
        viewModelScope.launch {
            try {
                val image = withContext(io) { prepare() }
                val result = classifier.classify(image)
                handleResult(result)
            } catch (e: ClassifierException) {
                fail(e.message ?: "Couldn't identify the image.")
            } catch (e: Exception) {
                fail(e.message ?: "Couldn't process that image.")
            }
        }
    }

    private suspend fun handleResult(result: ClassificationResult) {
        if (result.isPokemon && result.name != null && result.confidence >= CONFIDENCE_THRESHOLD) {
            val resolved = repository.resolvePokemonId(result.name)
            resolved.fold(
                onSuccess = { id ->
                    _state.value = CameraUiState(phase = ScanPhase.Idle)
                    _events.send(
                        CameraNavEvent.OpenPokemon(
                            idOrName = id.toString(),
                            banner = "Identified: ${displayName(result.name)} (${result.confidencePercent}% confident)",
                        ),
                    )
                },
                onFailure = {
                    // Recognised a Pokémon but couldn't map the name — show the result card.
                    emitResultCard(result)
                },
            )
        } else {
            emitResultCard(result)
        }
    }

    private suspend fun emitResultCard(result: ClassificationResult) {
        _state.value = CameraUiState(phase = ScanPhase.Idle)
        _events.send(CameraNavEvent.ShowResult(json.encodeToString(ClassificationResult.serializer(), result)))
    }

    private fun fail(message: String) {
        _state.value = CameraUiState(phase = ScanPhase.Error, errorMessage = message)
    }

    private fun displayName(name: String): String =
        name.split(Regex("[-\\s]")).joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
