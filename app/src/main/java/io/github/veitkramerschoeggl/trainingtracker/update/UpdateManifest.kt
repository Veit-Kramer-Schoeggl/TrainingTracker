package io.github.veitkramerschoeggl.trainingtracker.update

import org.json.JSONObject

/**
 * Content of `update.json`, published with every GitHub release by the release workflow:
 * ```
 * { "versionCode": 10100, "versionName": "1.1.0", "apkUrl": "https://…/pullups-1.1.0.apk",
 *   "sha256": "…", "minSdk": 34, "releaseNotes": "…" }
 * ```
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String?,
    val minSdk: Int,
    val releaseNotes: String,
) {
    companion object {
        fun parse(json: String): UpdateManifest {
            val obj = JSONObject(json)
            return UpdateManifest(
                versionCode = obj.getInt("versionCode"),
                versionName = obj.getString("versionName"),
                apkUrl = obj.getString("apkUrl"),
                sha256 = obj.optString("sha256").trim().lowercase().takeIf { it.isNotEmpty() },
                minSdk = obj.optInt("minSdk", 1),
                releaseNotes = obj.optString("releaseNotes").trim(),
            )
        }
    }
}
