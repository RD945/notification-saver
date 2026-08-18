package com.notificationsaver.app.ui.setup

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
import com.notificationsaver.app.ui.components.LargeTitle
import com.notificationsaver.app.ui.telegram.TelegramViewModel
import com.notificationsaver.app.ui.theme.AppleLabel
import com.notificationsaver.app.ui.theme.appButtonColors
import com.notificationsaver.app.ui.theme.appOutlinedButtonColors
import com.notificationsaver.app.ui.theme.appTextFieldColors

@Composable
fun SetupScreen(vm: TelegramViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            LargeTitle("Set up Telegram")
            Text(
                "Create a bot with @BotFather, then get your chat ID from @userinfobot. Both stay on this phone. The rest of the app unlocks after you save them.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
            Button(
                onClick = vm::save,
                enabled = state.token.isNotBlank() && state.chatId.isNotBlank(),
                colors = appButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Save and continue", color = AppleLabel)
            }
            OutlinedButton(
                onClick = vm::testConnection,
                enabled = !state.busy && state.token.isNotBlank() && state.chatId.isNotBlank(),
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(if (state.busy) "Testing…" else "Test connection", color = AppleLabel)
            }
        }

        state.notice?.let { notice ->
            AppleAlert(
                notice = notice,
                onDismiss = vm::consumeNotice,
            )
        }
    }
}
