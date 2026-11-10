package de.rpicloud.ipv64net.helper

import de.sr.ipv64.IPv64ApiException
import de.sr.ipv64.IPv64Exception
import de.sr.ipv64.model.Me
import de.sr.ipv64.model.MeUsage
import de.sr.ipv64.model.UsageCounter
import kotlin.math.ceil

/** Preisübersicht von IPv64.net (Button "View plans" in NotAvailableView). */
const val IPV64_PLANS_URL = "https://ipv64.net/pricing"

/**
 * Funktionen, deren Verfügbarkeit vom Plan abhängt; Zähler aus `account.me().usage`:
 * FAILOVER → MeUsage.failoverPolicies, CDN → MeUsage.cdnDomains, DNS64 → MeUsage.dns64Clients.
 */
enum class PlanFeature(val label: String) {
    FAILOVER("DNS failover"),
    CDN("CDN"),
    DNS64("DNS64");

    /** Zugehöriger Zähler (null, wenn der Server ihn nicht liefert). */
    fun counter(usage: MeUsage?): UsageCounter? = when (this) {
        FAILOVER -> usage?.failoverPolicies
        CDN -> usage?.cdnDomains
        DNS64 -> usage?.dns64Clients
    }
}

/** Ergebnis von [availability]. */
sealed interface FeatureAvailability {
    /** Nutzbar, weitere Objekte können angelegt werden (auch: Limit null = unbegrenzt). */
    data object Available : FeatureAvailability

    /** Nutzbar, aber das Limit ist erreicht: bestehende Objekte anzeigen/ändern, Anlegen deaktivieren. */
    data class LimitReached(val used: Int, val limit: Int) : FeatureAvailability

    /** Im Plan nicht enthalten (Limit 0): NotAvailableView anzeigen. */
    data object NotInPlan : FeatureAvailability

    /** Account noch nicht geladen oder Zähler fehlt: wie Available behandeln, der Server entscheidet. */
    data object Unknown : FeatureAvailability
}

/** Darf der Bereich überhaupt angezeigt werden (alles außer NotInPlan). */
val FeatureAvailability.canUse: Boolean get() = this !is FeatureAvailability.NotInPlan

/** Dürfen neue Objekte angelegt werden (Available oder Unknown). */
val FeatureAvailability.canCreate: Boolean
    get() = this is FeatureAvailability.Available || this is FeatureAvailability.Unknown

/**
 * Verfügbarkeit einer Plan-Funktion aus dem Account ([AccountCache]/[loadMe]).
 * Limit 0 → NotInPlan, Limit null → unbegrenzt (Available), used ≥ limit → LimitReached.
 * Ohne Account oder Zähler → Unknown.
 */
fun Me?.availability(feature: PlanFeature): FeatureAvailability {
    val counter = feature.counter(this?.usage) ?: return FeatureAvailability.Unknown
    val limit = counter.limit ?: return FeatureAvailability.Available
    if (limit <= 0) return FeatureAvailability.NotInPlan
    val used = ceil(counter.used ?: 0.0).toInt()
    return if (used >= limit) FeatureAvailability.LimitReached(used, limit) else FeatureAvailability.Available
}

/**
 * true, wenn der Server eine Anfrage wegen des Plans abgelehnt hat (HTTP 403 mit Plan-/Limit-Bezug, z. B. code
 * `limit_reached` oder ein Text, der den Plan/das Paket nennt). Ein 403 ohne solchen Bezug (fehlende Berechtigung)
 * zählt nicht. Für Lade-Anfragen → NotAvailableView; bei Anlegen besser [isLimitReached] als Meldung zeigen.
 */
fun IPv64Exception.isPlanRestricted(): Boolean {
    if (this !is IPv64ApiException || status != 403) return false
    val info = "${code.orEmpty()} ${detail.orEmpty()} ${problem?.title.orEmpty()}".lowercase()
    return PLAN_HINTS.any { info.contains(it) }
}

/** true bei HTTP 403/409 mit code `limit_reached` (Plan-Limit für weitere Objekte erreicht). */
fun IPv64Exception.isLimitReached(): Boolean =
    this is IPv64ApiException && (status == 403 || status == 409) && code == "limit_reached"

/** Lesbarer Grund einer Plan-Ablehnung (Servertext oder Standardtext). */
fun IPv64Exception.planRestrictionText(): String =
    (this as? IPv64ApiException)?.detail?.takeIf { it.isNotBlank() } ?: "This feature is not available on your plan."

private val PLAN_HINTS = listOf("limit", "plan", "package", "paket", "tarif", "upgrade", "feature", "not available", "not included")
