package com.example.domain.util

object MoroccanPhoneNormalizer {
    private val MOROCCAN_E164_REGEX = Regex("^\\+212[67]\\d{8}$")

    /**
     * Normalizes Moroccan mobile numbers (06..., 07..., 2126..., 002126..., +212 6...)
     * into strict E.164 format (+2126XXXXXXXX or +2127XXXXXXXX).
     */
    fun normalize(rawPhone: String): String {
        val cleaned = rawPhone.trim()
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")

        if (cleaned.isEmpty()) return ""

        val candidate = when {
            cleaned.startsWith("+212") -> cleaned
            cleaned.startsWith("00212") -> "+212" + cleaned.removePrefix("00212")
            cleaned.startsWith("212") && cleaned.length >= 12 -> "+$cleaned"
            cleaned.startsWith("06") || cleaned.startsWith("07") -> "+212" + cleaned.drop(1)
            cleaned.startsWith("6") || cleaned.startsWith("7") -> "+212$cleaned"
            else -> cleaned
        }

        // Remove accidental leading zero right after +212 (e.g., +21206... -> +2126...)
        return if (candidate.startsWith("+2120") && candidate.length == 14) {
            "+212" + candidate.removePrefix("+2120")
        } else {
            candidate
        }
    }

    fun isValidMoroccanMobile(phone: String): Boolean {
        val normalized = normalize(phone)
        return MOROCCAN_E164_REGEX.matches(normalized)
    }

    /**
     * Masks middle digits for CNDP Law 09-08 compliant audit logging.
     */
    fun maskForAudit(phone: String): String {
        val normalized = normalize(phone)
        return if (normalized.length >= 10) {
            "${normalized.take(6)}****${normalized.takeLast(3)}"
        } else {
            "***"
        }
    }
}
