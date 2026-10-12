package org.cnag.clarid.core

import com.google.gson.JsonParser
import java.io.InputStream

class DiseaseDictionary(
    private val labels: Map<String, String>,
    private val codesByOrdinal: Map<Long, String>,
) {
    fun disease(code: String): Disease {
        val key = code.replace(".", "")
        val display = if (key.length > 3) key.take(3) + "." + key.drop(3) else key
        return Disease(display, labels[key])
    }

    fun ordinal(value: Long): Disease = disease(codesByOrdinal[value]
        ?: throw ClarIdException(ErrorCode.UNKNOWN_CODE, "Unknown condition ordinal in the stub."))

    companion object {
        fun load(labels: InputStream, ordinals: InputStream): DiseaseDictionary {
            val names = labels.bufferedReader(Charsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader).asJsonObject.entrySet().associate { it.key to it.value.asString }
            }
            val codes = ordinals.bufferedReader(Charsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader).asJsonObject.entrySet().associate { it.value.asLong to it.key }
            }
            return DiseaseDictionary(names, codes)
        }
    }
}

data class ReferenceData(val codebook: Codebook, val diseases: DiseaseDictionary) {
    companion object {
        fun bundled(): ReferenceData = ReferenceData(
            resource("clarid-codebook.yaml").use(CodebookLoader::load),
            resource("icd10.json").use { labels ->
                resource("icd10_order.json").use { order -> DiseaseDictionary.load(labels, order) }
            },
        )

        private fun resource(name: String): InputStream =
            ReferenceData::class.java.getResourceAsStream("/clarid/0.04/$name")
                ?: error("Bundled reference resource is missing: $name")
    }
}
