// Top-level build file. Versions are pinned so the GitHub Actions build
// is reproducible. AGP 8.5.2 + Kotlin 1.9.24 + Gradle 8.7 + JDK 17 is a
// known-good combination; keep them in sync when upgrading.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
