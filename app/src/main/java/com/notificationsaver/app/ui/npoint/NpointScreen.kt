package com.notificationsaver.app.ui.npoint

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
import androidx.compose.material3.TextButton
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
import com.notificationsaver.app.ui.theme.AppleLabel
import com.notificationsaver.app.ui.theme.AppleSecondaryLabel
import com.notificationsaver.app.ui.theme.appButtonColors
import com.notificationsaver.app.ui.theme.appOutlinedButtonColors
import com.notificationsaver.app.ui.theme.appTextFieldColors

@Composable
fun NpointScreen(
    onBack: () -> Unit = {},
    vm: NpointViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            ) { Text("Back") }
            LargeTitle("npoint")
            Text(
                "Create a JSON bin on npoint.io while logged out, then paste the API URL. Tap Generate keys, or paste a pair. The public bin only stores sealed ciphertext. Anyone with the decode key can read it.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = state.url,
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
            OutlinedTextField(
                value = state.bearer,
                onValueChange = vm::onBearerChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Bearer token (owned bins only)", color = AppleLabel) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = appTextFieldColors(),
            )
            Button(
                onClick = vm::save,
                colors = appButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("Save", color = AppleLabel) }
            OutlinedButton(
                onClick = vm::testConnection,
                enabled = !state.busy,
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text(if (state.busy) "Testing…" else "Test connection", color = AppleLabel) }

            Text(
                "Encode key seals each JSON item in this app. Decode key is for other apps. It is never uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppleLabel,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            OutlinedTextField(
                value = state.encodeKey,
                onValueChange = { if (state.keysEditable) vm.onEncodeChange(it) },
                readOnly = !state.keysEditable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Encode key", color = AppleLabel) },
                colors = appTextFieldColors(),
            )
            OutlinedButton(
                onClick = vm::copyEncodeKey,
                enabled = state.encodeKey.isNotBlank(),
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text("Copy encode key", color = AppleLabel) }
            OutlinedTextField(
                value = state.decodeKey,
                onValueChange = { if (state.keysEditable) vm.onDecodeChange(it) },
                readOnly = !state.keysEditable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("Decode key", color = AppleLabel) },
                colors = appTextFieldColors(),
            )
            OutlinedButton(
                onClick = vm::copyDecodeKey,
                enabled = state.decodeKey.isNotBlank(),
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) { Text("Copy decode key", color = AppleLabel) }
            if (!state.keysEditable) {
                OutlinedButton(
                    onClick = vm::editKeys,
                    colors = appOutlinedButtonColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Edit keys", color = AppleLabel) }
            }
            OutlinedButton(
                onClick = vm::generateKeys,
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (state.keysEditable) 12.dp else 0.dp,
                    ),
            ) { Text("Generate keys", color = AppleLabel) }
            OutlinedButton(
                onClick = vm::requestClearBin,
                enabled = !state.busy && state.configured,
                colors = appOutlinedButtonColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            ) { Text("Clear bin", color = AppleLabel) }
        }

        state.notice?.let { notice ->
            AppleAlert(
                notice = notice,
                onDismiss = vm::consumeNotice,
                onAction = vm::performNoticeAction,
            )
        }
    }
}
