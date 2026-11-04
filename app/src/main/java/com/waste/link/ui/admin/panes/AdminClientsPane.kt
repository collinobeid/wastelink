package com.waste.link.ui.admin.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.waste.link.data.models.AppUser
import com.waste.link.data.models.WasteRequest
import com.waste.link.ui.admin.AdminModerationViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminClientsPane() {
    val viewModel: AdminModerationViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                placeholder = { Text("Search by name, email, or phone...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                singleLine = true
            )

            // Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = uiState.selectedClientIds.size == uiState.filteredClients.size && uiState.filteredClients.isNotEmpty(),
                    onCheckedChange = { 
                        if (it) viewModel.selectAllClients() else viewModel.clearSelection() 
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Name & Email", modifier = Modifier.weight(2f), fontWeight = FontWeight.SemiBold)
                Text("Status", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("Join Date", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clients List
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredClients.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No clients found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.filteredClients, key = { it.uid }) { client ->
                        ClientRow(
                            client = client,
                            isSelected = uiState.selectedClientIds.contains(client.uid),
                            onSelectToggle = { viewModel.toggleClientSelection(client.uid) },
                            onClick = { viewModel.selectClientForDrawer(client) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Bulk Actions Bar
        if (uiState.selectedClientIds.isNotEmpty()) {
            BulkActionBar(
                selectedCount = uiState.selectedClientIds.size,
                onClear = { viewModel.clearSelection() },
                onBulkBan = { reason -> viewModel.bulkBanSelected(reason) {} },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Detail Drawer / Bottom Sheet
    if (uiState.selectedClient != null) {
        ClientDetailDrawer(
            client = uiState.selectedClient!!,
            requests = uiState.selectedClientRequests,
            isLoading = uiState.isDrawerLoading,
            onDismiss = { viewModel.selectClientForDrawer(null) },
            onBanClient = { reason -> viewModel.banClient(uiState.selectedClient!!.uid, reason) {} }
        )
    }
}

@Composable
fun ClientRow(
    client: AppUser,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit
) {
    val joinDate = "Unknown"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onSelectToggle() })
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(2f)) {
            Text(client.name.ifEmpty { "Unknown" }, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(client.email.ifEmpty { client.phone }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        
        Text(
            text = client.status, 
            modifier = Modifier.weight(1f), 
            fontSize = 12.sp,
            color = if (client.status == "BANNED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        
        Text(joinDate, modifier = Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BulkActionBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onBulkBan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBanDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.padding(16.dp).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$selectedCount selected", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Button(
                    onClick = { showBanDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Block, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ban")
                }
            }
        }
    }

    if (showBanDialog) {
        BanReasonDialog(
            title = "Ban $selectedCount Clients?",
            onDismiss = { showBanDialog = false },
            onConfirm = { reason -> 
                onBulkBan(reason)
                showBanDialog = false 
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailDrawer(
    client: AppUser,
    requests: List<WasteRequest>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onBanClient: (String) -> Unit
) {
    var showBanDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Client Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Name: ${client.name.ifEmpty { "N/A" }}", fontWeight = FontWeight.Medium)
            Text("Phone: ${client.phone}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Status: ${client.status}", color = if (client.status == "BANNED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("Request Timeline", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (requests.isEmpty()) {
                    Text("No past requests.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn {
                        items(requests) { req ->
                            Text("- ${req.category} (${req.status})", fontSize = 14.sp)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (client.status != "BANNED") {
                Button(
                    onClick = { showBanDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ban / Suspend Client")
                }
            } else {
                Text("Client is currently BANNED.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showBanDialog) {
        BanReasonDialog(
            title = "Ban ${client.name}?",
            onDismiss = { showBanDialog = false },
            onConfirm = { reason -> 
                onBanClient(reason)
                showBanDialog = false 
            }
        )
    }
}

@Composable
fun BanReasonDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("This action is destructive and will disable their account. Please provide a mandatory reason for the audit log.")
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("Reason for ban...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = reason.isNotBlank()
            ) {
                Text("Confirm Ban")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
