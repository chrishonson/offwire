package com.chrishonson.gemmavoice.limits

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThirdPartyNoticesTest {

    private fun findRepoRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "THIRD_PARTY_NOTICES.md").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not find repo root from ${File(".").canonicalPath}")
    }

    @Test
    fun licensesListMatchesThirdPartyNotices() {
        val noticesFile = File(findRepoRoot(), "THIRD_PARTY_NOTICES.md")
        assertTrue(noticesFile.exists(), "THIRD_PARTY_NOTICES.md must exist at repo root")

        val bulletRegex = Regex("""^-\s+\*\*(.*?)\*\*(.*)""")
        val parsedBullets = noticesFile.readLines()
            .mapNotNull { line ->
                bulletRegex.find(line.trim())?.let { match ->
                    val name = match.groupValues[1]
                    val text = line.trim()
                    name to text
                }
            }

        val bulletNames = parsedBullets.map { it.first }
        val kotlinNames = LICENSE_NOTICES.map { it.name }

        assertEquals(bulletNames, kotlinNames, "Parsed bullet names must match LICENSE_NOTICES in order")

        parsedBullets.forEachIndexed { index, (name, bulletText) ->
            val notice = LICENSE_NOTICES[index]
            assertEquals(name, notice.name)
            assertTrue(
                bulletText.contains(notice.license, ignoreCase = true),
                "Bullet for '$name' must mention license '${notice.license}'. Line: $bulletText"
            )
        }
    }
}
