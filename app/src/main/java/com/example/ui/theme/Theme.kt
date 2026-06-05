package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.ui.AppTheme

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    secondary = BrandEmerald,
    tertiary = BrandAmber,
    background = DarkMidnightBackground,
    surface = DarkMidnightSurface,
    surfaceVariant = DarkMidnightSurfaceVariant,
    onPrimary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    secondary = BrandEmerald,
    tertiary = BrandAmber,
    background = LightIceBackground,
    surface = LightIceSurface,
    surfaceVariant = LightIceSurfaceVariant,
    onPrimary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    appTheme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = darkTheme || appTheme == AppTheme.BLACK

    val customScheme = when (appTheme) {
        AppTheme.BLACK -> darkColorScheme(
            background = Color.Black,
            surface = Color(0xFF121212),
            surfaceVariant = Color(0xFF1E1E1E),
            primary = Color(0xFF1E88E5)
        )
        AppTheme.BLUE -> if (isDark) darkColorScheme(primary = Color(0xFF1976D2), background = Color(0xFF0D47A1), surfaceVariant = Color(0xFF1565C0)) else lightColorScheme(primary = Color(0xFF1E88E5), background = Color(0xFF2196F3), surfaceVariant = Color(0xFF64B5F6), onBackground = Color.White, onSurfaceVariant = Color.White)
        AppTheme.RED -> if (isDark) darkColorScheme(primary = Color(0xFFD32F2F), background = Color(0xFFB71C1C), surfaceVariant = Color(0xFFC62828)) else lightColorScheme(primary = Color(0xFFE53935), background = Color(0xFFF44336), surfaceVariant = Color(0xFFE57373), onBackground = Color.White, onSurfaceVariant = Color.White)
        AppTheme.PINK -> if (isDark) darkColorScheme(primary = Color(0xFFC2185B), background = Color(0xFF880E4F), surfaceVariant = Color(0xFFAD1457)) else lightColorScheme(primary = Color(0xFFD81B60), background = Color(0xFFE91E63), surfaceVariant = Color(0xFFF06292), onBackground = Color.White, onSurfaceVariant = Color.White)
        AppTheme.MAROON -> if (isDark) darkColorScheme(primary = Color(0xFFB71C1C), background = Color(0xFF4A148C), surfaceVariant = Color(0xFF311B92)) else lightColorScheme(primary = Color(0xFF800000), background = Color(0xFFA52A2A), surfaceVariant = Color(0xFFF4C2C2), onBackground = Color.White, onSurfaceVariant = Color.White)
        AppTheme.DARK_BLUE -> if (isDark) darkColorScheme(primary = Color(0xFF0D47A1), background = Color(0xFF001064), surfaceVariant = Color(0xFF002171)) else lightColorScheme(primary = Color(0xFF1A237E), background = Color(0xFF303F9F), surfaceVariant = Color(0xFF5C6BC0), onBackground = Color.White, onSurfaceVariant = Color.White)
        else -> null
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && customScheme == null -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        customScheme != null -> customScheme
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
