package app.unlocklearn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Surface,
    primaryContainer = OrangeSoft,
    onPrimaryContainer = OrangeDark,
    background = Surface,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    error = Error,
    secondary = InkSoft,
    onSecondary = Surface
)

private val DarkColors = darkColorScheme(
    primary = Orange,
    onPrimary = Ink,
    primaryContainer = OrangeDark,
    onPrimaryContainer = OrangeSoft,
    background = SurfaceDark,
    onBackground = Surface,
    surface = SurfaceDark,
    onSurface = Surface,
    error = Error,
    secondary = OrangeSoft,
    onSecondary = Ink
)

@Composable
fun UnlockLearnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
