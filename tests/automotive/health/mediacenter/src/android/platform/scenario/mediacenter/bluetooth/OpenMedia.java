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
package android.platform.test.scenario.mediacenter.bluetooth;

import android.platform.test.scenario.annotation.Scenario;
import android.support.test.uiautomator.UiDevice;
import androidx.test.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

/** Opens Bluetooth application */
@Scenario
@RunWith(JUnit4.class)
public class OpenMedia {
    @Test
    public void testOpen() throws IOException {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .executeShellCommand(
                        "am start -a android.car.intent.action.MEDIA_TEMPLATE -e "
                                + "android.car.intent.extra.MEDIA_COMPONENT "
                                + "com.android.bluetooth"
                                + "/com.android.bluetooth.avrcpcontroller"
                                + ".BluetoothMediaBrowseService");
    }
}
