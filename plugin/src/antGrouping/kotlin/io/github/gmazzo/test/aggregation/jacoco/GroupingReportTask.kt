package io.github.gmazzo.test.aggregation.jacoco

import java.io.IOException
import org.apache.tools.ant.BuildException
import org.apache.tools.ant.types.resources.Union
import org.jacoco.ant.ReportTask
import org.jacoco.core.analysis.IBundleCoverage
import org.jacoco.core.data.ExecutionData
import org.jacoco.core.data.SessionInfo
import org.jacoco.report.IReportVisitor
import org.jacoco.report.ISourceFileLocator

/**
 * A horrible hack class aiming to introduce support for mapping coverage files correctly to each group
 *
 * It relies on in two internals **likely to fail** in the future:
 * - The [ReportTask.executionDataStore] can it be how swapped while generating the report
 * - A group is about to be rendered when [GroupElement.children.isEmpty] is called
 */
class GroupingReportTask : ReportTask() {

    private val structure = StructureGroup(isRoot = true)

    private lateinit var loader: GroupingExecFileLoader

    init {
        FIELD_STRUCTURE.set(this, structure)
    }

    override fun execute() {
        loadGroupsExecutionData()
        ensureOnStartListener()

        super.execute()
    }

    private fun loadGroupsExecutionData()  {
        loader = GroupingExecFileLoader()

        for (group in structure.children) {
            loader.switchDataStore(group.name)

            for (resource in group.executiondataElement) {
                try {
                    resource.inputStream.use(loader::load)

                } catch (e: IOException) {
                    throw BuildException(
                        "Unable to read execution data file $resource, of group ${group.name}",
                        e, getLocation()
                    )
                }
            }
        }
    }

    private fun ensureOnStartListener() {
        @Suppress("UNCHECKED_CAST")
        val formatters = FIELD_FORMATTERS.get(this) as MutableList<IReportVisitor>

        if (formatters.firstOrNull() !is OnStartListener) {
            formatters.add(0, OnStartListener())
        }
    }

    override fun createExecutiondata(): Union {
        error("Define execution data in the group structure instead")
    }

    internal inner class StructureGroup(val isRoot: Boolean) : GroupElement() {

        @Suppress("UNCHECKED_CAST")
        val name get() = FIELD_NAME.get(this) as String

        @Suppress("UNCHECKED_CAST")
        val children = Children(FIELD_CHILDREN.get(this) as MutableList<StructureGroup>)
            .also { FIELD_CHILDREN.set(this, it) }

        val executiondataElement = Union()

        fun createExecutiondata() = executiondataElement

        override fun createGroup(): GroupElement =
            if (isRoot) StructureGroup(isRoot = false).also(children::add)
            else super.createGroup()

        @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
        inner class Children(
            private val delegate: MutableList<StructureGroup>,
        ) : MutableList<StructureGroup> by delegate {

            override fun isEmpty(): Boolean {
                setGroupDataStore()
                return delegate.isEmpty()
            }

            private fun setGroupDataStore() {
                val dataStore = loader.switchDataStore(this@StructureGroup.name)

                FIELD_EXECUTION_DATA_STORE.set(this@GroupingReportTask, dataStore)
            }

        }

    }

    private inner class OnStartListener :
        IReportVisitor,
        CSVFormatterElement() { // yeah, this is just to make it inherit from FormatterElement

        override fun createVisitor(): IReportVisitor {
            FIELD_SESSION_INFO_STORE.set(this@GroupingReportTask, loader.sessionInfoStore)
            return this
        }

        override fun visitInfo(
            sessionInfos: List<SessionInfo>,
            executionData: Collection<ExecutionData>
        ) {
        }

        override fun visitEnd() {
        }

        override fun visitBundle(bundle: IBundleCoverage, locator: ISourceFileLocator) {
        }

        override fun visitGroup(name: String) = this

    }

    private companion object {

        val FIELD_STRUCTURE = ReportTask::class.java
            .getDeclaredField("structure")
            .apply { isAccessible = true }

        val FIELD_FORMATTERS = ReportTask::class.java
            .getDeclaredField("formatters")
            .apply { isAccessible = true }

        val FIELD_SESSION_INFO_STORE = ReportTask::class.java
            .getDeclaredField("sessionInfoStore")
            .apply { isAccessible = true }

        val FIELD_EXECUTION_DATA_STORE = ReportTask::class.java
            .getDeclaredField("executionDataStore")
            .apply { isAccessible = true }

        val FIELD_NAME = GroupElement::class.java
            .getDeclaredField("name")
            .apply { isAccessible = true }

        val FIELD_CHILDREN = GroupElement::class.java
            .getDeclaredField("children")
            .apply { isAccessible = true }

    }

}
