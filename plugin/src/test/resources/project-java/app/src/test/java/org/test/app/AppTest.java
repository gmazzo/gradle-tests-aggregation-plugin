package org.test.app;

import org.junit.Test;
import org.test.lib.Lib;

public class AppTest {

    @Test
    public void test() {
        new App();
        Lib.onlyCoveredByApp();
    }

}
