package io.github.gmazzo.test.aggregation

import io.github.gmazzo.test.aggregation.AndroidSupport.enableCoverageDSLHint
import io.github.gmazzo.test.aggregation.TestAggregationCoverageReport.Content
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.typeOf
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

internal lateinit var objectsRef: WeakReference<ObjectFactory>

private val objects: ObjectFactory
    get() = checkNotNull(objectsRef.get()) {
        "Apply the 'io.github.gmazzo.test.aggregation' plugin before accessing the 'objects' property"
    }

@Suppress("UNCHECKED_CAST")
private fun ExtensionAware.createAggregateExtension(name: String, defaultValue: Property<Boolean>? = null): Property<Boolean> =
    when (val existing = extensions.findByName(name)) {
        null -> objects.property<Boolean>().apply {
            if (defaultValue != null) convention(defaultValue) else convention(true)
            finalizeValueOnRead()
            extensions.add(typeOf<Property<Boolean>>(), name, this)
        }

        else -> existing as Property<Boolean>
    }

@Suppress("UNCHECKED_CAST")
internal val ExtensionAware.aggregateTests: Property<Boolean>
    get() = createAggregateExtension(::aggregateTests.name)

@Suppress("UNCHECKED_CAST")
internal val ExtensionAware.aggregateTestResults: Property<Boolean>
    get() = createAggregateExtension(::aggregateTestResults.name, aggregateTests)

@Suppress("UNCHECKED_CAST")
internal val ExtensionAware.aggregateTestCoverage: Property<Boolean>
    get() = createAggregateExtension(::aggregateTestCoverage.name, aggregateTests)

internal fun <Type : Task> Type.coverageData(
    getter: Type.() -> Any? = { jacocoDataFile },
) = getter()
    ?: error("Coverage data for variant '$path' is missing. Did you $missingCoverageHint?")

internal val Task.jacocoDataFile
    get() = extensions.findByType<JacocoTaskExtension>()?.destinationFile

private val Task.missingCoverageHint
    get() = when {
        project.plugins.hasPlugin("com.android.base") -> "added '${enableCoverageDSLHint}'"
        project.plugins.hasPlugin("com.android.library.multiplatform") -> "added 'with${if (this is AbstractTestTask) "Host" else "Device"}Test { enableCoverage = true }'"
        else -> "applied the 'jacoco' plugin"
    }

internal val String.capitalized: String
    get() = replaceFirstChar { it.uppercase() }

internal fun Project.tasksMatching(
    name: String,
    configure: Action<Task>,
) = tasksMatching(regex = Regex.fromLiteral(name), configure = configure)

internal fun Project.tasksMatching(
    regex: Regex,
    configuredFlag: AtomicBoolean = AtomicBoolean(false),
    configure: Action<Task>,
) = provider { tasks.names.filter { it.matches(regex) } }
    .map { names -> names.mapNotNull(tasks::findByName) }
    .map { list ->
        if (!configuredFlag.getAndSet(true)) {
            for (task in list) {
                configure.execute(task)
            }
        }
        list
    }

internal fun FileCollection.filtered(by: Content) = asFileTree.matching {
    include(by.includes.get())
    exclude(by.excludes.get())
}
