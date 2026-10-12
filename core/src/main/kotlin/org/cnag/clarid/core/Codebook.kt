package org.cnag.clarid.core

import java.io.InputStream
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

class Vocabulary(val name: String, val terms: List<Term>) {
    private val byCode = terms.associateBy(Term::code)
    private val byStub = terms.associateBy(Term::stub)
    private val longestFirst = terms.sortedByDescending { it.stub.length }

    init {
        if (terms.isEmpty() || byCode.size != terms.size || byStub.size != terms.size) {
            throw ClarIdException(ErrorCode.INVALID_CODEBOOK, "Empty or ambiguous $name vocabulary.")
        }
    }

    fun code(value: String): Term = byCode[value]
        ?: throw ClarIdException(ErrorCode.UNKNOWN_CODE, "Unknown $name code. Check the active codebook.")

    fun stub(value: String): Term = byStub[value]
        ?: throw ClarIdException(ErrorCode.UNKNOWN_CODE, "Unknown $name stub. Check the active settings.")

    fun prefix(value: String): Term = longestFirst.firstOrNull { value.startsWith(it.stub) }
        ?: throw ClarIdException(ErrorCode.UNKNOWN_CODE, "Unknown $name stub. Check the active settings.")

    fun suffix(value: String): Term = longestFirst.firstOrNull { value.endsWith(it.stub) }
        ?: throw ClarIdException(ErrorCode.UNKNOWN_CODE, "Unknown $name stub. Check the active settings.")
}

data class SubjectCodebook(val study: Vocabulary, val type: Vocabulary, val sex: Vocabulary, val ageGroup: Vocabulary) {
    init {
        if (type.terms.any { it.stub.length != 1 } || sex.terms.any { it.stub.length != 1 } ||
            ageGroup.terms.any { it.stub.length != 2 }) {
            throw ClarIdException(ErrorCode.INVALID_CODEBOOK, "Subject type/sex/age stubs must use widths 1/1/2.")
        }
    }
}

data class Codebook(
    val version: String,
    val localVersion: String?,
    val project: Vocabulary,
    val species: Vocabulary,
    val tissue: Vocabulary,
    val sampleType: Vocabulary,
    val assay: Vocabulary,
    val timepoint: Vocabulary,
    val subject: SubjectCodebook,
) {
    val speciesWidth: Int = species.terms.filter { it.key != "Not Available" }
        .map { it.stub.length }.distinct().singleOrNull()
        ?: throw ClarIdException(ErrorCode.INVALID_CODEBOOK, "Species stubs must have a consistent width.")
}

/** Loads the 0.04 bundled pattern profile. General project import is a later feature. */
object CodebookLoader {
    fun load(input: InputStream): Codebook {
        try {
            val options = LoaderOptions().apply {
                isAllowDuplicateKeys = false
                maxAliasesForCollections = 20
                nestingDepthLimit = 20
                codePointLimit = 262_144
            }
            val root = map(Yaml(SafeConstructor(options)).load<Any>(input), "codebook")
            val metadata = map(root["metadata"], "metadata")
            val version = string(metadata["version"], "metadata.version")
            if (version != "0.04") {
                throw ClarIdException(
                    ErrorCode.UNSUPPORTED_CODEBOOK,
                    "This app supports ClarID codebook 0.04 only.",
                )
            }
            val entities = map(root["entities"], "entities")
            val defaults = map(entities["_defaults"], "defaults")
            val biosample = map(entities["biosample"], "biosample")
            val subject = map(entities["subject"], "subject")
            // Never evaluate arbitrary Perl expressions as Java regular expressions.
            checkPattern(biosample, "condition_pattern", "^([A-Z]\\d{2}(?:\\.\\d+)?)$", "%s", "%s")
            checkPattern(subject, "condition_pattern", "^([A-Z]\\d{2}(?:\\.\\d+)?)$", "%s", "%s")
            checkPattern(biosample, "duration_pattern", "^(?:P?(\\d)([DWMY])|P?(0)(N))$", "P%d%s", "%d%s")
            checkPattern(biosample, "batch_pattern", "^(\\d{1,2})$", "B%02d", "B%02d")
            checkPattern(biosample, "replicate_pattern", "^(\\d{1,2})$", "R%02d", "R%02d")

            fun vocabulary(slot: String, entity: Map<String, Any?> = biosample): Vocabulary {
                val effective = defaults.toMutableMap().apply { putAll(map(entity[slot], slot)) }
                return Vocabulary(slot, effective.map { (key, raw) ->
                    val term = map(raw, slot)
                    Term(key, string(term["code"], "$slot.code"),
                        string(term["stub_code"], "$slot.stub_code"),
                        string(term["label"], "$slot.label"))
                })
            }
            return Codebook(
                version, metadata["local_version"]?.let { string(it, "metadata.local_version") },
                vocabulary("project"), vocabulary("species"), vocabulary("tissue"),
                vocabulary("sample_type"), vocabulary("assay"), vocabulary("timepoint"),
                SubjectCodebook(vocabulary("study", subject), vocabulary("type", subject),
                    vocabulary("sex", subject), vocabulary("age_group", subject)),
            )
        } catch (e: ClarIdException) {
            throw e
        } catch (_: Exception) {
            throw ClarIdException(ErrorCode.INVALID_CODEBOOK, "The codebook could not be loaded.")
        }
    }

    private fun checkPattern(
        category: Map<String, Any?>, field: String, regex: String, human: String, stub: String,
    ) {
        val pattern = map(category[field], field)
        if (pattern["regex"] != regex || pattern["code_format"] != human || pattern["stub_format"] != stub) {
            throw ClarIdException(ErrorCode.INVALID_CODEBOOK, "Unsupported $field configuration.")
        }
    }

    private fun map(value: Any?, field: String): Map<String, Any?> {
        if (value !is Map<*, *> || value.keys.any { it !is String }) {
            throw ClarIdException(ErrorCode.INVALID_CODEBOOK, "Invalid $field mapping.")
        }
        return value.entries.associate { it.key as String to it.value }
    }

    private fun string(value: Any?, field: String): String = (value as? String)?.takeIf { it.isNotEmpty() }
        ?: throw ClarIdException(ErrorCode.INVALID_CODEBOOK, "Missing or non-string $field.")
}
