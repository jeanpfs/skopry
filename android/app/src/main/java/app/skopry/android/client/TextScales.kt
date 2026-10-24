package app.skopry.android.client

import org.json.JSONObject

/**
 * The Aa choice (Viewport.scale) each app keeps. One size does not suit every app (the crisp 3/4× that suits a
 * terminal is small for a browser), so a choice made while an app's window streams belongs to that app, and an app
 * never chosen for follows the default: what Aa was before apps had their own, and what the Desktop uses.
 * `null` is Off (leave the window as it is); an app can be set to it explicitly.
 */
class TextScales(private var default: Double?, byApp: Map<String, Double?> = emptyMap()) {
    private val byApp = LinkedHashMap(byApp)

    /** What Aa is for `bundleID` (null: the Desktop, or a window with no app id). */
    fun scaleFor(bundleID: String?): Double? =
        if (bundleID != null && byApp.containsKey(bundleID)) byApp[bundleID] else default

    /** The person chose `scale` while `bundleID` streamed (null: the Desktop, which sets the default). */
    fun choose(bundleID: String?, scale: Double?) {
        if (bundleID == null) default = scale else byApp[bundleID] = scale
    }

    val defaultScale: Double? get() = default

    /** The per-app choices as JSON, Off as 0. */
    fun toJson(): String = JSONObject().also { o -> byApp.forEach { (k, v) -> o.put(k, v ?: 0.0) } }.toString()

    companion object {
        fun parse(default: Double?, json: String?): TextScales {
            val map = LinkedHashMap<String, Double?>()
            runCatching {
                val o = JSONObject(json ?: return@runCatching)
                o.keys().forEach { k -> val v = o.optDouble(k, Double.NaN); if (!v.isNaN()) map[k] = v.takeIf { it > 0.0 } }
            }
            return TextScales(default, map)
        }
    }
}
