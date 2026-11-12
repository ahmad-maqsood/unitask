package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RepositoriesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetDatabaseSingleton()
    }

    @After
    fun tearDown() {
        resetDatabaseSingleton()
    }

    /** Closes the shared database and forgets it, the same way the other database tests do. */
    private fun resetDatabaseSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
        (field.get(null) as? AppDatabase)?.close()
        field.set(null, null)
    }

    @Test
    fun groupRepository_isTheSameCopyEveryTime() {
        assertSame(Repositories.group(context), Repositories.group(context))
    }

    @Test
    fun appRepository_isTheSameCopyWhileTheDatabaseIsTheSame() {
        assertSame(Repositories.app(context), Repositories.app(context))
    }

    @Test
    fun appRepository_isRebuiltWhenTheDatabaseIsReplaced() {
        val before = Repositories.app(context)

        resetDatabaseSingleton()

        val after = Repositories.app(context)
        assertNotSame(before, after)
    }
}
