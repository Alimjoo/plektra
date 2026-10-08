package rocks.pkb.plektra.update

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class UpdateGithubParserTest {

    @Test
    fun releaseNameIsParsedAndFallsBackToTag() {
        val releases = parseGitHubReleases(
            JSONArray(
                """[
                    {"tag_name":"v2","name":"Plektra 2","prerelease":false,"draft":false,"html_url":"https://example.com/v2"},
                    {"tag_name":"v1","name":"","prerelease":false,"draft":false,"html_url":"https://example.com/v1"}
                ]"""
            )
        )

        assertEquals("Plektra 2", findLatestRelease(releases, "stable")?.displayName)
        assertEquals("v1", findLatestRelease(releases.drop(1), "stable")?.displayName)
    }
    @Test
    fun selectsOwnVariantRegardlessOfAssetOrder() {
        for (variant in listOf("full", "no-accessibility")) {
            val own = apk(variant)
            val other = apk(if (variant == "full") "no-accessibility" else "full")
            for (assets in listOf(listOf(own, other), listOf(other, own))) {
                val release = parseGitHubReleases(releases(assets), variant).single()
                assertEquals("https://example.com/$variant.apk", release.downloadUrl)
                assertEquals(release.downloadUrl,
                    findNewerNightlyRelease(listOf(release), "26.10-nightly.07.010000Z")?.downloadUrl)
            }
        }
    }

    @Test
    fun absentVariantKeepsReleaseNoticeWithoutDownload() {
        val release = parseGitHubReleases(releases(listOf(apk("full"))), "no-accessibility").single()
        assertNull(release.downloadUrl)
        assertEquals("https://example.com/release",
            findNewerNightlyRelease(listOf(release), "26.10-nightly.07.010000Z")?.releasePageUrl)
        assertNull(parseGitHubReleases(releases(listOf(apk("no-accessibility"))), "full").single().downloadUrl)
    }

    @Test
    fun legacyGithubApkIsOnlyAcceptedByFullVariant() {
        val release = releases(listOf(apk("github")))
        assertEquals("https://example.com/github.apk", parseGitHubReleases(release, "full").single().downloadUrl)
        assertNull(parseGitHubReleases(release, "no-accessibility").single().downloadUrl)
    }

    @Test
    fun rejectsAmbiguousUnknownWrongVersionAndInsecureAssets() {
        for (assets in listOf(
            listOf(apk("full"), apk("full")),
            listOf(apk("full"), apk("github")),
            listOf(apk("play")),
            listOf(apk("full").replace("26.10-nightly.08.100000Z", "26.10-nightly.06.100000Z")),
            listOf(apk("full").replace("https://", "http://"))
        )) {
            assertNull(parseGitHubReleases(releases(assets), "full").single().downloadUrl)
        }
        assertNull(parseGitHubReleases(releases(listOf(apk("full"))), "unknown").single().downloadUrl)
    }

    private fun apk(variant: String): String =
        """{"name":"Plektra-26.10-nightly.08.100000Z-$variant.apk","browser_download_url":"https://example.com/$variant.apk"}"""

    private fun releases(assets: List<String>): JSONArray = JSONArray(
        """[{"tag_name":"nightly/v26.10-nightly.08.100000Z","prerelease":true,
            "html_url":"https://example.com/release","assets":[${assets.joinToString(",")}]}]"""
    )

}
