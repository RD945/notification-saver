package com.notificationsaver.app.data.otp

/**
 * Keyword-ranked OTP extractor for notification title + body.
 * Inspired by otp-message-extractor: 4–8 digit (or short alphanumeric) codes
 * near OTP language, skipping phones, dates, and amounts.
 */
object OtpDetector {
    private const val MIN_SCORE = 0.45

    private val arabicIndic = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9',
        '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
        '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
    )

    private val keywords = listOf(
        "one time password",
        "one-time password",
        "one time code",
        "one-time code",
        "verification code",
        "verification pin",
        "security code",
        "authentication code",
        "auth code",
        "login code",
        "confirm code",
        "confirmation code",
        "whatsapp code",
        "is your code",
        "is your whatsapp",
        "your otp",
        "your code",
        "passcode",
        "otp",
        "2fa",
        "2-fa",
        "mfa",
        "pin",
        "ओटीपी",
        "सत्यापन",
        "कोड",
    )

    private val negative = listOf(
        "order id",
        "order #",
        "invoice",
        "tracking",
        "transaction id",
        "ref no",
        "reference",
        "amount",
        "rs.",
        "rs ",
        "inr",
        "phone",
        "call",
        "mobile",
        "www.",
        "http",
        "price",
        "total",
    )

    private val phone = Regex("""\+?\d[\d\s\-()]{8,}\d""")
    private val date = Regex("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b""")
    private val time = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val year = Regex("""\b(?:19|20)\d{2}\b""")
    private val money = Regex("""(?:₹|rs\.?)\s*\d[\d,]{2,}""", RegexOption.IGNORE_CASE)
    private val numeric = Regex("""(?<![A-Za-z0-9])(\d{4,8})(?![A-Za-z0-9])""")
    private val alphanumeric = Regex("""(?<![A-Za-z0-9])([A-Za-z0-9][-A-Za-z0-9]{3,7})(?![A-Za-z0-9])""")

    fun extract(title: String, text: String): String? {
        val raw = "$title\n$text".trim()
        if (raw.isBlank()) return null
        val normalized = normalizeDigits(raw)
        val haystack = normalized.lowercase()
        val protected = protectedRanges(normalized)
        val candidates = mutableListOf<Candidate>()

        numeric.findAll(normalized).forEach { match ->
            if (!overlaps(match.range, protected)) {
                candidates += Candidate(match.value, match.range.first, numeric = true)
            }
        }
        alphanumeric.findAll(normalized).forEach { match ->
            val value = match.value
            if (value.any { it.isLetter() } && value.any { it.isDigit() } && !overlaps(match.range, protected)) {
                candidates += Candidate(value, match.range.first, numeric = false)
            }
        }

        var best: Candidate? = null
        var bestScore = MIN_SCORE
        for (candidate in candidates) {
            val score = score(candidate, haystack)
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best?.code
    }

    fun looksLikeOtp(title: String, text: String): Boolean = extract(title, text) != null

    private fun score(candidate: Candidate, haystack: String): Double {
        var score = when {
            candidate.numeric && candidate.code.length == 6 -> 0.35
            candidate.numeric && candidate.code.length in 4..8 -> 0.25
            !candidate.numeric -> 0.20
            else -> 0.10
        }
        val nearby = window(haystack, candidate.index, 48)
        val hasNearKeyword = keywords.any { nearby.contains(it) }
        val hasAnyKeyword = keywords.any { haystack.contains(it) }
        if (hasNearKeyword) score += 0.40
        else if (hasAnyKeyword) score += 0.15
        if (negative.any { nearby.contains(it) }) score -= 0.30
        if (candidate.numeric && candidate.code.length == 4 && !hasNearKeyword) score -= 0.15
        if (year.matches(candidate.code) && !hasNearKeyword) score -= 0.35
        return score
    }

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        for (ch in value) {
            append(arabicIndic[ch] ?: ch)
        }
    }

    private fun protectedRanges(value: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        listOf(phone, date, time, money).forEach { regex ->
            regex.findAll(value).forEach { ranges += it.range }
        }
        return ranges
    }

    private fun overlaps(range: IntRange, protected: List<IntRange>): Boolean =
        protected.any { p -> range.first <= p.last && p.first <= range.last }

    private fun window(value: String, index: Int, radius: Int): String {
        val start = (index - radius).coerceAtLeast(0)
        val end = (index + radius).coerceAtMost(value.length)
        return value.substring(start, end)
    }

    private data class Candidate(
        val code: String,
        val index: Int,
        val numeric: Boolean,
    )
}
