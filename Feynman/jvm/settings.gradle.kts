// Builds and tests the app's plain-Kotlin parts (physics, LaTeX layout, line drawing) on
// the JVM, without the Android SDK: gradle test (from this folder).
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories { mavenCentral() }
}
rootProject.name = "feynman-core"
