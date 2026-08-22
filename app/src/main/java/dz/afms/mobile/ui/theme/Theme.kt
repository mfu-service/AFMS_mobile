package dz.afms.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AfmsOrange = Color(0xFFF47F0A)
val AfmsNavy = Color(0xFF0F2744)
val AfmsBg = Color(0xFFF4F6F8)
val AfmsMuted = Color(0xFF64748B)

private val Scheme = lightColorScheme(
    primary = AfmsOrange,
    onPrimary = Color.White,
    secondary = AfmsNavy,
    onSecondary = Color.White,
    background = AfmsBg,
    surface = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    error = Color(0xFFB91C1C),
)

@Composable
fun AfmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
