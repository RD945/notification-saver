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
import com.notificationsaver.app.ui.components.SectionHeader
import com.notificationsaver.app.ui.theme.AppleLabel
import com.notificationsaver.app.ui.theme.AppleSecondaryLabel
import com.notificationsaver.app.ui.theme.appButtonColors
import com.notificationsaver.app.ui.theme.appOutlinedButtonColors
import com.notificationsaver.app.ui.theme.appTextFieldColors

@Composable
fun SetupScreen(vm: SetupViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            LargeTitle("Set up a destination")
            Text(
                "Save Telegram, an npoint bin, or both. The rest of the app unlocks after one destination is ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            SectionHeader("Telegram")
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
                onClick = vm::saveTelegram,
                enabled = state.token.isNotBlank() && state.chatId.isNotBlank(),
                colors = appButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Save Telegram and continue", color = AppleLabel)
            }
            OutlinedButton(
                onClick = vm::testTelegram,
                enabled = !state.busy && state.token.isNotBlank() && state.chatId.isNotBlank(),
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(if (state.busy) "Testing…" else "Test Telegram", color = AppleLabel)
            }

            SectionHeader("npoint")
            Text(
                "Create a bin on npoint.io while logged out, then paste the API URL. Encode and decode keys are generated on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.npointUrl,
                onValueChange = vm::onUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("API URL", color = AppleLabel) },
                placeholder = { Text("https://api.npoint.io/…", color = AppleSecondaryLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = appTextFieldColors(),
            )
            Button(
                onClick = vm::saveNpoint,
                enabled = state.npointUrl.isNotBlank(),
                colors = appButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("Save npoint and continue", color = AppleLabel)
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
