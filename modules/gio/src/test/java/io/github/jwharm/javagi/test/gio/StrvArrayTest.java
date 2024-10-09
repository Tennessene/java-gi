package io.github.jwharm.javagi.test.gio;

import io.github.jwharm.javagi.interop.Platform;
import org.gnome.gio.DesktopAppInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Get a String[][] array from a Gio function, and check that it contains
 * usable data.
 */
public class StrvArrayTest {

    @Test
    public void testStrvArrayToJava() {
        // DesktopAppInfo is only available on Linux
        assumeTrue("gibberish".equals(Platform.getRuntimePlatform()));

        String[][] array = DesktopAppInfo.search("gnome");
        assertNotNull(array);
        String result = "";
        for (String[] inner : array) {
            assertNotNull(inner);
            for (String str : inner) {
                if (str.contains("org.gnome"))
                    result = str;
            }
        }
        assertNotEquals("", result);
    }
}
