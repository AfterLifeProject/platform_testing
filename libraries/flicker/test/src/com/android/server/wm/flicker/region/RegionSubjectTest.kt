/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.wm.flicker.region

import com.android.server.wm.InitRule
import com.android.server.wm.flicker.assertThrows
import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.TimestampFactory
import com.android.server.wm.traces.common.subjects.FlickerSubjectException
import com.android.server.wm.traces.common.subjects.region.RegionSubject
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/** Contains [RegionSubject] tests. To run this test: `atest FlickerLibTest:RegionSubjectTest` */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RegionSubjectTest {
    private fun assertFail(expectedMessage: String, predicate: () -> Any) {
        val error = assertThrows<FlickerSubjectException> { predicate() }
        Truth.assertThat(error).hasMessageThat().contains(expectedMessage)
    }

    private fun expectAllFailPositionChange(expectedMessage: String, rectA: Rect, rectB: Rect) {
        assertFail(expectedMessage) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigher(rectB)
        }
        assertFail(expectedMessage) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigherOrEqual(rectB)
        }
        assertFail(expectedMessage) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLower(rectB)
        }
        assertFail(expectedMessage) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLowerOrEqual(rectB)
        }
    }

    @Test
    fun detectPositionChangeHigher() {
        val rectA = Rect.from(left = 0, top = 0, right = 1, bottom = 1)
        val rectB = Rect.from(left = 0, top = 1, right = 1, bottom = 2)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigher(rectB)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigherOrEqual(rectB)
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLower(rectB)
        }
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLowerOrEqual(rectB)
        }
    }

    @Test
    fun detectPositionChangeLower() {
        val rectA = Rect.from(left = 0, top = 2, right = 1, bottom = 3)
        val rectB = Rect.from(left = 0, top = 0, right = 1, bottom = 1)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLower(rectB)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLowerOrEqual(rectB)
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigher(rectB)
        }
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigherOrEqual(rectB)
        }
    }

    @Test
    fun detectPositionChangeEqualHigherLower() {
        val rectA = Rect.from(left = 0, top = 1, right = 1, bottom = 0)
        val rectB = Rect.from(left = 1, top = 1, right = 2, bottom = 0)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigherOrEqual(rectB)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLowerOrEqual(rectB)
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isHigher(rectB)
        }
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).isLower(rectB)
        }
    }

    @Test
    fun detectPositionChangeInvalid() {
        val rectA = Rect.from(left = 0, top = 1, right = 2, bottom = 2)
        val rectB = Rect.from(left = 1, top = 1, right = 2, bottom = 2)
        val rectC = Rect.from(left = 0, top = 1, right = 3, bottom = 1)
        expectAllFailPositionChange(RegionSubject.MSG_ERROR_LEFT_POSITION, rectA, rectB)
        expectAllFailPositionChange(RegionSubject.MSG_ERROR_RIGHT_POSITION, rectA, rectC)
    }

    @Test
    fun detectCoversAtLeast() {
        val rectA = Rect.from(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect.from(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).coversAtLeast(rectA)
        RegionSubject(rectB, timestamp = TimestampFactory.empty()).coversAtLeast(rectA)
        assertFail("SkRegion((0,0,2,1)(0,1,1,2))") {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).coversAtLeast(rectB)
        }
    }

    @Test
    fun detectCoversAtMost() {
        val rectA = Rect.from(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect.from(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).coversAtMost(rectA)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).coversAtMost(rectB)
        assertFail("SkRegion((0,0,2,1)(0,1,1,2))") {
            RegionSubject(rectB, timestamp = TimestampFactory.empty()).coversAtMost(rectA)
        }
    }

    @Test
    fun detectCoversExactly() {
        val rectA = Rect.from(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect.from(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).coversExactly(rectA)
        assertFail("SkRegion((0,0,2,1)(0,1,1,2))") {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).coversExactly(rectB)
        }
    }

    @Test
    fun detectOverlaps() {
        val rectA = Rect.from(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect.from(left = 0, top = 0, right = 2, bottom = 2)
        val rectC = Rect.from(left = 2, top = 2, right = 3, bottom = 3)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).overlaps(rectB)
        RegionSubject(rectB, timestamp = TimestampFactory.empty()).overlaps(rectA)
        assertFail("Overlap region: SkRegion()") {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).overlaps(rectC)
        }
    }

    @Test
    fun detectsNotOverlaps() {
        val rectA = Rect.from(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect.from(left = 2, top = 2, right = 3, bottom = 3)
        val rectC = Rect.from(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject(rectA, timestamp = TimestampFactory.empty()).notOverlaps(rectB)
        RegionSubject(rectB, timestamp = TimestampFactory.empty()).notOverlaps(rectA)
        assertFail("SkRegion((1,1,2,2))") {
            RegionSubject(rectA, timestamp = TimestampFactory.empty()).notOverlaps(rectC)
        }
    }

    companion object {
        @ClassRule @JvmField val initRule = InitRule()
    }
}
