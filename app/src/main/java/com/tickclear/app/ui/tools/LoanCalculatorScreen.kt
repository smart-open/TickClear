package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.math.pow

private data class LoanResult(
    val monthlyFixed: Double?,
    val firstMonth: Double?,
    val decrease: Double?,
    val totalInterest: Double,
    val totalPay: Double,
)

private fun computeLoan(principal: Double, n: Int, r: Double, method: Int): LoanResult? {
    if (principal <= 0 || n <= 0) return null
    return if (method == 0) {
        val monthly = if (r == 0.0) {
            principal / n
        } else {
            val f = (1 + r).pow(n)
            principal * r * f / (f - 1)
        }
        val total = monthly * n
        LoanResult(monthly, null, null, total - principal, total)
    } else {
        val mp = principal / n
        val first = mp + principal * r
        val dec = mp * r
        val totalInterest = mp * r * n * (n + 1) / 2
        LoanResult(null, first, dec, totalInterest, principal + totalInterest)
    }
}

private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%,.2f", v)
private fun yuan(v: Double): String = "¥${fmt(v)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanCalculatorScreen(onBack: () -> Unit) {
    var amountStr by remember { mutableStateOf("1000000") }
    var rateStr by remember { mutableStateOf("4.9") }
    var yearsStr by remember { mutableStateOf("30") }
    var method by remember { mutableIntStateOf(0) } // 0 等额本息, 1 等额本金

    val amount = amountStr.toDoubleOrNull() ?: 0.0
    val annual = rateStr.toDoubleOrNull() ?: 0.0
    val years = yearsStr.toIntOrNull() ?: 0
    val n = years * 12
    val r = annual / 100.0 / 12.0
    val result = computeLoan(amount, n, r, method)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_loan_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text(stringResource(R.string.tools_loan_amount)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = rateStr,
                onValueChange = { rateStr = it },
                label = { Text(stringResource(R.string.tools_loan_rate)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = yearsStr,
                onValueChange = { yearsStr = it },
                label = { Text(stringResource(R.string.tools_loan_years)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.tools_loan_method), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = method == 0,
                    onClick = { method = 0 },
                    label = { Text(stringResource(R.string.tools_loan_equal_installment)) },
                )
                FilterChip(
                    selected = method == 1,
                    onClick = { method = 1 },
                    label = { Text(stringResource(R.string.tools_loan_equal_principal)) },
                )
            }

            if (result == null) {
                Text(
                    stringResource(R.string.tools_loan_invalid),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                if (method == 0) {
                    ResultCard(stringResource(R.string.tools_loan_monthly), yuan(result.monthlyFixed ?: 0.0))
                } else {
                    ResultCard(stringResource(R.string.tools_loan_first_month), yuan(result.firstMonth ?: 0.0))
                    ResultCard(stringResource(R.string.tools_loan_decrease), yuan(result.decrease ?: 0.0))
                }
                ResultCard(stringResource(R.string.tools_loan_total_interest), yuan(result.totalInterest))
                ResultCard(stringResource(R.string.tools_loan_total_pay), yuan(result.totalPay))
            }

            Button(
                onClick = {
                    amountStr = "1000000"
                    rateStr = "4.9"
                    yearsStr = "30"
                    method = 0
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.tools_loan_reset))
            }
        }
    }
}

@Composable
private fun ResultCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
