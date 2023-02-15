/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.wm.flicker.junit

import com.android.server.wm.flicker.FlickerTest
import com.android.server.wm.flicker.Utils
import com.android.server.wm.traces.common.CrossPlatform
import com.android.server.wm.traces.common.TimestampFactory
import com.android.server.wm.traces.parser.ANDROID_LOGGER
import org.junit.runner.Runner
import org.junit.runners.parameterized.ParametersRunnerFactory
import org.junit.runners.parameterized.TestWithParameters

/**
 * A {@code FlickerRunnerFactory} creates a runner for a single {@link TestWithParameters}. Parses
 * and executes assertions from a flicker DSL
 */
class FlickerParametersRunnerFactory : ParametersRunnerFactory {
    init {
        CrossPlatform.setLogger(ANDROID_LOGGER)
            .setTimestampFactory(TimestampFactory { Utils.formatRealTimestamp(it) })
    }

    override fun createRunnerForTestWithParameters(test: TestWithParameters): Runner {
        val simpleClassName = test.testClass.javaClass.simpleName
        val flickerTest =
            test.parameters.filterIsInstance<FlickerTest>().firstOrNull()
                ?: error(
                    "Unable to extract ${FlickerTest::class.simpleName} for class $simpleClassName"
                )
        val scenario = flickerTest.initialize(simpleClassName)
        val newTest =
            TestWithParameters(
                /*name */ "[${scenario.description}]",
                /* testClass */ test.testClass,
                /* parameters */ test.parameters
            )
        return FlickerBlockJUnit4ClassRunner(newTest, scenario)
    }
}
