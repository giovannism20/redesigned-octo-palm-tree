package com.giovannimartins.rreader.ui.voices

import android.content.Context
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.playback.voice
import com.giovannimartins.rreader.tts.Voice
import com.giovannimartins.rreader.tts.neural.NeuralVoice
import com.giovannimartins.rreader.tts.neural.NeuralVoices
import com.giovannimartins.rreader.tts.neural.VoiceDownloadFailure
import com.giovannimartins.rreader.ui.assertAllTextReadableOn
import com.giovannimartins.rreader.ui.components.ScreenSurface
import com.giovannimartins.rreader.ui.theme.Ink
import com.giovannimartins.rreader.ui.theme.OnSeal
import com.giovannimartins.rreader.ui.theme.RReaderTheme
import com.giovannimartins.rreader.ui.theme.Seal
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** The Voices tab on a phone screen: names, emblems' descriptions, "In use" in words, touch targets, sections. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w384dp-h780dp")
class VoicesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<String>()
    private val dora = NeuralVoices.all.first()

    @Test
    fun `every voice has its own name and description, and no licence terms`() {
        show(state())

        val context = ApplicationProvider.getApplicationContext<Context>()
        NeuralVoices.all.forEach { voice ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(voice.name))
            compose.onNodeWithText(voice.name).assertExists()
            compose.onNodeWithText(context.getString(VoiceLooks.of(voice).description)).assertExists()
        }
        listOf("Apache", "CC BY", "CC0", "GPL", "non-commercial", "licence").forEach { term ->
            val found = compose.onAllNodesWithText(term, substring = true, ignoreCase = true).fetchSemanticsNodes()
            assertThat(found).isEmpty()
        }
    }

    @Test
    fun `the voice in use says so in words, with no button to choose it again`() {
        show(state(ready = setOf("dora", "alex", "santa"), inUse = "dora"))

        compose.onNodeWithText("In use").assertExists()
        assertThat(compose.onAllNodesWithContentDescriptionText("Use the voice Dora")).isEmpty()
        compose.onNodeWithContentDescription("Use the voice Alex").assertExists()
    }

    @Test
    fun `listening, downloading and choosing are large enough to touch`() {
        show(state(ready = setOf("alex")))

        compose.onNodeWithContentDescription("Listen to Dora").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithContentDescription("Download the voice Dora", substring = true).assertHeightIsAtLeast(56.dp)
        compose.onNodeWithContentDescription("Use the voice Alex").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithContentDescription("Delete the voice Alex").assertHeightIsAtLeast(56.dp)

        compose.onNodeWithContentDescription("Listen to Dora").performClick()
        compose.onNodeWithContentDescription("Download the voice Dora", substring = true).performClick()
        assertThat(actions).containsExactly("sample dora", "download dora").inOrder()
    }

    @Test
    fun `a download shows its progress and can be cancelled`() {
        show(state(status = mapOf("dora" to NeuralVoiceStatus.Downloading(0.45f))))

        compose.onNodeWithText("Downloading… 45%").assertExists()
        compose.onNodeWithContentDescription("Cancel the download of Dora").performClick()
        assertThat(actions).containsExactly("cancel dora")
    }

    @Test
    fun `a download that failed says why and can be tried again or removed`() {
        show(state(status = mapOf("dora" to NeuralVoiceStatus.Failed(VoiceDownloadFailure.NETWORK))))

        compose.onNodeWithText("The download stopped because the internet connection failed.").assertExists()
        compose.onNodeWithContentDescription("Try downloading Dora again").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithContentDescription("Remove the download of Dora").assertHeightIsAtLeast(56.dp)
        compose.onNodeWithText("Remove").assertExists()
        compose.onNodeWithContentDescription("Try downloading Dora again").performClick()
        compose.onNodeWithContentDescription("Remove the download of Dora").performClick()
        assertThat(actions).containsExactly("download dora", "cancel dora").inOrder()
    }

    @Test
    fun `the phone's other voices fold away and have names of their own`() {
        val voices = listOf(phoneVoice("pt-br-x-afs-local"), phoneVoice("pt-br-x-pte-local"))
        show(state().copy(otherPhoneVoices = PhoneVoiceLabels.of(voices).map { PhoneVoiceCard(it, inUse = false) }))

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Other phone voices"))
        assertThat(compose.onAllNodesWithText("Google · Voice 1").fetchSemanticsNodes()).isEmpty()
        compose.onNodeWithText("Other phone voices").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Google · Voice 2"))
        compose.onNodeWithText("Google · Voice 1").assertExists()
        compose.onNodeWithText("Google · Voice 2").assertExists()
    }

    @Test
    fun `About and licences is at the end of the list`() {
        show(state())

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("About and licences"))
        compose.onNodeWithText("About and licences").performClick()

        assertThat(actions).containsExactly("about")
    }

    @Test
    fun `all text is readable on the dark background`() {
        val status =
            mapOf(
                "faber" to NeuralVoiceStatus.Downloading(0.2f),
                "dora" to NeuralVoiceStatus.Failed(VoiceDownloadFailure.STORAGE),
            )
        show(state(ready = setOf("cadu"), inUse = "cadu", status = status))

        compose.assertAllTextReadableOn(Ink, containers = mapOf(OnSeal to Seal))
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onAllNodesWithContentDescriptionText(
        description: String,
    ) = onAllNodes(
        androidx.compose.ui.test
            .hasContentDescription(description),
    ).fetchSemanticsNodes()

    private fun state(
        ready: Set<String> = emptySet(),
        inUse: String? = null,
        status: Map<String, NeuralVoiceStatus> = emptyMap(),
    ) = VoicesUiState(
        neural =
            NeuralVoices.all.map { voice ->
                NeuralVoiceCard(
                    voice = voice,
                    status = status[voice.key] ?: statusOf(voice.key in ready),
                    inUse = voice.key == inUse,
                    sharesWith = NeuralVoices.voicesOf(voice.pkg).filter { it != voice }.map(NeuralVoice::name),
                )
            },
        phoneInUse = inUse == null,
        phoneVoiceAvailable = true,
    )

    private fun statusOf(ready: Boolean) = if (ready) NeuralVoiceStatus.Ready else NeuralVoiceStatus.NotDownloaded

    private fun phoneVoice(name: String): Voice =
        voice(id = "system:com.google.android.tts:$name", locale = Locale.forLanguageTag("pt-BR"))
            .copy(engine = "com.google.android.tts", name = name)

    private fun show(state: VoicesUiState) {
        compose.setContent {
            // The app shell draws the tabs on the screen surface.
            RReaderTheme {
                ScreenSurface {
                    VoicesScreen(
                        state = state,
                        onToggleSample = { actions += "sample ${it.key}" },
                        onTogglePhoneSample = { actions += "phone sample" },
                        onDownload = { actions += "download ${it.key}" },
                        onCancelDownload = { actions += "cancel ${it.key}" },
                        onUse = { actions += "use $it" },
                        onDelete = { actions += "delete ${it.key}" },
                        onConfirmDelete = {},
                        onDismissDelete = {},
                        onAnswerMobileData = {},
                        onOpenSystemSettings = { actions += "settings" },
                        onOpenAbout = { actions += "about" },
                    )
                }
            }
        }
    }
}
