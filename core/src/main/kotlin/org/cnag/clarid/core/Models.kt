package org.cnag.clarid.core

enum class IdentifierFormat { AUTO, HUMAN, STUB }
enum class IdentifierEntity { AUTO, BIOSAMPLE, SUBJECT }

data class DecodeSettings(
    val format: IdentifierFormat = IdentifierFormat.AUTO,
    val humanSubjectWidth: Int = 5,
    val stubSubjectWidth: Int = 3,
    val entity: IdentifierEntity = IdentifierEntity.AUTO,
) {
    fun validate() {
        if (humanSubjectWidth !in 1..18 || stubSubjectWidth !in 1..10) {
            throw ClarIdException(
                ErrorCode.INVALID_SETTINGS,
                "Human subject width must be 1–18; stub subject width must be 1–10.",
            )
        }
    }
}

enum class ErrorCode {
    INVALID_SETTINGS, INVALID_IDENTIFIER, UNKNOWN_CODE, UNSUPPORTED_CODEBOOK, INVALID_CODEBOOK,
}

class ClarIdException(val code: ErrorCode, override val message: String) : Exception(message)

data class Term(val key: String, val code: String, val stub: String, val label: String)
data class Disease(val code: String, val label: String?)
data class DecodedBiosample(val biosample: Biosample, val format: IdentifierFormat)
data class DecodedIdentifier(val record: IdentifierRecord, val format: IdentifierFormat)
sealed interface IdentifierRecord {
    val subjectId: Long
    val conditions: List<Disease>
    val entity: IdentifierEntity
}
data class Biosample(
    val project: Term,
    val species: Term,
    override val subjectId: Long,
    val tissue: Term,
    val sampleType: Term,
    val assay: Term,
    override val conditions: List<Disease>,
    val timepoint: Term,
    val duration: String,
    val batch: Int?,
    val replicate: Int?,
) : IdentifierRecord {
    override val entity = IdentifierEntity.BIOSAMPLE
}

data class Subject(
    val study: String,
    val studyLabel: String?,
    override val subjectId: Long,
    val type: Term,
    override val conditions: List<Disease>,
    val sex: Term,
    val ageGroup: Term,
) : IdentifierRecord {
    override val entity = IdentifierEntity.SUBJECT
}
