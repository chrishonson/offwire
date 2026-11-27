package com.chrishonson.gemmavoice.devicecheck

import com.chrishonson.gemmavoice.limits.InMemoryAcknowledgementStore
import com.chrishonson.gemmavoice.model.ModelSpec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceCheckStoreTest {
    @Test
    fun testedDeviceIsConfirmed() = runTest {
        val store = store(device = device(model = "Pixel 9 Pro", chipset = "Tensor G4"))

        assertTrue(store.state.value.testedDevice)
        assertContains(store.state.value.verdict, "tested device")
    }

    @Test
    fun untestedDeviceWithEnoughRamWarnsWithoutBlocking() = runTest {
        val store = store(device = device(model = "Other phone", ram = 12_000_000_000L))

        assertFalse(store.state.value.lowRam)
        assertFalse(store.state.value.storageBlocked)
        assertContains(store.state.value.verdict, "Tested on a Pixel 9 Pro")
        assertContains(store.state.value.verdict, "Your phone has 12.0 GB RAM")
        assertContains(store.state.value.verdict, "meets the minimum")
    }

    @Test
    fun iosConfigAcceptsATestedThreeGigabyteDevice() = runTest {
        val store = store(device = device(model = "iPhone", ram = 2_990_000_000L), config = IOS_DEVICE_CHECK_CONFIG)

        assertFalse(store.state.value.lowRam)
        assertContains(store.state.value.verdict, "Tested on an iPad (9th generation)")
        assertContains(store.state.value.verdict, "Your device has 3.0 GB RAM")
        assertFalse(store.state.value.verdict.contains("Pixel"))
    }

    @Test
    fun tooLittleRamWarnsButDoesNotBlock() = runTest {
        val store = store(device = device(ram = 4_000_000_000L))

        assertTrue(store.state.value.lowRam)
        assertFalse(store.state.value.storageBlocked)
        assertContains(store.state.value.verdict, "below the minimum")
        store.dispatch(DeviceCheckIntent.Continue)
        assertFalse(store.state.value.requiresCheck)
    }

    @Test
    fun tooLittleStorageBlocksAndExplainsNeededVersusFree() = runTest {
        val store = store(device = device(storage = 1_000_000_000L))

        assertTrue(store.state.value.storageBlocked)
        assertContains(store.state.value.storageMessage, "Not enough free storage")
        assertContains(store.state.value.storageMessage, "3.6 GB needed")
        assertContains(store.state.value.storageMessage, "1.0 GB free")
        store.dispatch(DeviceCheckIntent.Continue)
        assertTrue(store.state.value.requiresCheck)
    }

    @Test
    fun completionStoreMakesSecondStoreSkipWizard() = runTest {
        val completion = InMemoryDeviceCheckCompletionStore()
        val first = store(completion = completion)
        first.dispatch(DeviceCheckIntent.Continue)

        val second = store(completion = completion)

        assertFalse(second.state.value.requiresCheck)
    }

    @Test
    fun continueHandsOffToExistingLimitsFlow() = runTest {
        val completion = InMemoryDeviceCheckCompletionStore()
        val store = store(completion = completion)

        store.dispatch(DeviceCheckIntent.Continue)

        assertFalse(store.state.value.requiresCheck)
        assertTrue(store.state.value.handoffToLimits)
        assertTrue(completion.isDeviceCheckCompleted())
    }

    @Test
    fun existingLimitsAcknowledgementSkipsAndMigratesCompletion() = runTest {
        val completion = InMemoryDeviceCheckCompletionStore()
        val store = store(
            completion = completion,
            acknowledgement = InMemoryAcknowledgementStore(acknowledged = true)
        )

        assertFalse(store.state.value.requiresCheck)
        assertTrue(completion.isDeviceCheckCompleted())
    }

    private fun store(
        device: DeviceInfo = device(),
        completion: DeviceCheckCompletionStore = InMemoryDeviceCheckCompletionStore(),
        acknowledgement: InMemoryAcknowledgementStore = InMemoryAcknowledgementStore(),
        config: DeviceCheckConfig = DeviceCheckConfig(
            testedDevices = listOf(TestedDevice("Pixel 9 Pro", "Tensor G4")),
            minRamBytes = 8_000_000_000L,
            storageHeadroomBytes = 1_000_000_000L
        )
    ) = DeviceCheckStore(
        deviceInfo = device,
        completionStore = completion,
        acknowledgementStore = acknowledgement,
        config = config,
        modelSpec = MODEL,
        voiceSpec = null
    )

    private fun device(
        model: String = "Other phone",
        chipset: String = "Other chip",
        ram: Long = 12_000_000_000L,
        storage: Long = 10_000_000_000L
    ) = FakeDeviceInfo(
        totalRamBytes = ram,
        freeStorageBytes = storage,
        modelName = model,
        chipset = chipset
    )

    private companion object {
        val MODEL = ModelSpec(
            id = "test",
            displayName = "Test model",
            url = "https://example.invalid/model",
            fileName = "model.bin",
            sizeBytes = 2_588_147_712L
        )
    }
}
