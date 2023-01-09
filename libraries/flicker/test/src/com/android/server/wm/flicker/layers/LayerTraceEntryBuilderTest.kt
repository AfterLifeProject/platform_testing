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

package com.android.server.wm.flicker.layers

import com.android.server.wm.traces.common.Timestamp
import com.android.server.wm.traces.common.layers.LayerTraceEntryBuilder
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [LayerTraceEntryBuilder] tests. To run this test: `atest
 * FlickerLibTest:LayerTraceEntryBuilderTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LayerTraceEntryBuilderTest {

    @Test
    fun createsEntryWithCorrectClockTime() {
        val builder =
            LayerTraceEntryBuilder(
                _elapsedTimestamp = "100",
                layers = emptyArray(),
                displays = emptyArray(),
                vSyncId = 123,
                realToElapsedTimeOffsetNs = "500"
            )
        val entry = builder.build()
        Truth.assertThat(entry.elapsedTimestamp).isEqualTo(100)
        Truth.assertThat(entry.clockTimestamp).isEqualTo(600)

        Truth.assertThat(entry.timestamp.elapsedNanos).isEqualTo(Timestamp.EMPTY.elapsedNanos)
        Truth.assertThat(entry.timestamp.systemUptimeNanos).isEqualTo(100)
        Truth.assertThat(entry.timestamp.unixNanos).isEqualTo(600)
    }

    @Test
    fun supportsMissingRealToElapsedTimeOffsetNs() {
        val builder =
            LayerTraceEntryBuilder(
                _elapsedTimestamp = "100",
                layers = emptyArray(),
                displays = emptyArray(),
                vSyncId = 123,
            )
        val entry = builder.build()
        Truth.assertThat(entry.elapsedTimestamp).isEqualTo(100)
        Truth.assertThat(entry.clockTimestamp).isEqualTo(null)

        Truth.assertThat(entry.timestamp.elapsedNanos).isEqualTo(Timestamp.EMPTY.elapsedNanos)
        Truth.assertThat(entry.timestamp.systemUptimeNanos).isEqualTo(100)
        Truth.assertThat(entry.timestamp.unixNanos).isEqualTo(Timestamp.EMPTY.unixNanos)
    }
}