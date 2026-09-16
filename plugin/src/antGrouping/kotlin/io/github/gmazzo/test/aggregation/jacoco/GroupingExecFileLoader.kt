package io.github.gmazzo.test.aggregation.jacoco

import org.jacoco.core.data.ExecutionDataStore
import org.jacoco.core.tools.ExecFileLoader

internal class GroupingExecFileLoader : ExecFileLoader() {

    private val executionDataGroups = mutableMapOf<String, ExecutionDataStore>()

    fun switchDataStore(name: String) = executionDataGroups
        .getOrPut(name, ::ExecutionDataStore)
        .also { FIELD_EXECUTION_DATA.set(this@GroupingExecFileLoader, it) }

    override fun getExecutionDataStore() =
        throw UnsupportedOperationException("Use executionDataGroups instead")

    private companion object {

        private val FIELD_EXECUTION_DATA = ExecFileLoader::class.java
            .getDeclaredField("executionData")
            .apply { isAccessible = true }

    }

}
