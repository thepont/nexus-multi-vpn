package com.multiregionvpn.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.multiregionvpn.ui.shared.RouterViewModel
import com.multiregionvpn.ui.shared.RouterViewModelImpl

/**
 * TV Settings Screen - Configuration with D-pad navigation
 * 
 * Features:
 * - Provider credentials input
 * - Add/remove VPN servers
 * - Advanced options
 */
@Composable
fun TvSettingsScreen(
    viewModel: RouterViewModel = hiltViewModel<RouterViewModelImpl>()
) {
    val providerCredentials by viewModel.providerCredentials.collectAsState()
    
    var usernameInput by remember { mutableStateOf(providerCredentials?.username ?: "") }
    var passwordInput by remember { mutableStateOf(providerCredentials?.password ?: "") }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    
    // Update inputs when credentials change
    LaunchedEffect(providerCredentials) {
        usernameInput = providerCredentials?.username ?: ""
        passwordInput = providerCredentials?.password ?: ""
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Title
        Text(
            text = "Settings",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Provider Credentials Section
            item {
                Text(
                    text = "NordVPN Credentials",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Username Field
            item {
                TvTextField(
                    label = "Username",
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    placeholder = "Enter NordVPN username"
                )
            }
            
            // Password Field
            item {
                TvTextField(
                    label = "Password",
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    placeholder = "Enter NordVPN password",
                    isPassword = true
                )
            }
            
            // Save Button
            item {
                TvButton(
                    text = "Save Credentials",
                    onClick = {
                        viewModel.saveProviderCredentials(usernameInput, passwordInput)
                        showSaveConfirmation = true
                    },
                    enabled = usernameInput.isNotBlank() && passwordInput.isNotBlank()
                )
            }
            
            // Current Status
            item {
                if (providerCredentials != null) {
                    Text(
                        text = "✓ Credentials configured",
                        fontSize = 16.sp,
                        color = Color(0xFF4CAF50),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            // Instructions
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Server Management",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
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
    
    // Save Confirmation
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

/**
 * TV Text Field - Focusable input field for D-pad navigation
 */
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
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFF757575)) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .focusable()
                .onFocusChanged { isFocused = it.isFocused }
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 3.dp,
                            color = Color(0xFF2196F3),
                            shape = RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF2196F3),
                unfocusedBorderColor = Color(0xFF424242),
                focusedContainerColor = Color(0xFF1E2433),
                unfocusedContainerColor = Color(0xFF151B28)
            ),
            textStyle = LocalTextStyle.current.copy(
                fontSize = 20.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

/**
 * TV Button - Focusable button for D-pad navigation
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .focusable()
            .onFocusChanged { isFocused = it.isFocused },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFocused) Color(0xFF2196F3) else Color(0xFF424242),
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF2C2C2C),
            disabledContentColor = Color(0xFF757575)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isFocused) 8.dp else 2.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
