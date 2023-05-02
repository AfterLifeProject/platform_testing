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
package com.android.helpers.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import com.android.helpers.BugReportDurationHelper;
import com.android.helpers.BugReportDurationHelper.BugReportDurationLines;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Android Unit tests for {@link BugReportDurationHelper}.
 *
 * <p>atest CollectorsHelperTest:com.android.helpers.BugReportDurationHelperTest
 */
@RunWith(AndroidJUnit4.class)
public class BugReportDurationHelperTest {

    private static final String TAG = BugReportDurationHelperTest.class.getSimpleName();

    // Comparison of floating-point numbers with assertEquals requires a maximum delta.
    private static final double DELTA = 0.00001;

    private BugReportDurationHelper helper;
    private File testDir;

    @Before
    public void setUp() throws IOException {
        testDir = Files.createTempDirectory("test_dir").toFile();
        helper = new BugReportDurationHelper(testDir.getPath());
        helper.startCollecting();
    }

    @After
    public void tearDown() {
        // stopCollecting() is currently a no-op but is included here in case it is updated.
        helper.stopCollecting();

        // Deletes the files in the test directory, then the test directory.
        File[] files = testDir.listFiles();
        for (File f : files) {
            if (f.isFile()) {
                f.delete();
            }
        }
        testDir.delete();
    }

    // Creates a .zip archive with an identically-named .txt file containing the input lines.
    private File createArchive(String name, List<String> bugReportLines) throws IOException {
        File f = new File(testDir, name + ".zip");
        FileOutputStream fos = new FileOutputStream(f);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        ZipOutputStream zos = new ZipOutputStream(bos);
        try {
            zos.putNextEntry(new ZipEntry(name + ".txt"));
            for (String line : bugReportLines) {
                zos.write((line + "\n").getBytes());
            }
            zos.closeEntry();
        } finally {
            zos.close();
        }
        return f;
    }

    @Test
    public void testGetMetrics() throws IOException {
        List<String> bugReportLines =
                Arrays.asList(
                        "------ 44.619s was the duration of \'dumpstate_board()\' ------",
                        "------ 21.397s was the duration of \'DUMPSYS\' ------",
                        "------ 0.022s was the duration of \'DUMPSYS CRITICAL PROTO\' ------",
                        "--------- 0.051s was the duration of dumpsys SurfaceFlinger, ending at:"
                                + " 2023-04-27 23:50:35",
                        "--------- 24.741s was the duration of dumpsys meminfo, ending at:"
                                + " 2023-04-27 23:51:38",
                        "unrelated log line");

        createArchive("bugreport", bugReportLines);

        Map<String, Double> metrics = helper.getMetrics();
        assertEquals(5, metrics.size());
        assertEquals(44.619, metrics.get("bugreport-duration-dumpstate_board()"), DELTA);
        assertEquals(21.397, metrics.get("bugreport-duration-dumpsys"), DELTA);
        assertEquals(0.022, metrics.get("bugreport-duration-dumpsys-critical-proto"), DELTA);
        assertEquals(0.051, metrics.get("bugreport-dumpsys-duration-SurfaceFlinger"), DELTA);
        assertEquals(24.741, metrics.get("bugreport-dumpsys-duration-meminfo"), DELTA);
    }

    @Test
    public void testGetLatestBugReport() throws IOException {
        List<String> empty = new ArrayList<>();
        createArchive("bugreport-2022-04-23-03-12-33", empty);
        createArchive("bugreport-2022-04-20-21-44-11", empty);
        createArchive("bugreport-2021-12-28-10-32-10", empty);
        assertEquals("bugreport-2022-04-23-03-12-33.zip", helper.getLatestBugReport());
    }

    @Test
    public void testExtractAndFilterBugReport() throws IOException {
        // dumpstate section lines
        String dumpstateLine1 = "------ 44.619s was the duration of \'dumpstate_board()\' ------";
        String dumpstateLine2 = "------ 21.397s was the duration of \'DUMPSYS\' ------";

        // dumpsys section lines
        String dumpsysLine1 =
                "--------- 0.051s was the duration of dumpsys SurfaceFlinger, ending at: 2023-04-27"
                        + " 23:50:35";
        String dumpsysLine2 =
                "--------- 24.741s was the duration of dumpsys meminfo, ending at: 2023-04-27"
                        + " 23:51:38";

        // Lines that should be filtered out
        String invalidLine = "unrelated log line";
        String showmapLine =
                "------ 0.076s was the duration of \'SHOW MAP 22930 (com.android.chrome)\' ------";
        List<String> lines =
                Arrays.asList(
                        dumpstateLine1,
                        dumpstateLine2,
                        dumpsysLine1,
                        dumpsysLine2,
                        invalidLine,
                        showmapLine);

        File archive = createArchive("bugreport", lines);

        String zip = archive.getName();

        BugReportDurationLines filtered = helper.extractAndFilterBugReport(zip);
        assertTrue(filtered.contains(dumpstateLine1));
        assertTrue(filtered.contains(dumpstateLine2));
        assertTrue(filtered.contains(dumpsysLine1));
        assertTrue(filtered.contains(dumpsysLine2));
        assertFalse(filtered.contains(invalidLine));
        assertFalse(filtered.contains(showmapLine));
    }

    @Test
    public void testParseDecimalDurationForDumpstate() {
        String line1 = "------ 44.619s was the duration of \'dumpstate_board()\' ------";
        String line2 = "------ 21.397s was the duration of \'DUMPSYS\' ------";
        // This isn't a "real" case, since parseDecimalDuration() is only ever passed valid lines.
        String invalidLine = "unrelated log line";
        assertEquals(44.619, helper.parseDecimalDuration(line1), DELTA);
        assertEquals(21.397, helper.parseDecimalDuration(line2), DELTA);
        assertEquals(-1, helper.parseDecimalDuration(invalidLine), DELTA);
    }

    @Test
    public void testParseDecimalDurationForDumpsys() {
        String line1 =
                "--------- 0.051s was the duration of dumpsys SurfaceFlinger, ending at: 2023-04-27"
                        + " 23:50:35";
        String line2 =
                "--------- 24.741s was the duration of dumpsys meminfo, ending at: 2023-04-27"
                        + " 23:51:38";
        String line3 =
                "--------- 0.044s was the duration of dumpsys"
                    + " android.frameworks.stats.IStats/default, ending at: 2023-04-27 23:51:40";
        assertEquals(0.051, helper.parseDecimalDuration(line1), DELTA);
        assertEquals(24.741, helper.parseDecimalDuration(line2), DELTA);
        assertEquals(0.044, helper.parseDecimalDuration(line3), DELTA);
    }

    @Test
    public void testParseDumpstateSection() {
        String line1 = "------ 44.619s was the duration of \'dumpstate_board()\' ------";
        String line2 = "------ 21.397s was the duration of \'DUMPSYS\' ------";
        // This isn't a "real" case, since parseDumpstateSection() is only ever passed valid lines.
        String invalidLine = "unrelated log line";
        assertEquals("dumpstate_board()", helper.parseDumpstateSection(line1));
        assertEquals("DUMPSYS", helper.parseDumpstateSection(line2));
        assertNull(helper.parseDumpstateSection(invalidLine));
    }

    @Test
    public void testParseDumpsysSection() {
        String line1 =
                "--------- 0.051s was the duration of dumpsys SurfaceFlinger, ending at: 2023-04-27"
                        + " 23:50:35";
        String line2 =
                "--------- 24.741s was the duration of dumpsys meminfo, ending at: 2023-04-27"
                        + " 23:51:38";
        String line3 =
                "--------- 0.044s was the duration of dumpsys"
                    + " android.frameworks.stats.IStats/default, ending at: 2023-04-27 23:51:40";
        assertEquals("SurfaceFlinger", helper.parseDumpsysSection(line1));
        assertEquals("meminfo", helper.parseDumpsysSection(line2));
        assertEquals("android.frameworks.stats.IStats/default", helper.parseDumpsysSection(line3));
    }

    @Test
    public void testConvertDumpstateSectionToKey() {
        String dumpstate1 = "PROCRANK";
        String dumpstate2 = "PROCESSES AND THREADS";
        String dumpstate3 = "dumpstate_board()";
        assertEquals(
                "bugreport-duration-procrank", helper.convertDumpstateSectionToKey(dumpstate1));
        assertEquals(
                "bugreport-duration-processes-and-threads",
                helper.convertDumpstateSectionToKey(dumpstate2));
        assertEquals(
                "bugreport-duration-dumpstate_board()",
                helper.convertDumpstateSectionToKey(dumpstate3));
    }

    @Test
    public void testConvertDumpsysSectionToKey() {
        String dumpsys1 = "SurfaceFlinger";
        String dumpsys2 = "meminfo";
        String dumpsys3 = "android.frameworks.stats.IStats/default";
        assertEquals(
                "bugreport-dumpsys-duration-SurfaceFlinger",
                helper.convertDumpsysSectionToKey(dumpsys1));
        assertEquals(
                "bugreport-dumpsys-duration-meminfo", helper.convertDumpsysSectionToKey(dumpsys2));
        assertEquals(
                "bugreport-dumpsys-duration-android.frameworks.stats.IStats/default",
                helper.convertDumpsysSectionToKey(dumpsys3));
    }
}
