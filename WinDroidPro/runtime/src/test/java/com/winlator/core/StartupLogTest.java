package com.winlator.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class StartupLogTest {
    @Test public void noisyOutputKeepsTheNewestFailureWithinTheLimit() {
        StartupLog log = new StartupLog(128);
        for (int i = 0; i < 1000; i++) log.append("Box64 output " + i);
        log.append("Wine exited with status 127");
        String text = log.snapshot();
        assertTrue(text.startsWith("[Earlier output omitted]"));
        assertTrue(text.endsWith("Wine exited with status 127\n"));
        assertTrue(text.length() <= 128 + "[Earlier output omitted]\n".length());
        assertFalse(text.contains("Box64 output 0\n"));
    }

    @Test public void aVeryLongNativeLineCannotGrowTheReportIndefinitely() {
        StartupLog log = new StartupLog(64);
        log.append(new String(new char[10000]).replace('\0', 'a') + "error");
        assertEquals(64, log.snapshot().length());
        assertTrue(log.snapshot().endsWith("error\n"));
        log.append("next line");
        assertTrue(log.snapshot().endsWith("next line\n"));
    }
}
