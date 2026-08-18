package com.notificationsaver.app.ui.telegram

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notificationsaver.app.ui.components.AppleAlert
import com.notificationsaver.app.ui.components.GroupedList
import com.notificationsaver.app.ui.components.GroupedRow
import com.notificationsaver.app.ui.components.LargeTitle
import com.notificationsaver.app.ui.components.SectionHeader
import com.notificationsaver.app.ui.theme.AppleLabel
import com.notificationsaver.app.ui.theme.appButtonColors
import com.notificationsaver.app.ui.theme.appOutlinedButtonColors
import com.notificationsaver.app.ui.theme.appTextFieldColors

@Composable
fun TelegramScreen(vm: TelegramViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            LargeTitle("Telegram")
            Text(
                "Token and chat ID stay on this phone. Create a bot with @BotFather.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.token,
                onValueChange = vm::onTokenChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Bot token", color = AppleLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = appTextFieldColors(),
            )
            OutlinedTextField(
                value = state.chatId,
                onValueChange = vm::onChatChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Chat ID", color = AppleLabel) },
                singleLine = true,
                colors = appTextFieldColors(),
            )
            SectionHeader("Options")
            GroupedList {
                GroupedRow(
                    title = "Ignore Telegram",
                    subtitle = "Stops a loop if Telegram posts a notification",
                    trailing = {
                        Switch(
                            checked = state.ignoreTelegram,
                            onCheckedChange = vm::setIgnoreTelegram,
                        )
                    },
                )
            }
            Button(
                onClick = vm::save,
                colors = appButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text("Save", color = AppleLabel) }
            OutlinedButton(
                onClick = vm::testConnection,
                enabled = !state.busy,
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text(if (state.busy) "Testing…" else "Test connection", color = AppleLabel) }
        }

        state.notice?.let { notice ->
            AppleAlert(
                notice = notice,
                onDismiss = vm::consumeNotice,
            )
        }
    }
}
