plugins {
    id("com.android.asset-pack")
}

// Play caps an app's base module at 200 MB, and the Parakeet and MiniLM models are 183 MB of the classic app. In an
// app bundle they ship in this install-time pack instead: Play installs it with the app, and the app reads the files
// through AssetManager at the same paths as before. src/main/assets links to the models in core's assets, so APK
// builds, the iOS app and the scripts still find them in one place.
assetPack {
    packName.set("model_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
