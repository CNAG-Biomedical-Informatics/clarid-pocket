package org.cnag.clarid.pocket

import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

internal const val RELEASES_PAGE = "https://github.com/CNAG-Biomedical-Informatics/clarid-pocket/releases"
internal const val UPDATE_ENDPOINT = "https://api.github.com/repos/CNAG-Biomedical-Informatics/clarid-pocket/releases/latest"

internal data class UpdateResponse(val status: Int, val body: String)
data class AppUpdate(val message: String, val downloadPage: String? = null)

/** A manual public metadata request. No identifiers, settings, or credentials are sent. */
internal class AppUpdates(private val fetch: () -> UpdateResponse = ::fetchLatestRelease) {
    fun check(installed: String): AppUpdate = try {
        val response = fetch()
        when (response.status) {
            200 -> evaluate(installed, response.body)
            404 -> AppUpdate("No published release is available yet.")
            403, 429 -> AppUpdate("GitHub is limiting update checks. Please try again later.")
            else -> AppUpdate("Could not check for updates. Please try again later.")
        }
    } catch (_: IOException) {
        AppUpdate("Could not connect to GitHub. Check your connection and try again.")
    } catch (_: RuntimeException) {
        AppUpdate("Could not read the release information. Please try again later.")
    }

    private fun evaluate(installed: String, json: String): AppUpdate {
        val release = JsonParser.parseString(json).asJsonObject
        if (release["draft"].asBoolean || release["prerelease"].asBoolean) {
            return AppUpdate("No published stable release is available yet.")
        }
        val tag = release["tag_name"].asString
        val current = version(installed)
        val latest = version(tag)
        val comparison = latest.zip(current).map { (a, b) -> a.compareTo(b) }.firstOrNull { it != 0 } ?: 0
        if (comparison <= 0) return AppUpdate(if (comparison == 0) "Your app is up to date."
            else "Your installed version is newer than the latest published release.")
        val expectedName = "clarid-pocket-${tag.removePrefix("v")}.apk"
        val hasApk = release["assets"].asJsonArray.any {
            val asset = it.asJsonObject
            asset["name"]?.asString == expectedName && asset["state"]?.asString == "uploaded"
        }
        if (!hasApk) return AppUpdate("Version ${tag.removePrefix("v")} has no installable APK yet.")
        // Construct the official page rather than trusting a URL in the response.
        return AppUpdate("Version ${tag.removePrefix("v")} is available.", "$RELEASES_PAGE/tag/$tag")
    }

    private fun version(value: String): List<Long> {
        require(Regex("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)").matches(value))
        return value.removePrefix("v").split('.').map { it.toLong() }
    }
}

private fun fetchLatestRelease(): UpdateResponse {
    val connection = URI(UPDATE_ENDPOINT).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "ClarID-Pocket")
        val status = connection.responseCode
        if (status != 200) return UpdateResponse(status, "")
        val bytes = connection.inputStream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size() + count > 1_048_576) throw IOException("Release response too large")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return UpdateResponse(status, bytes.toString(Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}
