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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** 月度累进税率表（简易版，与个税预扣预缴月度表一致）。 */
private fun taxBracket(t: Double): Pair<Double, Double> = when {
    t <= 3000 -> 0.03 to 0.0
    t <= 12000 -> 0.10 to 210.0
    t <= 25000 -> 0.20 to 1410.0
    t <= 35000 -> 0.25 to 2660.0
    t <= 55000 -> 0.30 to 4410.0
    t <= 80000 -> 0.35 to 7160.0
    else -> 0.45 to 15160.0
}

/** 年终奖单独计税：以「奖金/12」定位月度税率，按奖金全额计税（不并入综合所得）。 */
private fun bonusTax(bonus: Double): Double {
    if (bonus <= 0) return 0.0
    val (rate, quick) = taxBracket(bonus / 12.0)
    return maxOf(0.0, bonus * rate - quick)
}

private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%,.2f", v)
private fun yuan(v: Double): String = "¥${fmt(v)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeTaxScreen(onBack: () -> Unit) {
    var salaryStr by remember { mutableStateOf("20000") }
    var insuranceStr by remember { mutableStateOf("3000") }
    var extraStr by remember { mutableStateOf("0") }
    var otherStr by remember { mutableStateOf("0") }
    var monthsAccumStr by remember { mutableStateOf("0") }
    var bonusStr by remember { mutableStateOf("0") }

    val salary = salaryStr.toDoubleOrNull() ?: 0.0
    val insurance = insuranceStr.toDoubleOrNull() ?: 0.0
    val extra = extraStr.toDoubleOrNull() ?: 0.0
    val other = otherStr.toDoubleOrNull() ?: 0.0
    val monthsAccum = monthsAccumStr.toIntOrNull()?.coerceIn(0, 11) ?: 0
    val bonus = bonusStr.toDoubleOrNull() ?: 0.0
    // 其他金额可正可负，直接计入应税收入（减项填负数、加项填正数）
    val taxable = maxOf(0.0, salary - 5000 - insurance - extra + other)
    val (rate, quick) = taxBracket(taxable)
    val tax = maxOf(0.0, taxable * rate - quick)
    val after = maxOf(0.0, salary - insurance - tax)
    val bonusTax = bonusTax(bonus)
    val bonusAfter = maxOf(0.0, bonus - bonusTax)
    val remainingMonths = maxOf(0, 12 - monthsAccum)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_tax_title)) },
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
            SimHintCard(stringResource(R.string.tools_tax_hint))
            OutlinedTextField(
                value = salaryStr,
                onValueChange = { salaryStr = it },
                label = { Text(stringResource(R.string.tools_tax_salary)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = insuranceStr,
                onValueChange = { insuranceStr = it },
                label = { Text(stringResource(R.string.tools_tax_insurance)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = extraStr,
                onValueChange = { extraStr = it },
                label = { Text(stringResource(R.string.tools_tax_extra)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = otherStr,
                onValueChange = { otherStr = it },
                label = { Text(stringResource(R.string.tools_tax_other)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = monthsAccumStr,
                onValueChange = { monthsAccumStr = it },
                label = { Text(stringResource(R.string.tools_tax_accum_months)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bonusStr,
                onValueChange = { bonusStr = it },
                label = { Text(stringResource(R.string.tools_tax_bonus)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            ResultCard(stringResource(R.string.tools_tax_taxable), yuan(taxable))
            ResultCard(
                stringResource(R.string.tools_tax_rate),
                "${(rate * 100).toInt()}%",
            )
            ResultCard(stringResource(R.string.tools_tax_insurance_label), yuan(insurance))
            ResultCard(stringResource(R.string.tools_tax_amount), yuan(tax))
            ResultCard(stringResource(R.string.tools_tax_after), yuan(after), highlight = true)

            ResultCard(stringResource(R.string.tools_tax_remaining_months), "$remainingMonths")
            if (bonus > 0) {
                ResultCard(stringResource(R.string.tools_tax_bonus_tax), yuan(bonusTax))
                ResultCard(stringResource(R.string.tools_tax_bonus_after), yuan(bonusAfter), highlight = true)
            }

            Text(
                stringResource(R.string.tools_tax_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    salaryStr = "20000"
                    insuranceStr = "3000"
                    extraStr = "0"
                    otherStr = "0"
                    monthsAccumStr = "0"
                    bonusStr = "0"
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.tools_tax_reset))
            }
        }
    }
}

@Composable
private fun ResultCard(label: String, value: String, highlight: Boolean = false) {
    val container = if (highlight) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onContainer = if (highlight) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = onContainer)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                value,
                style = if (highlight) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                color = onContainer,
            )
        }
    }
}
