package dilnoza.books

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dilnoza.books.core.di.ApplicationScope
import dilnoza.books.data.files.OrphanImageCleaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DilnozaApp : Application() {

    @Inject
    lateinit var orphanImageCleaner: OrphanImageCleaner

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { runCatching { orphanImageCleaner.clean() } }
    }
}
