/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.tools.device.flicker

import android.app.Instrumentation
import android.device.collectors.BaseMetricListener
import android.device.collectors.DataRecord
import android.tools.common.FLICKER_TAG
import android.tools.common.Logger
import android.tools.common.ScenarioBuilder
import android.tools.common.flicker.AssertionInvocationGroup
import android.tools.common.flicker.FlickerConfig
import android.tools.common.flicker.FlickerService
import android.tools.common.flicker.TracesCollector
import android.tools.common.flicker.assertions.AssertionResult
import android.tools.common.flicker.config.FlickerServiceConfig
import android.tools.common.flicker.config.ScenarioId
import android.tools.common.io.RunStatus
import androidx.test.platform.app.InstrumentationRegistry
import com.android.internal.annotations.VisibleForTesting
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure

/**
 * Collects all the Flicker Service's metrics which are then uploaded for analysis and monitoring to
 * the CrystalBall database.
 */
class FlickerServiceResultsCollector(
    private val tracesCollector: TracesCollector,
    private val flickerService: FlickerService =
        FlickerService(FlickerConfig().use(FlickerServiceConfig.DEFAULT)),
    instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
    private val collectMetricsPerTest: Boolean = true,
    private val reportOnlyForPassingTests: Boolean = true
) : BaseMetricListener(), IFlickerServiceResultsCollector {
    private var hasFailedTest = false
    private var testSkipped = false

    private val _executionErrors = mutableListOf<Throwable>()
    override val executionErrors
        get() = _executionErrors

    @VisibleForTesting val assertionResults = mutableListOf<AssertionResult>()

    @VisibleForTesting
    val assertionResultsByTest = mutableMapOf<Description, Collection<AssertionResult>>()

    @VisibleForTesting
    val detectedScenariosByTest = mutableMapOf<Description, Collection<ScenarioId>>()

    init {
        setInstrumentation(instrumentation)
    }

    override fun onTestRunStart(runData: DataRecord, description: Description) {
        errorReportingBlock {
            tracesCollector.cleanup() // Cleanup any trace archives from previous runs

            Logger.i(LOG_TAG, "onTestRunStart :: collectMetricsPerTest = $collectMetricsPerTest")
            if (!collectMetricsPerTest) {
                hasFailedTest = false
                val scenario =
                    ScenarioBuilder()
                        .forClass(description.testClass.canonicalName)
                        .withDescriptionOverride("")
                        .build()
                tracesCollector.start(scenario)
            }
        }
    }

    override fun onTestStart(testData: DataRecord, description: Description) {
        errorReportingBlock {
            Logger.i(LOG_TAG, "onTestStart :: collectMetricsPerTest = $collectMetricsPerTest")
            if (collectMetricsPerTest) {
                hasFailedTest = false
                val scenario =
                    ScenarioBuilder()
                        .forClass(
                            "${description.testClass.canonicalName}#${description.methodName}"
                        )
                        .withDescriptionOverride("")
                        .build()
                tracesCollector.start(scenario)
            }
            testSkipped = false
        }
    }

    override fun onTestFail(testData: DataRecord, description: Description, failure: Failure) {
        errorReportingBlock {
            Logger.i(LOG_TAG, "onTestFail")
            hasFailedTest = true
        }
    }

    override fun testSkipped(description: Description) {
        errorReportingBlock {
            Logger.i(LOG_TAG, "testSkipped")
            testSkipped = true
        }
    }

    override fun onTestEnd(testData: DataRecord, description: Description) {
        errorReportingBlock {
            Logger.i(LOG_TAG, "onTestEnd :: collectMetricsPerTest = $collectMetricsPerTest")
            if (collectMetricsPerTest && !testSkipped) {
                stopTracingAndCollectFlickerMetrics(testData, description)
            }
        }

        if (collectMetricsPerTest) {
            reportFlickerServiceStatus(testData)
        }
    }

    override fun onTestRunEnd(runData: DataRecord, result: Result) {
        errorReportingBlock {
            Logger.i(LOG_TAG, "onTestRunEnd :: collectMetricsPerTest = $collectMetricsPerTest")
            if (!collectMetricsPerTest) {
                stopTracingAndCollectFlickerMetrics(runData)
            }
        }

        if (!collectMetricsPerTest) {
            reportFlickerServiceStatus(runData)
        }
    }

    private fun stopTracingAndCollectFlickerMetrics(
        dataRecord: DataRecord,
        description: Description? = null
    ) {
        errorReportingBlock {
            Logger.i(LOG_TAG, "Stopping trace collection")
            val reader = tracesCollector.stop()
            Logger.i(LOG_TAG, "Stopped trace collection")
            if (reportOnlyForPassingTests && hasFailedTest) {
                return@errorReportingBlock
            }

            try {
                Logger.i(LOG_TAG, "Processing traces")
                val scenarios = flickerService.detectScenarios(reader)
                val results = scenarios.flatMap { it.generateAssertions() }.map { it.execute() }
                reader.artifact.updateStatus(RunStatus.RUN_EXECUTED)
                Logger.i(LOG_TAG, "Got ${results.size} results")
                assertionResults.addAll(results)
                if (description != null) {
                    require(assertionResultsByTest[description] == null) {
                        "Test description already contains flicker assertion results."
                    }
                    require(detectedScenariosByTest[description] == null) {
                        "Test description already contains detected scenarios."
                    }
                    assertionResultsByTest[description] = results
                    detectedScenariosByTest[description] = scenarios.map { it.type }.distinct()
                }
                if (results.any { it.failed }) {
                    reader.artifact.updateStatus(RunStatus.ASSERTION_FAILED)
                } else {
                    reader.artifact.updateStatus(RunStatus.ASSERTION_SUCCESS)
                }

                Logger.v(LOG_TAG, "Adding metric $FLICKER_ASSERTIONS_COUNT_KEY = ${results.size}")
                dataRecord.addStringMetric(FLICKER_ASSERTIONS_COUNT_KEY, "${results.size}")

                val aggregatedResults = processFlickerResults(results)
                collectMetrics(dataRecord, aggregatedResults)
            } finally {
                Logger.v(LOG_TAG, "Adding metric $WINSCOPE_FILE_PATH_KEY = ${reader.artifactPath}")
                dataRecord.addStringMetric(WINSCOPE_FILE_PATH_KEY, reader.artifactPath)
            }
        }
    }

    private fun processFlickerResults(
        results: Collection<AssertionResult>
    ): Map<String, AggregatedFlickerResult> {
        val aggregatedResults = mutableMapOf<String, AggregatedFlickerResult>()
        for (result in results) {
            val key = getKeyForAssertionResult(result)
            if (!aggregatedResults.containsKey(key)) {
                aggregatedResults[key] = AggregatedFlickerResult()
            }
            aggregatedResults[key]!!.addResult(result)
        }
        return aggregatedResults
    }

    private fun collectMetrics(
        data: DataRecord,
        aggregatedResults: Map<String, AggregatedFlickerResult>
    ) {
        val it = aggregatedResults.entries.iterator()

        while (it.hasNext()) {
            val (key, aggregatedResult) = it.next()
            aggregatedResult.results.forEachIndexed { index, result ->
                val resultStatus = if (result.passed) 0 else 1
                Logger.v(LOG_TAG, "Adding metric ${key}_$index = $resultStatus")
                data.addStringMetric("${key}_$index", "$resultStatus")
            }
        }
    }

    private fun errorReportingBlock(function: () -> Unit) {
        try {
            function()
        } catch (e: Throwable) {
            Logger.e(FLICKER_TAG, "Error executing in FlickerServiceResultsCollector", e)
            _executionErrors.add(e)
        }
    }

    override fun resultsForTest(description: Description): Collection<AssertionResult> {
        val resultsForTest = assertionResultsByTest[description]
        requireNotNull(resultsForTest) { "No results set for test $description" }
        return resultsForTest
    }

    override fun detectedScenariosForTest(description: Description): Collection<ScenarioId> {
        val scenariosForTest = detectedScenariosByTest[description]
        requireNotNull(scenariosForTest) { "No detected scenarios set for test $description" }
        return scenariosForTest
    }

    private fun reportFlickerServiceStatus(record: DataRecord) {
        val status = if (executionErrors.isEmpty()) OK_STATUS_CODE else EXECUTION_ERROR_STATUS_CODE
        record.addStringMetric("${FAAS_METRICS_PREFIX}_STATUS", status.toString())
    }

    companion object {
        // Unique prefix to add to all FaaS metrics to identify them
        const val FAAS_METRICS_PREFIX = "FAAS"
        private const val LOG_TAG = "$FLICKER_TAG-Collector"
        const val WINSCOPE_FILE_PATH_KEY = "winscope_file_path"
        const val FLICKER_ASSERTIONS_COUNT_KEY = "flicker_assertions_count"
        const val OK_STATUS_CODE = 0
        const val EXECUTION_ERROR_STATUS_CODE = 1

        fun getKeyForAssertionResult(result: AssertionResult): String {
            return "$FAAS_METRICS_PREFIX::${result.name}"
        }

        class AggregatedFlickerResult {
            val results = mutableListOf<AssertionResult>()
            var failures = 0
            var passes = 0
            val errors = mutableListOf<String>()
            var invocationGroup: AssertionInvocationGroup? = null

            fun addResult(result: AssertionResult) {
                results.add(result)

                if (result.failed) {
                    failures++
                    result.assertionErrors.forEach {
                        errors.add(it.message ?: "FAILURE WITHOUT ERROR MESSAGE...")
                    }
                } else {
                    passes++
                }

                if (invocationGroup == null) {
                    invocationGroup = result.stabilityGroup
                }

                if (invocationGroup != result.stabilityGroup) {
                    error("Unexpected assertion group mismatch")
                }
            }
        }
    }
}
