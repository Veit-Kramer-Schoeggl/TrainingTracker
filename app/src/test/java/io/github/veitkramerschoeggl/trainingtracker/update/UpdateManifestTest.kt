package io.github.veitkramerschoeggl.trainingtracker.update

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateManifestTest {

    @Test
    fun parsesTheReleaseWorkflowOutput() {
        val manifest = UpdateManifest.parse(
            """
            {
              "versionCode": 10100,
              "versionName": "1.1.0",
              "apkUrl": "https://github.com/Veit-Kramer-Schoeggl/TrainingTracker/releases/download/v1.1.0/pullups-1.1.0.apk",
              "sha256": "ABCDEF0123",
              "minSdk": 34,
              "releaseNotes": "  Neue Statistik  "
            }
            """.trimIndent(),
        )
        assertEquals(10100, manifest.versionCode)
        assertEquals("1.1.0", manifest.versionName)
        assertEquals("abcdef0123", manifest.sha256)
        assertEquals(34, manifest.minSdk)
        assertEquals("Neue Statistik", manifest.releaseNotes)
    }

    @Test
    fun optionalFieldsMayBeMissing() {
        val manifest = UpdateManifest.parse("""{"versionCode": 2, "versionName": "1.0.1", "apkUrl": "https://x/app.apk"}""")
        assertNull(manifest.sha256)
        assertEquals(1, manifest.minSdk)
        assertEquals("", manifest.releaseNotes)
    }

    @Test(expected = JSONException::class)
    fun rejectsIncompleteManifests() {
        UpdateManifest.parse("""{"versionName": "1.0.1"}""")
    }
}
