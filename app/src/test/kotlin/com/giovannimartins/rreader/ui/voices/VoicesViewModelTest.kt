package com.giovannimartins.rreader.ui.voices

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import com.giovannimartins.rreader.data.settings.SettingsRepository
import com.giovannimartins.rreader.net.Connection
import com.giovannimartins.rreader.playback.voice
import com.giovannimartins.rreader.tts.AudioCache
import com.giovannimartins.rreader.tts.VoiceIds
import com.giovannimartins.rreader.tts.neural.FakeNeuralRuntime
import com.giovannimartins.rreader.tts.neural.NeuralCallMarkers
import com.giovannimartins.rreader.tts.neural.NeuralVoiceLibrary
import com.giovannimartins.rreader.tts.neural.NeuralVoices
import com.giovannimartins.rreader.tts.neural.SherpaOnnxEngine
import com.giovannimartins.rreader.tts.neural.VoiceDownloadFailure
import com.giovannimartins.rreader.tts.neural.VoiceDownloadState
import com.giovannimartins.rreader.tts.neural.VoicePackage
import com.giovannimartins.rreader.tts.neural.VoicePackageStore
import com.giovannimartins.rreader.ui.player.RecordingControls
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/** The Voices tab's state and actions, over a real package store and settings, with fake downloads and players. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VoicesViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val downloads = FakeVoiceDownloads()
    private val controls = RecordingControls()
    private val samples = RecordingSamples()
    private var connection = Connection.UNMETERED
    private val brazil = Locale.forLanguageTag("pt-BR")
    private val google1 = voice(id = "system:com.google.android.tts:pt-br-x-afs-local", locale = brazil)
    private val google2 = voice(id = "system:com.google.android.tts:pt-br-x-pte-local", locale = brazil)
    private val phone = FakePhoneEngine(listOf(google1, google2).map { it.copy(engine = "com.google.android.tts") })
    private val dataScope = CoroutineScope(UnconfinedTestDispatcher())
    private val viewModels = mutableListOf<VoicesViewModel>()
    private lateinit var store: VoicePackageStore
    private lateinit var settings: SettingsRepository
    private val dora = NeuralVoices.all.first { it.key == "dora" }
    private val cadu = NeuralVoices.all.first { it.key == "cadu" }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store = VoicePackageStore(File(folder.root, "voices"), Dispatchers.Unconfined)
        settings =
            SettingsRepository(
                PreferenceDataStoreFactory.create(scope = dataScope) {
                    File(folder.root, "settings.preferences_pb")
                },
            )
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        dataScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `lists the nine voices, most recommended first, with the phone's voice in use until another is chosen`() {
        val state = state(viewModel()) { it.neural.isNotEmpty() && it.phoneVoiceAvailable != null }

        assertThat(state.neural.map { it.voice.name })
            .containsExactly("Dora", "Alex", "Cadu", "Santa", "Faber", "Dii", "Jeff", "Miro", "Edresson")
            .inOrder()
        assertThat(state.neural.map { it.status }.toSet()).containsExactly(NeuralVoiceStatus.NotDownloaded)
        assertThat(state.neural.first().sharesWith).containsExactly("Alex", "Santa").inOrder()
        assertThat(state.neural.first { it.voice == cadu }.sharesWith).isEmpty()
        assertThat(state.phoneInUse).isTrue()
        assertThat(state.phoneVoiceAvailable).isTrue()
    }

    @Test
    fun `downloads on Wi-Fi at once and asks before using mobile data`() {
        val viewModel = viewModel()

        viewModel.download(dora)
        connection = Connection.METERED
        viewModel.download(cadu)
        assertThat(state(viewModel) { it.askMobileData != null }.askMobileData).isEqualTo(cadu)
        viewModel.answerMobileData(true)
        viewModel.download(NeuralVoices.all.first { it.key == "faber" })
        viewModel.answerMobileData(false)
        viewModel.download(NeuralVoices.all.first { it.key == "jeff" })
        viewModel.answerMobileData(null)
        connection = Connection.NONE
        viewModel.download(NeuralVoices.all.first { it.key == "miro" })

        assertThat(downloads.requests)
            .containsExactly("kokoro" to true, "cadu" to false, "faber" to true, "miro" to true)
            .inOrder()
        assertThat(state(viewModel) { it.askMobileData == null }.askMobileData).isNull()
    }

    @Test
    fun `shows where each download stands and cancels it`() {
        val viewModel = viewModel()
        downloads.states.value =
            mapOf(
                "kokoro" to VoiceDownloadState.Downloading(0.4f),
                "cadu" to VoiceDownloadState.Failed(VoiceDownloadFailure.NETWORK),
            )

        val state = state(viewModel) { it.neural.any { card -> card.status is NeuralVoiceStatus.Downloading } }
        viewModel.cancelDownload(dora)

        assertThat(
            state.neural
                .filter { it.voice.pkg == NeuralVoices.KOKORO }
                .map { it.status }
                .toSet(),
        ).containsExactly(NeuralVoiceStatus.Downloading(0.4f))
        assertThat(
            state.neural
                .first {
                    it.voice == cadu
                }.status,
        ).isEqualTo(NeuralVoiceStatus.Failed(VoiceDownloadFailure.NETWORK))
        assertThat(downloads.cancelled).containsExactly("kokoro")
    }

    @Test
    fun `removing a download that failed offers the voice for download again`() {
        val viewModel = viewModel()
        downloads.states.value = mapOf("cadu" to VoiceDownloadState.Failed(VoiceDownloadFailure.NETWORK))
        state(viewModel) { it.neural.any { card -> card.status is NeuralVoiceStatus.Failed } }

        viewModel.cancelDownload(cadu)

        val state = state(viewModel) { it.neural.none { card -> card.status is NeuralVoiceStatus.Failed } }
        assertThat(state.neural.first { it.voice == cadu }.status).isEqualTo(NeuralVoiceStatus.NotDownloaded)
        assertThat(downloads.cancelled).containsExactly("cadu")
    }

    @Test
    fun `choosing an installed voice makes it the default and tells the player`() {
        install(NeuralVoices.CADU)
        val viewModel = viewModel()

        viewModel.use(cadu.id)

        val state = state(viewModel) { it.neural.any(NeuralVoiceCard::inUse) }
        assertThat(state.neural.single { it.inUse }.voice).isEqualTo(cadu)
        assertThat(state.neural.first { it.voice == cadu }.status).isEqualTo(NeuralVoiceStatus.Ready)
        assertThat(state.phoneInUse).isFalse()
        assertThat(runBlocking { settings.settings.first() }.defaultVoiceId).isEqualTo(cadu.id)
        assertThat(controls.calls).contains("refreshVoice")
    }

    @Test
    fun `deleting the voice in use falls back to the phone's voice at once`() {
        install(NeuralVoices.KOKORO)
        runBlocking { settings.setDefaultVoice(dora.id) }
        val viewModel = viewModel()
        state(viewModel) { it.neural.any(NeuralVoiceCard::inUse) }

        viewModel.askDelete(dora)
        assertThat(state(viewModel) { it.askDelete != null }.askDelete).isEqualTo(dora)
        viewModel.confirmDelete()

        val state = state(viewModel) { it.phoneInUse }
        assertThat(state.neural.first().status).isEqualTo(NeuralVoiceStatus.NotDownloaded)
        assertThat(store.isComplete(NeuralVoices.KOKORO)).isFalse()
        assertThat(runBlocking { settings.settings.first() }.defaultVoiceId).isEqualTo(VoiceIds.PHONE)
        assertThat(controls.calls).contains("refreshVoice")
    }

    @Test
    fun `plays a voice's bundled sample, and stops it on a second tap or at its end`() {
        val viewModel = viewModel()

        viewModel.toggleSample(dora)
        assertThat(state(viewModel) { it.sampleVoiceId != null }.sampleVoiceId).isEqualTo(dora.id)
        viewModel.toggleSample(dora)
        assertThat(state(viewModel) { it.sampleVoiceId == null }.sampleVoiceId).isNull()
        viewModel.toggleSample(cadu)
        samples.finish()

        assertThat(samples.played.map { it.toString() })
            .containsExactly("asset:///voices/samples/dora.ogg", "asset:///voices/samples/cadu.ogg")
            .inOrder()
        assertThat(state(viewModel) { it.sampleVoiceId == null }.sampleVoiceId).isNull()
        assertThat(samples.stops).isAtLeast(1)
    }

    @Test
    fun `the phone's own voice speaks its sample, and its other voices are named apart`() {
        runBlocking { settings.setDefaultVoice(google2.id) }
        val viewModel = viewModel()

        viewModel.togglePhoneSample(null, "Olá!")

        val state = state(viewModel) { it.otherPhoneVoices.isNotEmpty() }
        assertThat(phone.spoken).containsExactly(google1.id to "Olá!")
        assertThat(samples.played).hasSize(1)
        assertThat(state.otherPhoneVoices.map { it.label.engine to it.label.number })
            .containsExactly("Google" to 1, "Google" to 2)
            .inOrder()
        assertThat(state.otherPhoneVoices.map { it.inUse }).containsExactly(false, true).inOrder()
        assertThat(state.phoneInUse).isFalse()
    }

    private fun viewModel(): VoicesViewModel {
        val calls = NeuralCallMarkers(File(folder.root, "neural.inflight"))
        val engine = SherpaOnnxEngine(store, FakeNeuralRuntime(), calls, Dispatchers.Unconfined, TestScope())
        val library = NeuralVoiceLibrary(store, downloads, engine, settings)
        return VoicesViewModel(library, phone, settings, controls, { connection }, samples, AudioCache(folder.root))
            .also { viewModels += it }
    }

    private fun state(
        viewModel: VoicesViewModel,
        until: (VoicesUiState) -> Boolean,
    ): VoicesUiState = runBlocking { viewModel.state.first(until) }

    /** Puts files of the right sizes where [pkg] is installed (sparse: only their length matters). */
    private fun install(pkg: VoicePackage) {
        pkg.files.forEach { file ->
            RandomAccessFile(
                store.file(pkg, file.name).apply { parentFile?.mkdirs() },
                "rw",
            ).use { it.setLength(file.bytes) }
        }
        runBlocking { store.rescan() }
    }
}
