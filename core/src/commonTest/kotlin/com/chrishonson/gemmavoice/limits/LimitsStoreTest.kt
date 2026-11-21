package com.chrishonson.gemmavoice.limits

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LimitsStoreTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun firstLaunchStateIsOpenAndRequiresAcknowledgement() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = false)
        val store = LimitsStore(acknowledgementStore = ackStore)

        val state = store.state.value
        assertTrue(state.open)
        assertTrue(state.requiresAcknowledgement)

        // Dismiss leaves it open while requiresAcknowledgement is true
        store.dispatch(LimitsIntent.Dismiss)
        assertTrue(store.state.value.open)
        assertTrue(store.state.value.requiresAcknowledgement)

        // Acknowledge closes it and persists
        store.dispatch(LimitsIntent.Acknowledge)
        assertFalse(store.state.value.open)
        assertFalse(store.state.value.requiresAcknowledgement)
        assertTrue(ackStore.isLimitsAcknowledged())
    }

    @Test
    fun laterLaunchStartsClosedAndCanOpenAndDismiss() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = true)
        val store = LimitsStore(acknowledgementStore = ackStore)

        val initialState = store.state.value
        assertFalse(initialState.open)
        assertFalse(initialState.requiresAcknowledgement)

        store.dispatch(LimitsIntent.Open)
        assertTrue(store.state.value.open)

        store.dispatch(LimitsIntent.Dismiss)
        assertFalse(store.state.value.open)
    }

    @Test
    fun emptyFeatureConfigSafeguardsIsEmpty() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = true)
        val store = LimitsStore(
            acknowledgementStore = ackStore,
            featureConfig = FeatureConfig()
        )

        assertTrue(store.state.value.safeguards.isEmpty())
    }

    @Test
    fun featureConfigWithSafeguardsExposesThem() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = true)
        val safeguard = Safeguard(id = "T3", description = "Value check against source text")
        val store = LimitsStore(
            acknowledgementStore = ackStore,
            featureConfig = FeatureConfig(safeguards = listOf(safeguard))
        )

        assertEquals(listOf(safeguard), store.state.value.safeguards)
    }

    @Test
    fun licensesTransitionOpensAndCloses() {
        val ackStore = InMemoryAcknowledgementStore(acknowledged = true)
        val store = LimitsStore(acknowledgementStore = ackStore)

        assertFalse(store.state.value.showLicenses)
        assertEquals(LICENSE_NOTICES, store.state.value.licenses)

        store.dispatch(LimitsIntent.OpenLicenses)
        assertTrue(store.state.value.showLicenses)

        store.dispatch(LimitsIntent.CloseLicenses)
        assertFalse(store.state.value.showLicenses)
    }

    @Test
    fun dismissAndAcknowledgeResetShowLicenses() {
        val unackedStore = InMemoryAcknowledgementStore(acknowledged = false)
        val storeUnacked = LimitsStore(acknowledgementStore = unackedStore)

        storeUnacked.dispatch(LimitsIntent.OpenLicenses)
        assertTrue(storeUnacked.state.value.showLicenses)

        storeUnacked.dispatch(LimitsIntent.Acknowledge)
        assertFalse(storeUnacked.state.value.showLicenses)
        assertFalse(storeUnacked.state.value.open)

        val ackedStore = InMemoryAcknowledgementStore(acknowledged = true)
        val storeAcked = LimitsStore(acknowledgementStore = ackedStore)

        storeAcked.dispatch(LimitsIntent.Open)
        storeAcked.dispatch(LimitsIntent.OpenLicenses)
        assertTrue(storeAcked.state.value.showLicenses)

        storeAcked.dispatch(LimitsIntent.Dismiss)
        assertFalse(storeAcked.state.value.showLicenses)
        assertFalse(storeAcked.state.value.open)
    }
}
