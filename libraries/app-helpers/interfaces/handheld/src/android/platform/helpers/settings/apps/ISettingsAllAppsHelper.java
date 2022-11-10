/**
 * Copyright (C) 2022 The Android Open Source Project
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.platform.helpers.settings.apps;

/** Extends for Settings > Apps > All apps */
public interface ISettingsAllAppsHelper extends ISettingsAppsHelper {

    /**
     * Setup expectations: Settings All apps page is open
     *
     * <p>This method validates Settings All apps page.
     */
    void isAllAppsPage();
}
