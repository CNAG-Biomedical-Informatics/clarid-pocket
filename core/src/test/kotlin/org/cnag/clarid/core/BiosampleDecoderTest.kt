package org.cnag.clarid.core

import com.google.gson.JsonParser
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class BiosampleDecoderTest {
    private val reference = bundled
    private val decoder = BiosampleDecoder(reference)

    @Test fun pinnedPerlFixturesMatchAllFieldsAndDiseaseLabels() {
        val fixtures = javaClass.getResourceAsStream("/biosample-fixtures.json")!!.reader().use {
            JsonParser.parseReader(it).asJsonObject["fixtures"].asJsonArray
        }
        assertEquals(31, fixtures.size())
        for (entry in fixtures) {
            val fixture = entry.asJsonObject
            val settings = DecodeSettings(
                IdentifierFormat.valueOf(fixture["format"].asString.uppercase()),
                fixture["human_width"].asInt, fixture["stub_width"].asInt,
            )
            val result = decoder.decode(fixture["identifier"].asString, settings)
            val actual = mapOf(
                "project" to result.project.key, "species" to result.species.key,
                "subject_id" to result.subjectId.toString(), "tissue" to result.tissue.key,
                "sample_type" to result.sampleType.key, "assay" to result.assay.key,
                "condition" to result.conditions.joinToString(";") { it.code },
                "condition_name" to result.conditions.joinToString(";") { it.label ?: "" },
                "timepoint" to result.timepoint.key, "duration" to result.duration,
                "batch" to (result.batch?.toString() ?: ""), "replicate" to (result.replicate?.toString() ?: ""),
            )
            val expected = fixture["expected"].asJsonObject.entrySet().associate { it.key to it.value.asString }
            assertEquals(fixture["name"].asString, expected, actual)
            val auto = decoder.decodeResolved(fixture["identifier"].asString,
                settings.copy(format = IdentifierFormat.AUTO))
            assertEquals(fixture["name"].asString, settings.format, auto.format)
            assertEquals(result, auto.biosample)
        }
    }

    @Test fun autoDoesNotGuessWidthsOrOverrideAnExplicitFormat() {
        assertEquals(IdentifierFormat.AUTO, DecodeSettings().format)
        expectFailure { decoder.decodeResolved(HUMAN, DecodeSettings(humanSubjectWidth = 4)) }
        expectFailure { decoder.decodeResolved(STUB, DecodeSettings(stubSubjectWidth = 4)) }
        expectFailure { decoder.decodeResolved(HUMAN, DecodeSettings(IdentifierFormat.STUB)) }
        expectFailure { decoder.decodeResolved(STUB, DecodeSettings(IdentifierFormat.HUMAN)) }
        expectFailure { decoder.decodeResolved("https://example.org") }
        expectFailure { decoder.decodeResolved(STUB.lowercase()) }
    }

    @Test fun invalidHumanStructuresAreRejectedCompletely() {
        val cases = listOf(
            HUMAN + "-EXTRA", HUMAN + "-R01-B02", HUMAN + "-B1", HUMAN + "-B01-B02",
            HUMAN.replace("00001", "abcde"), HUMAN.replace("00001", "001"),
            HUMAN.replace("P1M", "P10D"), HUMAN.replace("P1M", "P7N"),
            HUMAN.replace("I25.110", "garbage"), HUMAN.replace("I25.110", "I2.5110"),
            HUMAN.replace("I25.110", "I25..110"), HUMAN.replace("I25.110", "I25.110+"),
            HUMAN.replace("I25.110", List(11) { "I25.110" }.joinToString("+")),
            HUMAN.replace("LIV", "XYZ"), HUMAN + "\n", "https://example.org/" + HUMAN,
        )
        cases.forEach { expectFailure { decoder.decode(it, DecodeSettings(IdentifierFormat.HUMAN)) } }
    }

    @Test fun invalidStubLengthsCountsTailsAndOrdinalsFail() {
        val cases = listOf(
            "", "AML", STUB.dropLast(1), STUB + "0105", STUB + "B1", STUB + "R01B02",
            STUB.replace("1M", "10D"), STUB.replace("1M", "7N"),
            STUB.replace("2to01", "2to00"), STUB.replace("2to01", "2to02"),
            STUB.replace("2to", "000"), STUB.replace("2to", "zzz"),
            STUB.replace("2to", "@@@"), STUB.replace("001", "0?1"),
            " ".repeat(1025), " $STUB", STUB + "\n",
        )
        cases.forEach { expectFailure { decoder.decode(it) } }
    }

    @Test fun settingsAreExplicitAndBounded() {
        expectFailure { decoder.decode(HUMAN, DecodeSettings(IdentifierFormat.HUMAN, 4)) }
        expectFailure { decoder.decode(STUB, DecodeSettings(stubSubjectWidth = 4)) }
        expectFailure { decoder.decode(STUB, DecodeSettings(stubSubjectWidth = 0)) }
        expectFailure { decoder.decode(STUB, DecodeSettings(humanSubjectWidth = 19)) }
        expectFailure { decoder.decode(STUB, DecodeSettings(stubSubjectWidth = 11)) }
    }

    @Test fun multipleLabelsPreserveConditionOrder() {
        val result = decoder.decode(HUMAN.replace("I25.110", "C22.0+I25.110"), DecodeSettings(IdentifierFormat.HUMAN))
        assertEquals(listOf("C22.0", "I25.110"), result.conditions.map { it.code })
        assertEquals("Liver cell carcinoma", result.conditions.first().label)
        assertTrue(result.conditions.last().label!!.startsWith("Atherosclerotic heart disease"))
    }

    @Test fun missingLabelPreservesCodeAndDotFreeLookupsWork() {
        val sparse = BiosampleDecoder(reference.copy(diseases = DiseaseDictionary(emptyMap(), mapOf(1L to "I25110"))))
        val result = sparse.decode(HUMAN, DecodeSettings(IdentifierFormat.HUMAN))
        assertEquals(Disease("I25.110", null), result.conditions.single())
        val dotFree = decoder.decode(HUMAN.replace("I25.110", "I25110"), DecodeSettings(IdentifierFormat.HUMAN))
        assertEquals(reference.diseases.disease("I25.110"), dotFree.conditions.single())
    }

    @Test fun bundledAliasesQuotedStringsAndDefaultsLoadCorrectly() {
        assertEquals("0.04", reference.codebook.version)
        assertEquals("CNAG-2026.07.12", reference.codebook.localVersion)
        assertEquals("Human", reference.codebook.species.stub("01").key)
        assertEquals("Unknown", reference.codebook.species.stub("00").key)
        assertEquals("Unknown", reference.codebook.tissue.stub("U").key)
        assertEquals("Not Available", reference.codebook.assay.code("NAV").key)
    }

    @Test fun legacyCodebooksUnsupportedPatternsDuplicateKeysAndMixedWidthsFail() {
        val text = javaClass.getResourceAsStream("/clarid/0.04/clarid-codebook.yaml")!!.reader().use { it.readText() }
        for (version in listOf("0.02", "0.03", "0.05")) {
            val error = expectFailure { CodebookLoader.load(text.replace("\"0.04\"", "\"$version\"").byteInputStream()) }
            assertEquals(ErrorCode.UNSUPPORTED_CODEBOOK, error.code)
        }
        expectFailure { CodebookLoader.load(text.replace("P%d%s", "D%d%s").byteInputStream()) }
        expectFailure { CodebookLoader.load((text + "\nmetadata: {}\n").byteInputStream()) }
        expectFailure { CodebookLoader.load(text.replace("stub_code:  \"01\"", "stub_code:  \"001\"").byteInputStream()) }
    }

    @Test fun bundledAssetsMatchRecordedUpstreamChecksums() {
        val manifest = javaClass.getResourceAsStream("/clarid/0.04/provenance.json")!!.reader().use {
            JsonParser.parseReader(it).asJsonObject
        }
        assertEquals("3789effd7481cef5928a251095f2b7f792b9fa3f", manifest["commit"].asString)
        for (file in manifest["files"].asJsonArray) {
            val name = file.asJsonObject["bundled"].asString
            val bytes = javaClass.getResourceAsStream("/clarid/0.04/$name")!!.use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(name, file.asJsonObject["sha256"].asString, hash)
        }
    }

    private fun expectFailure(block: () -> Unit): ClarIdException {
        try { block() } catch (e: ClarIdException) { return e }
        throw AssertionError("Expected a typed ClarID failure.")
    }

    companion object {
        private val bundled = ReferenceData.bundled()
        private const val HUMAN = "TCGA_AML-HomSap-00001-LIV-TUM-RNA-I25.110-CHL-P1M"
        private const val STUB = "AML01001LTR2to01C1M"
    }
}
