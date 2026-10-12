package org.cnag.clarid.core

/** Fixed ClarID field order with vocabulary lookups from the pinned codebook. */
class BiosampleDecoder(private val reference: ReferenceData) {
    private val book get() = reference.codebook

    fun decode(text: String, settings: DecodeSettings = DecodeSettings()): Biosample =
        decodeResolved(text, settings).biosample

    /** Detects only the format; the configured codebook and widths remain authoritative. */
    fun decodeResolved(text: String, settings: DecodeSettings = DecodeSettings()): DecodedBiosample {
        settings.validate()
        if (text.isEmpty() || text.length > MAX_IDENTIFIER_LENGTH || text.any { it.isWhitespace() }) {
            invalid("Expected a single ClarID identifier, without whitespace, up to $MAX_IDENTIFIER_LENGTH characters.")
        }
        if (settings.format != IdentifierFormat.AUTO) {
            val value = if (settings.format == IdentifierFormat.HUMAN) human(text, settings.humanSubjectWidth)
                else stub(text, settings.stubSubjectWidth)
            return DecodedBiosample(value, settings.format)
        }
        val successes = mutableListOf<DecodedBiosample>()
        val failures = mutableMapOf<IdentifierFormat, ClarIdException>()
        for (format in listOf(IdentifierFormat.HUMAN, IdentifierFormat.STUB)) {
            try {
                val value = if (format == IdentifierFormat.HUMAN) human(text, settings.humanSubjectWidth)
                    else stub(text, settings.stubSubjectWidth)
                successes += DecodedBiosample(value, format)
            } catch (e: ClarIdException) {
                failures[format] = e
            }
        }
        if (successes.size == 1) return successes.single()
        if (successes.size > 1) invalid("The format is ambiguous. Select Human or Stub explicitly.")
        val likelyFormat = if ('-' in text) IdentifierFormat.HUMAN else IdentifierFormat.STUB
        invalid("Neither format matched. " + failures.getValue(likelyFormat).message)
    }

    private fun human(text: String, width: Int): Biosample {
        val fields = text.split('-')
        if (fields.size !in 9..11) invalid("A human biosample identifier requires 9 fields and optional B/R suffixes.")
        val subject = fields[2]
        if (subject.length != width || !subject.all { it in '0'..'9' }) {
            invalid("Subject ID must contain $width decimal digits. Check the human width setting.")
        }
        val tail = fields.drop(9).toMutableList()
        val replicate = tail.lastOrNull()?.let { REPLICATE.matchEntire(it) }?.let {
            tail.removeAt(tail.lastIndex)
            it.groupValues[1].toInt()
        }
        val batch = tail.lastOrNull()?.let { BATCH.matchEntire(it) }?.let {
            tail.removeAt(tail.lastIndex)
            it.groupValues[1].toInt()
        }
        if (tail.isNotEmpty()) invalid("Optional suffixes must be B followed by two digits, then R followed by two digits.")
        val codes = fields[6].split('+')
        checkCount(codes.size)
        val diseases = codes.map { code ->
            // The reference decoder accepts dot-free codes and restores the dot.
            if (!HUMAN_CONDITION.matches(code)) invalid("A condition is not in the supported ICD-10 format.")
            val disease = reference.diseases.disease(code)
            if (!CONDITION.matches(disease.code)) invalid("A condition is not in the supported ICD-10 format.")
            disease
        }
        checkDuration(fields[8])
        return Biosample(
            book.project.code(fields[0]), book.species.code(fields[1]), subject.toLong(),
            book.tissue.code(fields[3]), book.sampleType.code(fields[4]), book.assay.code(fields[5]),
            diseases, book.timepoint.code(fields[7]), fields[8], batch, replicate,
        )
    }

    private fun stub(text: String, width: Int): Biosample {
        var rest = text
        fun peelOptional(pattern: Regex): Int? {
            val match = pattern.find(rest) ?: return null
            rest = rest.substring(0, match.range.first)
            return match.groupValues[1].toInt()
        }
        val replicate = peelOptional(Regex("R([0-9]{2})$"))
        val batch = peelOptional(Regex("B([0-9]{2})$"))
        val durationMatch = Regex("([0-9]+[DWMYN])$").find(rest)
            ?: invalid("Missing or invalid stub duration.")
        val duration = "P" + durationMatch.value
        checkDuration(duration)
        rest = rest.substring(0, durationMatch.range.first)
        val timepoint = book.timepoint.suffix(rest)
        rest = rest.dropLast(timepoint.stub.length)
        val countText = rest.takeLast(2)
        if (countText.length != 2 || !countText.all { it in '0'..'9' }) invalid("Missing condition count.")
        val count = countText.toInt()
        checkCount(count)
        rest = rest.dropLast(2)

        fun prefix(vocabulary: Vocabulary): Term {
            val term = vocabulary.prefix(rest)
            rest = rest.drop(term.stub.length)
            return term
        }
        fun take(length: Int, field: String): String {
            if (rest.length < length) invalid("Stub is too short for $field. Check the active settings.")
            return rest.take(length).also { rest = rest.drop(length) }
        }
        val project = prefix(book.project)
        val species = book.species.stub(take(book.speciesWidth, "species"))
        val subject = base62(take(width, "subject ID"))
        val tissue = prefix(book.tissue)
        val sampleType = prefix(book.sampleType)
        val assay = prefix(book.assay)
        if (rest.length != count * 3) invalid("Condition count or stub length does not match. Check the stub width.")
        val diseases = rest.chunked(3).map { reference.diseases.ordinal(base62(it)) }
        return Biosample(project, species, subject, tissue, sampleType, assay, diseases,
            timepoint, duration, batch, replicate)
    }

    private fun base62(text: String): Long {
        if (text.isEmpty()) invalid("Empty Base62 field.")
        return text.fold(0L) { value, char ->
            val digit = ALPHABET.indexOf(char)
            if (digit < 0 || value > (Long.MAX_VALUE - digit) / 62) invalid("Invalid or overflowing Base62 field.")
            value * 62 + digit
        }
    }

    private fun checkDuration(value: String) {
        if (!DURATION.matches(value)) invalid("Duration must be P plus one digit and D/W/M/Y, or P0N.")
    }

    private fun checkCount(count: Int) {
        if (count !in 1..10) invalid("Expected 1–10 conditions.")
    }

    private fun invalid(message: String): Nothing = throw ClarIdException(ErrorCode.INVALID_IDENTIFIER, message)

    companion object {
        const val MAX_IDENTIFIER_LENGTH = 1024
        private const val ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        private val CONDITION = Regex("[A-Z][0-9]{2}(?:\\.[0-9]+)?")
        private val HUMAN_CONDITION = Regex("[A-Z][0-9]{2}(?:\\.?[0-9]+)?")
        private val DURATION = Regex("P(?:[0-9][DWMY]|0N)")
        private val BATCH = Regex("B([0-9]{2})")
        private val REPLICATE = Regex("R([0-9]{2})")
    }
}
