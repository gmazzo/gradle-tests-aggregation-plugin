package io.github.gmazzo.test.aggregation

import com.android.build.api.extension.impl.CurrentAndroidGradlePluginVersion
import com.android.builder.model.Version.ANDROID_GRADLE_PLUGIN_VERSION
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.typeOf
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.util.GradleVersion

private const val AGGREGATE_EXTENSION_NAME = "aggregateTests"

internal fun Project.ensureMinVersions() {
    if (GradleVersion.current() < GradleVersion.version(BuildConfig.MIN_GRADLE_VERSION)) {
        error("This plugin requires Gradle ${BuildConfig.MIN_GRADLE_VERSION}} or later. Current is ${GradleVersion.current()}")
    }
    if (GradleVersion.version(agpVersion) < GradleVersion.version(BuildConfig.MIN_AGP_VERSION)) {
        error("This plugin requires Gradle ${BuildConfig.MIN_AGP_VERSION} or later. Current is $agpVersion")
    }
}

private val agpVersion
    get() = runCatching { CurrentAndroidGradlePluginVersion.CURRENT_AGP_VERSION.version }.getOrElse { ex1 ->
        runCatching { ANDROID_GRADLE_PLUGIN_VERSION }.getOrElse { ex2 ->
            ex1.addSuppressed(ex2)
            throw IllegalStateException(
                "Failed to get current AGP version, ${BuildConfig.MIN_AGP_VERSION} or later is required.",
                ex1
            )
        }
    }.replace("-.*$".toRegex(), "")

@Suppress("UNCHECKED_CAST")
internal fun ExtensionAware.aggregateTests(objects: ObjectFactory) =
    when (val existing = extensions.findByName(AGGREGATE_EXTENSION_NAME)) {
        null -> objects.property<Boolean>()
            .convention(true)
            .apply { finalizeValueOnRead() }
            .also { aggregateTests = it }

        else -> existing as Property<Boolean>
    }

@Suppress("UNCHECKED_CAST")
internal var ExtensionAware.aggregateTests: Property<Boolean>
    get() = extensions.getByName(AGGREGATE_EXTENSION_NAME) as Property<Boolean>
    set(value) {
        when (val existing = extensions.findByName(AGGREGATE_EXTENSION_NAME)) {
            null -> extensions.add(typeOf<Property<Boolean>>(), AGGREGATE_EXTENSION_NAME, value)
            else -> check(existing === value)
        }
    }

internal fun <Type : Task> Type.coverageData(
    getter: Type.() -> Any? = { extensions.findByType<JacocoTaskExtension>()?.destinationFile },
) = getter()
    ?: error("Coverage data for variant '$path' is missing. Did you $missingCoverageHint?")

private val Task.missingCoverageHint
    get() = when {
        project.plugins.hasPlugin("com.android.base") -> "added 'enable${if (this is AbstractTestTask) "Unit" else "Android"}TestCoverage = true'"
        project.plugins.hasPlugin("com.android.library.multiplatform") -> "added 'with${if (this is AbstractTestTask) "Host" else "Device"}Test { enableCoverage = true }'"
        else -> "applied the 'jacoco' plugin"
    }

internal val String.capitalized: String
    get() = replaceFirstChar { it.uppercase() }

@Suppress("UNCHECKED_CAST")
internal fun <Type : Task> Project.tasksMatching(
    name: String,
    configure:
    Action<Type> = {},
) = tasksMatching(Regex.fromLiteral(name), configure)

@Suppress("UNCHECKED_CAST")
internal fun <Type : Task> Project.tasksMatching(
    regex: Regex,
    configure:
    Action<Type> = {},
): Provider<List<Type>> = provider { tasks.names.filter { it.matches(regex) } }
    .map { names -> names.mapNotNull(tasks::findByName) as List<Type> }
