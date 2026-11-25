package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.StudentSummary
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.AmberOnTint
import com.example.ui.theme.AmberTint
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CardBorderGrey
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldOnTint
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun PendingFinesScreen(
    pendingStudents: List<StudentSummary>,
    isBusy: Boolean,
    onCollectPayment: suspend (summary: StudentSummary, amount: Long, note: String?) -> Result<Unit>,
    onShowMessage: (String) -> Unit
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var sortByHighest by remember { mutableStateOf(true) }
    var collectingFor by remember { mutableStateOf<StudentSummary?>(null) }

    val filtered = remember(pendingStudents, searchQuery, sortByHighest) {
        val q = searchQuery.trim()
        val list = if (q.isEmpty()) pendingStudents else pendingStudents.filter {
            it.student.fullName.contains(q, ignoreCase = true) ||
                it.student.studentCode.contains(q, ignoreCase = true)
        }
        if (sortByHighest) list.sortedByDescending { it.remainingFine }
        else list.sortedBy { it.student.fullName.lowercase() }
    }

    val totalPending = remember(pendingStudents) { pendingStudents.sumOf { it.remainingFine } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DeepNavy)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = s.totalPendingFines,
                            style = MaterialTheme.typography.labelLarge,
                            color = PureWhite.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "₹$totalPending",
                            style = MaterialTheme.typography.displaySmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = AmberWarning.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = AmberTint, modifier = Modifier.size(18.dp))
                            Text(
                                text = "${pendingStudents.size}",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(s.searchStudentHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pending_fines_search_input")
                )
                Surface(
                    onClick = { sortByHighest = !sortByHighest },
                    shape = RoundedCornerShape(14.dp),
                    color = PureWhite,
                    border = BorderStroke(1.dp, CardBorderGrey),
                    modifier = Modifier.height(54.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (sortByHighest) s.sortHighestFine else s.sortAZ,
                            style = MaterialTheme.typography.labelMedium,
                            color = DeepNavy
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.CheckCircle,
                    title = s.noPendingFinesTitle,
                    description = s.noPendingFinesDesc
                )
            }
        } else {
            items(filtered, key = { it.student.id }) { summary ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pending_fine_card_${summary.student.id}"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, CardBorderGrey)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = summary.student.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${summary.absentDays} ${s.absentDaysLabel} • ${summary.student.studentCode}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Surface(color = AmberTint, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    text = "₹${summary.remainingFine} ${s.pendingAmountLabel}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AmberOnTint,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${s.totalFineLabel}: ₹${summary.totalFineGenerated}  |  ${s.paidLabel}: ₹${summary.totalFinePaid}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Button(
                            onClick = { collectingFor = summary },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("collect_fine_button_${summary.student.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen, contentColor = PureWhite)
                        ) {
                            Icon(Icons.Rounded.CurrencyRupee, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(s.collectFineBtn, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }

    if (collectingFor != null) {
        val target = collectingFor!!
        CollectPaymentDialog(
            summary = target,
            isBusy = isBusy,
            onDismiss = { collectingFor = null },
            onConfirm = { amount, note ->
                scope.launch {
                    val res = onCollectPayment(target, amount, note)
                    res.onSuccess {
                        collectingFor = null
                        onShowMessage(String.format(s.paymentSuccessMsg, amount))
                    }.onFailure {
                        onShowMessage(it.localizedMessage ?: "Error")
                    }
                }
            }
        )
    }
}

@Composable
private fun CollectPaymentDialog(
    summary: StudentSummary,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, note: String?) -> Unit
) {
    val s = LocalStrings.current
    var amountInput by remember(summary) { mutableStateOf(summary.remainingFine.toString()) }
    var noteInput by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.collectPaymentTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = summary.student.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = EmeraldTint.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${s.totalFineLabel}: ₹${summary.totalFineGenerated}", style = MaterialTheme.typography.bodySmall)
                        Text("${s.alreadyPaidLabel}: ₹${summary.totalFinePaid}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${s.remainingBalanceLabel}: ₹${summary.remainingFine}",
                            style = MaterialTheme.typography.titleSmall,
                            color = EmeraldOnTint,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text(s.amountReceivedLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_amount_input")
                )

                OutlinedButton(
                    onClick = { amountInput = summary.remainingFine.toString() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${s.payFullAmountBtn} (₹${summary.remainingFine})")
                }

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text(s.paymentNoteOptional) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (err != null) {
                    Text(err!!, color = CrimsonRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.toLongOrNull() ?: 0L
                    if (amount < 1L || amount > summary.remainingFine) {
                        err = String.format(s.invalidPaymentAmountErr, summary.remainingFine)
                    } else {
                        err = null
                        onConfirm(amount, noteInput.takeIf { it.isNotBlank() })
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                modifier = Modifier.testTag("confirm_payment_button")
            ) { Text(s.collectPaymentBtn) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancelBtn) }
        }
    )
}
