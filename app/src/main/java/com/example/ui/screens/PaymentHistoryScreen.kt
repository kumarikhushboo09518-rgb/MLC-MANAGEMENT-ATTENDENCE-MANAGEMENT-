package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.logic.DateUtils
import com.example.data.model.PaymentRecord
import com.example.localization.LocalStrings
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MlcDatePickerDialog
import com.example.ui.theme.CardBorderGrey
import com.example.ui.theme.DeepNavy
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.EmeraldOnTint
import com.example.ui.theme.EmeraldTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class PaymentFilterMode { ALL, TODAY, MONTH, CUSTOM }

@Composable
fun PaymentHistoryScreen(
    paymentRecords: List<PaymentRecord>
) {
    val s = LocalStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(PaymentFilterMode.ALL) }
    var customDateKey by remember { mutableStateOf(DateUtils.todayKey()) }
    var showDatePicker by remember { mutableStateOf(false) }

    val todayKey = remember { DateUtils.todayKey() }
    val monthPrefix = remember { DateUtils.currentMonthPrefix() }

    val dailyTotal = remember(paymentRecords, todayKey) {
        paymentRecords.filter { it.paymentDateKey == todayKey }.sumOf { it.amountPaid }
    }
    val monthlyTotal = remember(paymentRecords, monthPrefix) {
        paymentRecords.filter { it.paymentDateKey.startsWith(monthPrefix) }.sumOf { it.amountPaid }
    }
    val grandTotal = remember(paymentRecords) {
        paymentRecords.sumOf { it.amountPaid }
    }

    val filtered = remember(paymentRecords, searchQuery, filterMode, customDateKey, todayKey, monthPrefix) {
        val q = searchQuery.trim()
        paymentRecords
            .filter { rec ->
                val matchesName = q.isEmpty() || rec.studentName.contains(q, ignoreCase = true)
                val matchesDate = when (filterMode) {
                    PaymentFilterMode.ALL -> true
                    PaymentFilterMode.TODAY -> rec.paymentDateKey == todayKey
                    PaymentFilterMode.MONTH -> rec.paymentDateKey.startsWith(monthPrefix)
                    PaymentFilterMode.CUSTOM -> rec.paymentDateKey == customDateKey
                }
                matchesName && matchesDate
            }
            .sortedByDescending { "${it.paymentDateKey}_${it.createdAt?.seconds ?: 0L}" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CollectionSummaryCard(s.dailyCollectionSummary, "₹$dailyTotal", EmeraldOnTint, Modifier.weight(1f))
                CollectionSummaryCard(s.monthlyCollectionSummary, "₹$monthlyTotal", DeepNavy, Modifier.weight(1f))
                CollectionSummaryCard(s.totalAmountCollected, "₹$grandTotal", EmeraldGreen, Modifier.weight(1f))
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(s.searchStudentHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_search_input")
            )
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = filterMode == PaymentFilterMode.ALL,
                        onClick = { filterMode = PaymentFilterMode.ALL },
                        label = { Text(s.filterAll) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == PaymentFilterMode.TODAY,
                        onClick = { filterMode = PaymentFilterMode.TODAY },
                        label = { Text(s.filterToday) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == PaymentFilterMode.MONTH,
                        onClick = { filterMode = PaymentFilterMode.MONTH },
                        label = { Text(s.filterThisMonth) }
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == PaymentFilterMode.CUSTOM,
                        onClick = { showDatePicker = true },
                        leadingIcon = { Icon(Icons.Rounded.CalendarToday, contentDescription = null) },
                        label = { Text(if (filterMode == PaymentFilterMode.CUSTOM) customDateKey else s.selectDateBtn) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                    title = s.paymentHistoryPageTitle,
                    description = s.noRecordsYet
                )
            }
        } else {
            items(filtered, key = { it.id }) { pay ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PureWhite),
                    border = BorderStroke(1.dp, CardBorderGrey)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = pay.studentName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${DateUtils.formatDisplayDate(pay.paymentDateKey, s.languageCode)} • ${pay.paymentTimeText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "${s.remainingAfterPayment}: ₹${pay.remainingAfter}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (pay.remainingAfter == 0L) EmeraldOnTint else TextSecondary
                            )
                            if (!pay.note.isNullOrBlank()) {
                                Text(
                                    text = pay.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Surface(
                            color = EmeraldTint,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "₹${pay.amountPaid}",
                                style = MaterialTheme.typography.titleLarge,
                                color = EmeraldOnTint,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        MlcDatePickerDialog(
            initialDateKey = customDateKey,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                customDateKey = it
                filterMode = PaymentFilterMode.CUSTOM
            }
        )
    }
}

@Composable
private fun CollectionSummaryCard(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite),
        border = BorderStroke(1.dp, CardBorderGrey)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge, color = valueColor, fontWeight = FontWeight.Bold)
        }
    }
}
