package com.ddupg.runtrip.data.repository

import android.content.Context
import androidx.core.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import com.ddupg.runtrip.data.model.TrainingPreferences
import java.io.IOException
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TrainingPreferencesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `updated preferences survive repository recreation`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = LocalTrainingPreferencesRepository(context)
        val updated = TrainingPreferences(7.5, 12.0, 0.3, 3.5)
        repository.update(updated)
        assertEquals(updated, repository.preferences.value)
        assertEquals(updated, LocalTrainingPreferencesRepository(context).preferences.value)
    }

    @Test fun `failed write retains the previously committed file and observed value`() = runTest {
        val file = File(folder.root, "training-preferences.json")
        val repository = LocalTrainingPreferencesRepository(file)
        val before = TrainingPreferences(cyclingFactor = 0.3)
        repository.update(before)
        val committedBytes = file.readBytes()
        val blockedWrite = File(file.path + ".new")
        assertTrue(blockedWrite.mkdir())
        File(blockedWrite, "blocker").writeText("prevents opening the temporary file")
        try {
            repository.update(before.copy(cyclingFactor = 0.5))
            fail("Expected a disk write failure")
        } catch (_: IOException) {
            assertEquals(before, repository.preferences.value)
            assertArrayEquals(committedBytes, file.readBytes())
            assertEquals(before, LocalTrainingPreferencesRepository(file).preferences.value)
        }
    }

    @Test fun `interrupted replacement is ignored when reopening the last committed preferences`() = runTest {
        val file = File(folder.root, "training-preferences.json")
        val before = TrainingPreferences(swimmingFactor = 3.5)
        LocalTrainingPreferencesRepository(file).update(before)
        val interrupted = AtomicFile(file).startWrite()
        interrupted.write("{\"cycling_factor\":".toByteArray())
        interrupted.close()
        assertEquals(before, LocalTrainingPreferencesRepository(file).preferences.value)
    }
}
