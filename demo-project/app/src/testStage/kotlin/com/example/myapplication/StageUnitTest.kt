package com.example.myapplication

import androidx.test.core.app.launchActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class StageUnitTest {

    @Test
    fun shouldStart() {
        launchActivity<MainActivity>().onActivity {
            it.supportFragmentManager.beginTransaction()
                .add(SecondFragment(), "second")
                .commitNow()
        }
    }

}
