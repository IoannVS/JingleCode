import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // 2025.1 (sinceBuild 251) bundles Kotlin 2.1: don't use newer stdlib API.
        apiVersion = KotlinVersion.KOTLIN_2_1
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea("2025.3.6.1")
        // Optional at runtime (see jingle-code-git.xml); only the git package uses it.
        bundledPlugin("Git4Idea")
    }
}

intellijPlatform {
    // Only needed for GUI Designer forms and Java @NotNull checks; we have neither.
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            // The oldest supported line, the latest release and the latest EAP.
            latest {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = "251"
                untilBuild = "251.*"
            }
            latest {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.RELEASE)
            }
            latest {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.EAP)
            }
        }
    }
}

tasks.runIde {
    // Pretend it's another day in the sandbox: ./gradlew runIde -PjingleNow=2026-12-31T23:59 (or a date alone).
    providers.gradleProperty("jingleNow").orNull?.let { systemProperty("jinglecode.now", it) }
}
