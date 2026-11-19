package com.tda.mediafloatbutton;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DurationParserTest {

    @Test
    public void parsesMinutesSeconds() {
        assertEquals(5, MediaAccessibilityService.parseDurationSeconds("0:05"));
        assertEquals(754, MediaAccessibilityService.parseDurationSeconds("12:34"));
    }

    @Test
    public void parsesHours() {
        assertEquals(3723, MediaAccessibilityService.parseDurationSeconds("1:02:03"));
    }

    @Test
    public void parsesLabelledDuration() {
        assertEquals(5, MediaAccessibilityService.parseDurationSeconds("tổng thời gian: 0:05"));
        assertEquals(754, MediaAccessibilityService.parseDurationSeconds("Total time: 12:34"));
    }

    @Test
    public void ignoresInvalid() {
        assertEquals(0, MediaAccessibilityService.parseDurationSeconds("no time here"));
        assertEquals(0, MediaAccessibilityService.parseDurationSeconds(null));
    }
}
