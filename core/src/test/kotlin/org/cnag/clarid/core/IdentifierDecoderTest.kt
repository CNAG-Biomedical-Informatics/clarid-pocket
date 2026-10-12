package org.cnag.clarid.core

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class IdentifierDecoderTest {
    private val reference = ReferenceData.bundled()
    private val decoder = IdentifierDecoder(reference)

    @Test fun allSubjectFixturesMatchReferenceFieldsAndLabelsInAutoAndExplicitModes() {
        val fixtures = fixtures("subject")
        assertEquals(45, fixtures.size())
        for (entry in fixtures) {
            val f = entry.asJsonObject
            val format = IdentifierFormat.valueOf(f["format"].asString.uppercase())
            val settings = DecodeSettings(format, f["human_width"].asInt, f["stub_width"].asInt, IdentifierEntity.SUBJECT)
            val decoded = decoder.decode(f["identifier"].asString, settings)
            val subject = decoded.record as Subject
            val actual = mapOf(
                "study" to subject.study,
                "subject_id" to subject.subjectId.toString(),
                "type" to if (format == IdentifierFormat.HUMAN) subject.type.code else subject.type.key,
                "sex" to if (format == IdentifierFormat.HUMAN) subject.sex.code else subject.sex.key,
                "age_group" to subject.ageGroup.key,
                "condition" to subject.conditions.joinToString(if (format == IdentifierFormat.HUMAN) "+" else ";") { it.code },
                "condition_name" to subject.conditions.joinToString(";") { it.label ?: "" },
            )
            val expected = f["expected"].asJsonObject.entrySet().associate { it.key to it.value.asString }
            assertEquals(f["name"].asString, expected, actual)
            assertEquals(decoded, decoder.decode(f["identifier"].asString,
                settings.copy(format = IdentifierFormat.AUTO, entity = IdentifierEntity.AUTO)))
        }
    }

    @Test fun biosampleFixturesRemainBiosamplesWithBothEntityAndFormatAuto() {
        for (entry in fixtures("biosample")) {
            val f = entry.asJsonObject
            val settings = DecodeSettings(IdentifierFormat.AUTO, f["human_width"].asInt, f["stub_width"].asInt)
            val result = decoder.decode(f["identifier"].asString, settings)
            assertEquals(IdentifierEntity.BIOSAMPLE, result.record.entity)
            assertEquals(f["format"].asString.uppercase(), result.format.name)
            assertEquals(BiosampleDecoder(reference).decode(f["identifier"].asString, settings), result.record)
        }
    }

    @Test fun rejectsMalformedSubjectsAndHonorsEntityAndFormatOverrides() {
        val human = "TCGA_AML-00001-Case-I25.110-Male-A40_49"
        val stub = "AML001C2to01MA4"
        val invalid = listOf(human + "-EXTRA", human.replace("00001", "abcde"),
            human.replace("Case", "UnknownType"), human.replace("Male", "UnknownSex"),
            human.replace("A40_49", "A400"), human.replace("I25.110", "I25..110"),
            human.replace("I25.110", List(11) { "I25.110" }.joinToString("+")),
            stub.replace("2to", "000"), stub.replace("01MA4", "00MA4"),
            stub.replace("01MA4", "99MA4"), stub.replace("01MA4", "XXMA4"),
            stub.replace("MA4", "MXX"), stub.replace("001", "0?1"), stub.removePrefix("AML"),
            "", stub + "\n", "x".repeat(1025))
        invalid.forEach { reject { decoder.decode(it) } }
        reject { decoder.decode(human, DecodeSettings(humanSubjectWidth = 4)) }
        reject { decoder.decode(human, DecodeSettings(entity = IdentifierEntity.BIOSAMPLE)) }
        reject { decoder.decode(stub, DecodeSettings(format = IdentifierFormat.HUMAN)) }
        assertEquals(IdentifierEntity.SUBJECT, decoder.decode(human).record.entity)
    }

    @Test fun ambiguousCodebooksRequireAnOverrideInsteadOfPickingTheFirstDecoder() {
        val subject = reference.codebook.subject
        fun only(vocabulary: Vocabulary, stub: String) =
            Vocabulary(vocabulary.name, listOf(vocabulary.terms.first().copy(stub = stub)))
        val variant = reference.copy(codebook = reference.codebook.copy(subject = subject.copy(
            type = only(subject.type, "R"), sex = only(subject.sex, "C"), ageGroup = only(subject.ageGroup, "1M"),
        )))
        val ambiguous = IdentifierDecoder(variant)
        val text = "AML01001LTR2to01C1M"
        reject { ambiguous.decode(text) }
        assertEquals(IdentifierEntity.BIOSAMPLE,
            ambiguous.decode(text, DecodeSettings(entity = IdentifierEntity.BIOSAMPLE)).record.entity)
        assertEquals(IdentifierEntity.SUBJECT,
            ambiguous.decode(text, DecodeSettings(entity = IdentifierEntity.SUBJECT)).record.entity)
    }

    @Test fun subjectDiseaseLabelsAndFreeStudyNamesArePreserved() {
        val decoded = decoder.decode("CUSTOM_2026-00001-Control-C22.0+I25.110-Female-A0_9").record as Subject
        assertEquals("CUSTOM_2026", decoded.study)
        assertNull(decoded.studyLabel)
        assertEquals("Liver cell carcinoma", decoded.conditions.first().label)
        assertEquals(listOf("C22.0", "I25.110"), decoded.conditions.map { it.code })
        val sparse = IdentifierDecoder(reference.copy(diseases = DiseaseDictionary(emptyMap(), emptyMap())))
        val withoutLabel = sparse.decode("CUSTOM-00001-Case-I25.110-Male-A40_49").record as Subject
        assertEquals(Disease("I25.110", null), withoutLabel.conditions.single())
    }

    private fun fixtures(entity: String) = javaClass.getResourceAsStream("/$entity-fixtures.json")!!.reader().use {
        JsonParser.parseReader(it).asJsonObject["fixtures"].asJsonArray
    }

    private fun reject(action: () -> Unit) {
        try { action() } catch (_: ClarIdException) { return }
        fail("Expected a typed ClarID failure")
    }
}
