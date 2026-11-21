package com.chrishonson.gemmavoice.limits

const val MODEL_LICENSE_NOTICE =
    "Runs Gemma 4, licensed under Apache 2.0. Offwire is not affiliated with Google."
const val APACHE_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

data class LicenseNotice(
    val name: String,
    val license: String,
    val url: String? = null
)

val LICENSE_NOTICES: List<LicenseNotice> = listOf(
    LicenseNotice(
        name = "Gemma 4 E2B and E4B",
        license = "Apache License 2.0",
        url = APACHE_LICENSE_URL
    ),
    LicenseNotice(
        name = "LiteRT-LM",
        license = "Apache License 2.0",
        url = APACHE_LICENSE_URL
    ),
    LicenseNotice(
        name = "Silero VAD",
        license = "MIT License",
        url = "https://github.com/snakers4/silero-vad/blob/master/LICENSE"
    ),
    LicenseNotice(
        name = "ONNX Runtime",
        license = "MIT License",
        url = "https://github.com/microsoft/onnxruntime/blob/main/LICENSE"
    ),
    LicenseNotice(
        name = "NVIDIA Parakeet TDT 110M (English, int8)",
        license = "CC-BY-4.0",
        url = "https://creativecommons.org/licenses/by/4.0/"
    ),
    LicenseNotice(
        name = "sherpa-onnx",
        license = "Apache License 2.0",
        url = "https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE"
    ),
    LicenseNotice(
        name = "Pocket TTS",
        license = "CC-BY-4.0",
        url = "https://creativecommons.org/licenses/by/4.0/"
    ),
    LicenseNotice(
        name = "Voice conditioning",
        license = "CC0-1.0",
        url = "https://creativecommons.org/publicdomain/zero/1.0/"
    ),
    LicenseNotice(
        name = "Apache Commons Compress",
        license = "Apache License 2.0",
        url = APACHE_LICENSE_URL
    ),
    LicenseNotice(
        name = "all-MiniLM-L6-v2",
        license = "Apache License 2.0",
        url = APACHE_LICENSE_URL
    ),
    LicenseNotice(
        name = "Antonio",
        license = "SIL Open Font License 1.1",
        url = "https://openfontlicense.org"
    )
)
