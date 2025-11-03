package com.multiregionvpn.ui.settings.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.multiregionvpn.data.database.ProviderCredentials

@Composable
fun ProviderCredentialsSection(
    credentials: ProviderCredentials?,
    onSaveCredentials: (String, String) -> Unit // Changed to take two strings
) {
    // Use 'remember' with key to reset the text fields if the credentials in the state change
    var username by remember(credentials) { mutableStateOf(credentials?.username ?: "") }
    var password by remember(credentials) { mutableStateOf(credentials?.password ?: "") }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Provider Credentials", style = MaterialTheme.typography.titleLarge)
        Text(
            "Go to your NordVPN dashboard -> Advanced Settings -> 'Set up NordVPN manually' to find your Service Credentials.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("NordVPN Service Username") },
            placeholder = { Text("Paste your Service Username") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nord_username_textfield")
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("NordVPN Service Password") },
            placeholder = { Text("Paste your Service Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nord_password_textfield")
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = { onSaveCredentials(username.trim(), password.trim()) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("nord_credentials_save_button")
        ) {
            Text("Save NordVPN Credentials")
        }
    }
}
