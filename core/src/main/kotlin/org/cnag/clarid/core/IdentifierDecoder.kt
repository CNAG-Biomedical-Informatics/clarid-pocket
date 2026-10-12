package org.cnag.clarid.core

/** Auto dispatch accepts exactly one strict interpretation; it never searches widths. */
class IdentifierDecoder(reference: ReferenceData) {
    private val biosample = BiosampleDecoder(reference)
    private val subject = SubjectDecoder(reference)

    fun decode(text: String, settings: DecodeSettings = DecodeSettings()): DecodedIdentifier {
        settings.validate()
        if (text.isEmpty() || text.length > BiosampleDecoder.MAX_IDENTIFIER_LENGTH ||
            text.any { it.isWhitespace() || it.isISOControl() }) {
            throw ClarIdException(ErrorCode.INVALID_IDENTIFIER,
                "Expected one ClarID identifier without whitespace, up to 1024 characters.")
        }
        val entities = if (settings.entity == IdentifierEntity.AUTO)
            listOf(IdentifierEntity.BIOSAMPLE, IdentifierEntity.SUBJECT) else listOf(settings.entity)
        val formats = if (settings.format == IdentifierFormat.AUTO)
            listOf(IdentifierFormat.HUMAN, IdentifierFormat.STUB) else listOf(settings.format)
        val successes = mutableListOf<DecodedIdentifier>()
        val failures = mutableMapOf<Pair<IdentifierEntity, IdentifierFormat>, ClarIdException>()
        for (entity in entities) for (format in formats) {
            try {
                val explicit = settings.copy(entity = entity, format = format)
                val record = if (entity == IdentifierEntity.BIOSAMPLE) biosample.decode(text, explicit)
                    else subject.decode(text, explicit)
                successes += DecodedIdentifier(record, format)
            } catch (e: ClarIdException) {
                failures[entity to format] = e
            }
        }
        if (successes.size == 1) return successes.single()
        if (successes.size > 1) throw ClarIdException(ErrorCode.INVALID_IDENTIFIER,
            "This identifier has multiple interpretations. Choose its entity in Advanced settings and its format.")
        val likelyFormat = if ('-' in text) IdentifierFormat.HUMAN else IdentifierFormat.STUB
        val likelyEntity = if (text.count { it == '-' } == 5) IdentifierEntity.SUBJECT else IdentifierEntity.BIOSAMPLE
        val detail = failures[likelyEntity to likelyFormat] ?: failures.values.first()
        throw ClarIdException(ErrorCode.INVALID_IDENTIFIER,
            "No supported identifier matched. " + detail.message)
    }
}
