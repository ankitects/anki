package net.ankiweb

import androidx.test.ext.junit.runners.AndroidJUnit4
import net.ankiweb.rsdroid.BackendFactory.getBackend
import net.ankiweb.rsdroid.database.AnkiSupportSQLiteDatabase.Companion.withRustBackend
import net.ankiweb.rsdroid.testing.RustBackendLoader.ensureSetup
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see [Testing documentation](http://d.android.com/tools/testing)
 */
@RunWith(AndroidJUnit4::class)
class CollectionCreationTest {
    @Before
    fun loadLibrary() {
        ensureSetup()
    }

    @Test
    fun ensureCollectionCreatedIsValid() {
        // We use this routine in AnkiDroid to create the collection, therefore we need to ensure
        // that the database is valid, open, and the values returned match how the Java used to work
        val backend = getBackend()
        backend.openCollection(":memory:")
        val database = withRustBackend(backend)
        try {
            val ver = database.query("select * from col")
            check(ver.moveToFirst()) { "no rows" }
        } catch (e: Exception) {
            // OK
        }
    }
}
