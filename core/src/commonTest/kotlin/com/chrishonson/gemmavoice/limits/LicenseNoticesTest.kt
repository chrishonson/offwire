package com.chrishonson.gemmavoice.limits

import kotlin.test.Test
import kotlin.test.assertEquals

class LicenseNoticesTest {

    @Test
    fun modelLicenseNoticeMatchesExactRequiredString() {
        assertEquals(
            "Runs Gemma 4, licensed under Apache 2.0. Offwire is not affiliated with Google.",
            MODEL_LICENSE_NOTICE
        )
    }

    @Test
    fun apacheLicenseUrlIsExpected() {
        assertEquals("https://www.apache.org/licenses/LICENSE-2.0", APACHE_LICENSE_URL)
    }

    @Test
    fun gemmaEntryIsApache() {
        val gemma = LICENSE_NOTICES.first { it.name.startsWith("Gemma 4") }
        assertEquals("Apache License 2.0", gemma.license)
        assertEquals(APACHE_LICENSE_URL, gemma.url)
    }
}
