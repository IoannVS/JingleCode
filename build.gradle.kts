import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.changelog)
    id("org.jetbrains.intellij.platform")
}

// The oldest supported IDE line: plugin.xml's since-build and the oldest IDE the verifier checks.
val oldestBuild = "251"

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // 2025.1 (since-build 251) bundles Kotlin 2.1: don't use newer stdlib API.
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
            // No until-build: every IDE from since-build on. The Gradle plugin no longer adds one by default.
            sinceBuild = oldestBuild
        }

        // "What's new" on Marketplace: this version's section of CHANGELOG.md, or [Unreleased] before patchChangelog.
        val pluginVersion = project.version.toString()
        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }
    }

    // publishPlugin, from CI on merge to master: .github/workflows/release.yml.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            // We compile against a newer IDE than since-build: the oldest line catches API added after it,
            // the latest release and EAP catch API removed since. IntelliJ IDEA holds the whole platform plus
            // Git, all we depend on; Marketplace checks the other IDEs itself on every upload.
            latest {
                types = listOf(IntelliJPlatformType.IntellijIdea)
                channels = listOf(ProductRelease.Channel.RELEASE)
                sinceBuild = oldestBuild
                untilBuild = "$oldestBuild.*"
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

changelog {
    groups.empty()
    repositoryUrl = "https://github.com/IoannVS/JingleCode"
}

tasks.runIde {
    // Pretend it's another day in the sandbox: ./gradlew runIde -PjingleNow=2026-12-31T23:59 (or a date alone).
    providers.gradleProperty("jingleNow").orNull?.let { systemProperty("jinglecode.now", it) }
}
