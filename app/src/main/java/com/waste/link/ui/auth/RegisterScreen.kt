package com.waste.link.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waste.link.ui.theme.*

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterClick: (String, String, String, String) -> Unit,
    authState: AuthState
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(AppBgFrom, AppBgMid, AppBgTo)
    )

    // Compute Password Strength
    val passwordStrength = remember(password) {
        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        score
    }
    val strengthColor = when (passwordStrength) {
        0, 1 -> Color.Red
        2 -> Color(0xFFF59E0B) // Amber
        3 -> Color(0xFFFBBF24) // Yellow
        4 -> Color.Green
        else -> Color.Gray
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = bgBrush)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Create Account",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp, top = 24.dp)
            )
            Text(
                text = "Join WasteLink today",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name", color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color.White) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address", color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.White) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number", color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password", color = Color.White.copy(alpha = 0.7f)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White) },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            // Password Strength Meter
            if (password.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in 1..4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    if (i <= passwordStrength) strengthColor else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp).padding(bottom = 24.dp))
            }

            if (authState is AuthState.Error) {
                Text(
                    text = authState.message,
                    color = Color.Red,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = { onRegisterClick(email, password, name, phone) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                enabled = authState !is AuthState.Loading
            ) {
                if (authState is AuthState.Loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Sign Up", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                Text("Already have an account? ", color = Color.White.copy(alpha = 0.8f))
                Text(
                    "Login",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}
