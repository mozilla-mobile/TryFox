package org.mozilla.tryfox.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
     */
)

@Immutable
data class CustomColors(
    val tryFoxCardBackground: Color,
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomColors(
        tryFoxCardBackground = Color.Unspecified,
    )
}

private val DarkCustomColors = CustomColors(
    tryFoxCardBackground = Color(0xFFA89300),
)

private val LightCustomColors = CustomColors(
    tryFoxCardBackground = Color(0xFFFFEB3B),
)

@Composable
fun TryFoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val customColors = if (darkTheme) DarkCustomColors else LightCustomColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalCustomColors provides customColors,
            content = content,
        )
    }
}

val MaterialTheme.customColors: CustomColors
    @Composable
    get() = LocalCustomColors.current
