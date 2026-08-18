package com.notificationsaver.app.ui.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val AppleDarkColors = darkColorScheme(
    primary = AppleBlue,
    onPrimary = AppleLabel,
    secondary = AppleBlue,
    background = AppleBackground,
    onBackground = AppleLabel,
    surface = AppleBackground,
    onSurface = AppleLabel,
    surfaceVariant = AppleGrouped,
    onSurfaceVariant = AppleSecondaryLabel,
    outline = AppleSeparator,
    error = AppleRed,
    onError = AppleLabel,
    tertiary = AppleOrange,
)

private val AppleTypography = Typography(
    headlineLarge = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    ),
)

@Composable
fun appButtonColors() = ButtonDefaults.buttonColors(
    contentColor = AppleLabel,
    disabledContentColor = AppleLabel.copy(alpha = 0.78f),
)

@Composable
fun appOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    contentColor = AppleLabel,
    disabledContentColor = AppleLabel.copy(alpha = 0.78f),
)

@Composable
fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppleLabel,
    unfocusedTextColor = AppleLabel,
    disabledTextColor = AppleLabel.copy(alpha = 0.78f),
    focusedLabelColor = AppleLabel,
    unfocusedLabelColor = AppleSecondaryLabel,
    cursorColor = AppleBlue,
    focusedBorderColor = AppleBlue,
    unfocusedBorderColor = AppleSeparator,
)

@Composable
fun NotificationSaverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppleDarkColors,
        typography = AppleTypography,
        content = content,
    )
}
