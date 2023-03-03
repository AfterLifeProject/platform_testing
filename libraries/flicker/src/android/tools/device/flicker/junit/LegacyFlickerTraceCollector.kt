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

package android.tools.device.flicker.junit

import android.tools.common.CrossPlatform
import android.tools.common.IScenario
import android.tools.common.flicker.ITracesCollector
import android.tools.common.io.IReader
import android.tools.device.flicker.datastore.CachedResultReader
import android.tools.device.traces.DEFAULT_TRACE_CONFIG

class LegacyFlickerTraceCollector(private val scenario: IScenario) : ITracesCollector {
    override fun start() {}

    override fun stop() {}

    override fun getResultReader(): IReader {
        CrossPlatform.log.d("FAAS", "LegacyFlickerTraceCollector#getCollectedTraces")
        return CachedResultReader(scenario, DEFAULT_TRACE_CONFIG)
    }
}