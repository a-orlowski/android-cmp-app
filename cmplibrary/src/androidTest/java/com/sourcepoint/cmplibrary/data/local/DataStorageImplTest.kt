package com.sourcepoint.cmplibrary.data.local

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.uitestutil.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class DataStorageImplTest {

    private val appContext by lazy { InstrumentationRegistry.getInstrumentation().targetContext }
    private val gdprStorage by lazy { DataStorageGdpr.create(appContext) }
    private val ccpaStorage by lazy { DataStorageCcpa.create(appContext) }
    private val sut by lazy { DataStorage.create(appContext, gdprStorage, ccpaStorage) }

    @Before
    fun setup() {
        sut.clearAll()
    }

    @Test
    fun check_used_key_for_save_get_LocalState() {
        sut.run {
            saveLocalState("test_ls")
            getLocalState().assertEquals("test_ls")
        }
    }

    @Test
    fun check_used_key_for_save_get_PropertyPriorityData() {
        sut.run {
            savePropertyPriorityData("test_ppd")
            getPropertyPriorityData().assertEquals("test_ppd")
        }
    }
}
