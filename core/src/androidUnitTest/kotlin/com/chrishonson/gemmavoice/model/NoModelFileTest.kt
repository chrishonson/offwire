package com.chrishonson.gemmavoice.model

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NoModelFileTest {

    private fun findRepoRoot(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not find repo root from ${File(".").canonicalPath}")
    }

    @Test
    fun noLitertlmModelFilesCommittedOrBundled() {
        val root = findRepoRoot()
        val foundFiles = mutableListOf<File>()

        fun scan(dir: File) {
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (file.isDirectory) {
                    val name = file.name
                    if (name == "build" || name == ".gradle" || name == ".git") {
                        continue
                    }
                    scan(file)
                } else if (file.name.endsWith(".litertlm")) {
                    foundFiles.add(file)
                }
            }
        }
        scan(root)

        assertTrue(
            foundFiles.isEmpty(),
            "No *.litertlm files should exist in the repository outside build/. Found: $foundFiles"
        )
    }

    @Test
    fun noLitertlmModelFilesInAnyAssetsOrResourcesDirs() {
        val root = findRepoRoot()
        val foundFiles = mutableListOf<File>()

        fun scan(dir: File) {
            val children = dir.listFiles() ?: return
            for (file in children) {
                if (file.isDirectory) {
                    scan(file)
                } else if (file.name.endsWith(".litertlm")) {
                    val path = file.canonicalPath
                    if (path.contains("/assets/") || path.contains("/resources/")) {
                        foundFiles.add(file)
                    }
                }
            }
        }
        scan(root)

        assertTrue(
            foundFiles.isEmpty(),
            "No *.litertlm files should exist under any assets or resources directory. Found: $foundFiles"
        )
    }

    @Test
    fun gemmaModelUrlPointsToHuggingFace() {
        assertTrue(
            Models.GEMMA_4_E2B.url.startsWith("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/"),
            "Model URL must start with expected prefix but was ${Models.GEMMA_4_E2B.url}"
        )
    }
}
