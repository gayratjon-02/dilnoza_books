package dilnoza.books

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dilnoza.books.core.ui.theme.DilnozaTheme
import dilnoza.books.ui.AppRoot

// AppCompatActivity (not ComponentActivity) is required for per-app language switching below API 33.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DilnozaTheme {
                AppRoot()
            }
        }
    }
}
