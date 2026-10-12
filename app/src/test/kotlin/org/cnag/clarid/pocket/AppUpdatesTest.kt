package org.cnag.clarid.pocket

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AppUpdatesTest {
    @Test fun livePublicEndpointWhenRequested() {
        org.junit.Assume.assumeTrue(System.getenv("CLARID_LIVE_UPDATE_TEST") == "1")
        val result = AppUpdates().check("0.0.0")
        assertTrue(result.message, result.message == "No published release is available yet." ||
            (result.message.endsWith("is available.") && result.downloadPage?.startsWith(RELEASES_PAGE) == true))
    }

    private fun json(tag: String = "v0.1.10", flags: String = "\"draft\":false,\"prerelease\":false", apk: Boolean = true) =
        """{"tag_name":"$tag",$flags,"assets":[${if (apk) """{"name":"clarid-pocket-${tag.removePrefix("v")}.apk","state":"uploaded"}""" else ""}]}"""
    private fun check(body: String, installed: String = "0.1.9") =
        AppUpdates { UpdateResponse(200, body) }.check(installed)

    @Test fun newerVersionUsesNumericComparisonAndOfficialPage() {
        val result = check(json())
        assertEquals("Version 0.1.10 is available.", result.message)
        assertEquals("$RELEASES_PAGE/tag/v0.1.10", result.downloadPage)
        assertNotNull(check(json("v1.0.0")).downloadPage)
    }
    @Test fun equalAndOlderReleasesNeverOfferDowngrades() {
        assertEquals("Your app is up to date.", check(json("v0.1.9")).message)
        assertNull(check(json("v0.1.8")).downloadPage)
        assertTrue(check(json("v0.1.8")).message.contains("newer"))
    }
    @Test fun unpublishedAndPrereleasesAreExcluded() {
        assertNull(check(json(flags = "\"draft\":true,\"prerelease\":false")).downloadPage)
        assertNull(check(json(flags = "\"draft\":false,\"prerelease\":true")).downloadPage)
    }
    @Test fun missingApkDoesNotOfferDownload() {
        assertTrue(check(json(apk = false)).message.contains("no installable APK"))
        assertNull(check(json().replace("uploaded", "new")).downloadPage)
    }
    @Test fun malformedVersionsAndResponsesFailClearly() {
        for (body in listOf("{}", "not json", json("v0.1.10-dev"), json("v0.1.10/evil"),
            json("v999999999999999999999.0.0"))) {
            assertTrue(check(body).message.contains("Could not read"))
            assertNull(check(body).downloadPage)
        }
    }
    @Test fun handlesNotFoundRateLimitsAndServerErrors() {
        for ((status, phrase) in listOf(404 to "No published", 403 to "limiting", 429 to "limiting", 500 to "Could not check")) {
            val result = AppUpdates { UpdateResponse(status, "") }.check("0.1.9")
            assertTrue(result.message.contains(phrase))
            assertNull(result.downloadPage)
        }
    }
    @Test fun connectionFailureCanBeRetried() {
        var first = true
        val checker = AppUpdates {
            if (first) { first = false; throw IOException("offline") }
            UpdateResponse(200, json())
        }
        assertTrue(checker.check("0.1.9").message.contains("connection"))
        assertNotNull(checker.check("0.1.9").downloadPage)
    }
}
