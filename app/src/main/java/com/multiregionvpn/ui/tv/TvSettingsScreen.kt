@file:OptIn(ExperimentalMaterial3Api::class)

package com.multiregionvpn.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multiregionvpn.ui.settings.SettingsViewModel

@Composable
fun TvSettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var usernameInput by remember { mutableStateOf(uiState.nordCredentials?.username ?: "") }
    var passwordInput by remember { mutableStateOf(uiState.nordCredentials?.password ?: "") }
    var showSaveConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .padding(bottom = 24.dp)
                .semantics { contentDescription = "Settings" }
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "NordVPN Credentials",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "NordVPN Credentials" }
                )
            }

            item {
                TvTextField(
                    label = "Username",
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    placeholder = "Enter NordVPN username"
                )
            }

            item {
                TvTextField(
                    label = "Password",
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    placeholder = "Enter NordVPN password",
                    isPassword = true
                )
            }

            item {
                TvButton(
                    text = "Save Credentials",
                    onClick = {
                        viewModel.saveNordCredentials(usernameInput, passwordInput)
                        showSaveConfirmation = true
                    },
                    enabled = usernameInput.isNotBlank() && passwordInput.isNotBlank()
                )
            }

            item {
                if (uiState.nordCredentials != null) {
                    Text(
                        text = "✓ Credentials configured",
                        fontSize = 16.sp,
                        color = Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Server Management",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { contentDescription = "Server Management" }
                )

                Text(
                    text = "Use the Tunnels tab to view your VPN servers.\nTo add new servers, use the mobile interface or fetch automatically via credentials.",
                    fontSize = 16.sp,
                    color = Color(0xFF9E9E9E),
                    lineHeight = 24.sp
                )
            }
        }
    }

    if (showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { showSaveConfirmation = false },
            title = { Text("Credentials Saved") },
            text = { Text("Your NordVPN credentials have been saved successfully.") },
            confirmButton = {
                Button(onClick = { showSaveConfirmation = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun TvTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.semantics { contentDescription = label }
        )

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF121828), RoundedCornerShape(12.dp))
                .border(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused) Color(0xFF2196F3) else Color(0xFF2C3445),
                    shape = RoundedCornerShape(12.dp)
                )
                .focusable()
                .onFocusChanged { isFocused = it.isFocused },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                containerColor = Color.Transparent,
                cursorColor = Color.White,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color(0xFF60738C),
                unfocusedPlaceholderColor = Color(0xFF60738C)
            )
        )
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusable()
            .semantics { contentDescription = text }
    ) {
        Text(text = text, fontSize = 18.sp)
    }
}
