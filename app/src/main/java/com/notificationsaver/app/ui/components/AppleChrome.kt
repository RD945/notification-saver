package com.notificationsaver.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notificationsaver.app.ui.theme.AppleBlue
import com.notificationsaver.app.ui.theme.AppleGray
import com.notificationsaver.app.ui.theme.AppleGrouped
import com.notificationsaver.app.ui.theme.AppleLabel
import com.notificationsaver.app.ui.theme.AppleSecondaryLabel
import com.notificationsaver.app.ui.theme.AppleSeparator

data class AppNotice(
    val title: String,
    val message: String,
    val actionLabel: String? = null,
    val action: NoticeAction? = null,
)

enum class NoticeAction {
    OpenBackgroundSettings,
    OpenAppInfo,
    ConfirmReset,
    ConfirmResetKeys,
    ConfirmClearBin,
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = AppleSecondaryLabel,
        letterSpacing = 0.2.sp,
        modifier = Modifier.padding(start = 36.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppleGrouped),
        content = content,
    )
}

@Composable
fun GroupedRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 12.dp)
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppleSecondaryLabel,
                )
            }
        }
        trailing?.invoke()
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = AppleGray,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
            )
        }
    }
}

@Composable
fun AppleAlert(
    notice: AppNotice,
    onDismiss: () -> Unit,
    onAction: ((NoticeAction) -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 44.dp)
                .widthIn(max = 270.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppleGrouped),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = AppleLabel,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppleLabel,
                    textAlign = TextAlign.Center,
                )
            }
            HorizontalDivider(color = AppleSeparator, thickness = 0.5.dp)
            val action = notice.action
            val actionLabel = notice.actionLabel
            if (action != null && !actionLabel.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlertButton(
                        text = "OK",
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss,
                    )
                    Spacer(
                        modifier = Modifier
                            .width(0.5.dp)
                            .height(44.dp)
                            .background(AppleSeparator),
                    )
                    AlertButton(
                        text = actionLabel,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onAction?.invoke(action)
                            onDismiss()
                        },
                    )
                }
            } else {
                AlertButton(
                    text = "OK",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun AlertButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = AppleBlue,
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}

@Composable
fun GroupedDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = AppleSeparator,
        thickness = 0.5.dp,
    )
}
