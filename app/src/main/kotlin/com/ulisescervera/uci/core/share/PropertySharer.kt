package com.ulisescervera.uci.core.share

import android.content.Context
import android.content.Intent
import androidx.core.app.ShareCompat
import com.ulisescervera.uci.R
import com.ulisescervera.uci.core.format.PropertyFormatter
import com.ulisescervera.uci.domain.model.Property
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds the share intent for a property.
 *
 * The shared text carries the app's own deep link, so a link sent over WhatsApp
 * reopens the exact property in UCI on a device that has it installed and falls
 * back to the https:// mirror everywhere else. Both URIs are declared in
 * `uci_nav_graph.xml`; [DEEP_LINK_TEMPLATE] is the one place that formats them.
 *
 * `ShareCompat` rather than a hand-rolled `Intent`: it sets the mime type, adds
 * `FLAG_ACTIVITY_NEW_DOCUMENT` and produces a chooser that behaves correctly on
 * every API level down to 24.
 */
@Singleton
class PropertySharer @Inject constructor(
    private val formatter: PropertyFormatter,
) {

    fun intentFor(context: Context, property: Property): Intent {
        val title = formatter.title(property)
        val price = formatter.price(property.price)
        val link = deepLinkFor(property.id)

        return ShareCompat.IntentBuilder(context)
            .setType(MIME_TYPE)
            .setSubject(title)
            .setText(context.getString(R.string.uci_share_body, title, price, link))
            .setChooserTitle(R.string.uci_share_chooser)
            .createChooserIntent()
    }

    fun deepLinkFor(propertyId: String): String = DEEP_LINK_TEMPLATE.format(propertyId)

    private companion object {
        const val MIME_TYPE = "text/plain"

        /** Must stay in sync with the `<deepLink>` in uci_nav_graph.xml. */
        const val DEEP_LINK_TEMPLATE = "https://ulisescervera.dev/uci/property/%s"
    }
}
