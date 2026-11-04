package com.waste.link.ui.admin.panes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
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
import com.waste.link.data.models.Dispute
import com.waste.link.data.models.WasteRequest
import com.waste.link.ui.admin.AdminDisputesViewModel
import com.waste.link.ui.admin.DisputeNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDisputesPane() {
    val viewModel: AdminDisputesViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("Dispute Case Management", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.disputes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No disputes found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.disputes) { dispute ->
                    DisputeRow(dispute = dispute, onClick = { viewModel.selectDispute(dispute) })
                }
            }
        }
    }

    if (uiState.selectedDispute != null) {
        DisputeDetailDrawer(
            dispute = uiState.selectedDispute!!,
            request = uiState.selectedRequest,
            resident = uiState.resident,
            collector = uiState.collector,
            notes = uiState.notes,
            isLoading = uiState.isDrawerLoading,
            onDismiss = { viewModel.selectDispute(null) },
            onAddNote = { text -> viewModel.addNote(uiState.selectedDispute!!.id, text) },
            onResolve = { outcome, note -> viewModel.resolveDispute(uiState.selectedDispute!!.id, outcome, note) }
        )
    }
}

@Composable
fun DisputeRow(dispute: Dispute, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(dispute.reason.ifEmpty { "No Reason Provided" }, fontWeight = FontWeight.SemiBold)
                Text("Status: ${dispute.status}", color = if (dispute.status == "RESOLVED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisputeDetailDrawer(
    dispute: Dispute,
    request: WasteRequest?,
    resident: AppUser?,
    collector: AppUser?,
    notes: List<DisputeNote>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onAddNote: (String) -> Unit,
    onResolve: (String, String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@ModalBottomSheet
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Dispute Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Info Section
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Resident: ${resident?.name ?: "Unknown"}", fontWeight = FontWeight.SemiBold)
                        Text("Collector: ${collector?.name ?: "None"}", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Complaint: ${dispute.reason}")
                        Text("Request Address: ${request?.pickupAddress ?: "Unknown"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Internal Notes Thread
            item {
                Text("Internal Admin Notes", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text("Not visible to users", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(notes) { note ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                    Box(
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)).padding(12.dp)
                    ) {
                        Text(note.text, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 14.sp)
                    }
                }
            }

            // Add Note Input
            item {
                var newNote by remember { mutableStateOf("") }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newNote,
                        onValueChange = { newNote = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type an internal note...") },
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newNote.isNotBlank()) {
                                onAddNote(newNote)
                                newNote = ""
                            }
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                    ) {
                        Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Resolution Action
            if (dispute.status != "RESOLVED") {
                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Resolve Dispute", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    var selectedOutcome by remember { mutableStateOf("Warning") }
                    var resolutionNote by remember { mutableStateOf("") }
                    val outcomes = listOf("Refund", "Warning", "Dismissed")

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        outcomes.forEach { outcome ->
                            FilterChip(
                                selected = selectedOutcome == outcome,
                                onClick = { selectedOutcome = outcome },
                                label = { Text(outcome) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resolutionNote,
                        onValueChange = { resolutionNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Resolution explanation (optional)...") }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onResolve(selectedOutcome, resolutionNote) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mark as Resolved")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            } else {
                item {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Resolved Outcome: ${dispute.resolutionOutcome}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
