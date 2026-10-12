package org.cnag.clarid.core

/** Fixed 0.04 subject grammar. Study is a free string in the reference format. */
internal class SubjectDecoder(private val reference: ReferenceData) {
    private val book get() = reference.codebook.subject

    fun decode(text: String, settings: DecodeSettings): Subject = when (settings.format) {
        IdentifierFormat.HUMAN -> human(text, settings.humanSubjectWidth)
        IdentifierFormat.STUB -> stub(text, settings.stubSubjectWidth)
        IdentifierFormat.AUTO -> throw ClarIdException(ErrorCode.INVALID_SETTINGS, "Resolve the format before decoding.")
    }

    private fun human(text: String, width: Int): Subject {
        val fields = text.split('-')
        if (fields.size != 6) invalid("A human subject identifier requires exactly 6 fields.")
        val study = fields[0].takeIf { it.isNotEmpty() } ?: invalid("Missing study.")
        val sid = fields[1]
        if (sid.length != width || sid.any { it !in '0'..'9' }) {
            invalid("Subject ID must contain $width decimal digits. Check Advanced settings.")
        }
        val codes = fields[3].split('+')
        checkCount(codes.size)
        val conditions = codes.map {
            if (!Regex("[A-Z][0-9]{2}(?:\\.?[0-9]+)?").matches(it)) invalid("Invalid subject condition syntax.")
            reference.diseases.disease(it)
        }
        return Subject(study, book.study.terms.firstOrNull { it.key.replace('-', '_') == study }?.label,
            sid.toLong(), book.type.code(fields[2]), conditions, book.sex.code(fields[4]),
            book.ageGroup.code(fields[5]))
    }

    private fun stub(text: String, width: Int): Subject {
        var rest = text
        fun peel(length: Int, field: String): String {
            if (rest.length < length) invalid("Subject stub is too short for $field.")
            return rest.takeLast(length).also { rest = rest.dropLast(length) }
        }
        val age = book.ageGroup.stub(peel(2, "age group"))
        val sex = book.sex.stub(peel(1, "sex"))
        val countText = peel(2, "condition count")
        if (countText.any { it !in '0'..'9' }) invalid("Invalid subject condition count.")
        val count = countText.toInt()
        checkCount(count)
        val conditions = peel(count * 3, "conditions").chunked(3).map {
            reference.diseases.ordinal(base62(it))
        }
        val type = book.type.stub(peel(1, "type"))
        val sid = base62(peel(width, "subject ID"))
        val study = rest.takeIf { it.isNotEmpty() } ?: invalid("Missing study in subject stub.")
        return Subject(study, book.study.terms.firstOrNull { it.stub == study }?.label,
            sid, type, conditions, sex, age)
    }

    private fun base62(text: String): Long = text.fold(0L) { value, char ->
        val digit = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".indexOf(char)
        if (digit < 0 || value > (Long.MAX_VALUE - digit) / 62) invalid("Invalid subject Base62 field.")
        value * 62 + digit
    }

    private fun checkCount(count: Int) {
        if (count !in 1..10) invalid("Expected 1–10 subject conditions.")
    }

    private fun invalid(message: String): Nothing = throw ClarIdException(ErrorCode.INVALID_IDENTIFIER, message)
}
