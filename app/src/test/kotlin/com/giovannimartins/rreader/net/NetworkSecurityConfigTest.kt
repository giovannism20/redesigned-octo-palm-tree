package com.giovannimartins.rreader.net

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.catalog.CatalogHosts
import com.giovannimartins.rreader.tts.neural.VoiceModelHosts
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/** SR-5: the network security config forbids cleartext and trusts exactly the hosts the allowlists in code name. */
@RunWith(RobolectricTestRunner::class)
class NetworkSecurityConfigTest {
    private val tags = networkSecurityConfig()

    @Test
    fun `no cleartext and no trusted authority outside the allowlisted hosts`() {
        assertThat(tags.single { it.name == "base-config" }.attributes["cleartextTrafficPermitted"]).isEqualTo("false")
        assertThat(tags.filter { it.section == "base-config" }.map { it.name })
            .containsExactly("base-config", "trust-anchors")
        assertThat(tags.filter { it.name == "domain-config" }.map { it.attributes["cleartextTrafficPermitted"] })
            .containsExactly("false", "false")
        assertThat(tags.filter { it.name == "certificates" }.map { it.section to it.attributes["src"] })
            .containsExactly("domain-config" to "system", "domain-config" to "system")
    }

    @Test
    fun `trusts exactly the catalogue's hosts and the voice hosts, without subdomains`() {
        val domains = tags.filter { it.name == "domain" }.map { it.text to it.attributes["includeSubdomains"] }

        val expected = (CatalogHosts.HOSTS + VoiceModelHosts.HOSTS).map { it to "false" }
        assertThat(domains).containsExactlyElementsIn(expected)
    }

    @Test
    fun `the voice hosts are reached over HTTPS only, by exact name`() {
        listOf(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-cadu-medium.tar.bz2",
            "https://release-assets.githubusercontent.com/github-production-release-asset/531380835/abc?sp=r&sig=x",
            "https://objects.githubusercontent.com/github-production-release-asset-2e65be/531380835/abc",
            "HTTPS://GITHUB.COM:443/k2-fsa/sherpa-onnx/releases",
        ).forEach { address -> assertThat(VoiceModelHosts.check(address)).isNotNull() }
        listOf(
            "http://github.com/k2-fsa/sherpa-onnx/releases",
            "https://api.github.com/repos/k2-fsa/sherpa-onnx/releases",
            "https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/README.md",
            "https://github.com.evil.example/model.tar.bz2",
            "https://evilgithub.com/model.tar.bz2",
            "https://user@github.com/k2-fsa/sherpa-onnx/releases",
            "https://github.com:8443/k2-fsa/sherpa-onnx/releases",
            "https://huggingface.co/model.tar.bz2",
            "https://www.gutenberg.org/ebooks/55752.epub3.images",
        ).forEach { address -> assertThat(VoiceModelHosts.check(address)).isNull() }
    }

    /** An element of `res/xml/network_security_config.xml`, in the `*-config` section it belongs to. */
    private data class Tag(
        val name: String,
        val section: String,
        val attributes: Map<String, String?>,
        val text: String?,
    )

    private fun networkSecurityConfig(): List<Tag> {
        val context: Context = ApplicationProvider.getApplicationContext()
        val parser = context.resources.getXml(R.xml.network_security_config)
        val tags = mutableListOf<Tag>()
        var section = ""
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                if (parser.name.endsWith("-config")) section = parser.name
                val attributes =
                    (0 until parser.attributeCount).associate { index ->
                        parser.getAttributeName(index) to parser.getAttributeValue(index)
                    }
                val text = if (parser.name == "domain") parser.nextText().trim() else null
                tags += Tag(parser.name, section, attributes, text)
            }
        }
        return tags
    }
}
