package com.ulisescervera.uci.core.format

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.ulisescervera.uci.core.share.PropertySharer
import com.ulisescervera.uci.support.AppFixtures
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "es")
class PropertySharerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sharer = PropertySharer(PropertyFormatter(context))

    @Test
    fun `the deep link matches the pattern declared in the navigation graph`() {
        // If these drift apart, a shared link opens a browser instead of the app.
        assertThat(sharer.deepLinkFor("42"))
            .isEqualTo("https://ulisescervera.dev/uci/property/42")
    }

    @Test
    fun `the shared text carries the title, the price and the link`() {
        val intent = sharer.intentFor(context, AppFixtures.property("1"))

        // createChooserIntent wraps the real intent.
        val target = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: intent
        val text = target.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        assertThat(text).contains("Piso - Castellana - Madrid")
        assertThat(text).contains("€")
        assertThat(text).contains("https://ulisescervera.dev/uci/property/1")
    }

    @Test
    fun `the chooser is a plain text share`() {
        val intent = sharer.intentFor(context, AppFixtures.property("1"))
        val target = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: intent

        assertThat(target.type).isEqualTo("text/plain")
        assertThat(target.action).isEqualTo(Intent.ACTION_SEND)
    }
}
