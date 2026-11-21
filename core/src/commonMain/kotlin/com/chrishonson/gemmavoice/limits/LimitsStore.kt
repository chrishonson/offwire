package com.chrishonson.gemmavoice.limits

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LimitsState(
    val open: Boolean,
    val requiresAcknowledgement: Boolean,
    val safeguards: List<Safeguard> = emptyList(),
    val showLicenses: Boolean = false,
    val licenses: List<LicenseNotice> = LICENSE_NOTICES
)

sealed interface LimitsIntent {
    data object Open : LimitsIntent
    data object Dismiss : LimitsIntent
    data object Acknowledge : LimitsIntent
    data object OpenLicenses : LimitsIntent
    data object CloseLicenses : LimitsIntent
}

class LimitsStore(
    private val acknowledgementStore: AcknowledgementStore,
    featureConfig: FeatureConfig = DEFAULT_FEATURE_CONFIG
) : ViewModel() {
    private val isAcknowledged = acknowledgementStore.isLimitsAcknowledged()
    private val _state = MutableStateFlow(
        LimitsState(
            open = !isAcknowledged,
            requiresAcknowledgement = !isAcknowledged,
            safeguards = featureConfig.safeguards
        )
    )
    val state: StateFlow<LimitsState> = _state.asStateFlow()

    fun dispatch(intent: LimitsIntent) {
        when (intent) {
            LimitsIntent.Open -> _state.update { it.copy(open = true) }
            LimitsIntent.Dismiss -> {
                if (!_state.value.requiresAcknowledgement) {
                    _state.update { it.copy(open = false, showLicenses = false) }
                }
            }
            LimitsIntent.Acknowledge -> {
                acknowledgementStore.saveLimitsAcknowledged()
                _state.update {
                    it.copy(
                        open = false,
                        requiresAcknowledgement = false,
                        showLicenses = false
                    )
                }
            }
            LimitsIntent.OpenLicenses -> _state.update { it.copy(showLicenses = true) }
            LimitsIntent.CloseLicenses -> _state.update { it.copy(showLicenses = false) }
        }
    }
}
