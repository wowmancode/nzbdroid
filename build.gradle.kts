// Top-level build file
plugins {
    id("com.android.application") version "8.6.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    // Kotlin 2.0+ ships the Compose compiler as its own plugin (must match the Kotlin version)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
