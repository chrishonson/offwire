package com.chrishonson.gemmavoice.limits

data class Safeguard(val id: String, val description: String)

class FeatureConfig(val safeguards: List<Safeguard> = emptyList())

val DEFAULT_FEATURE_CONFIG = FeatureConfig()
