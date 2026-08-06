package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import java.util.Locale
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing

private data class ColStat(val sum: Double, val avg: Double, val max: Double, val min: Double)
private data class CalcResult(val rows: Int, val cols: Int, val columns: List<ColStat>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableCalcScreen(onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CalcResult?>(null) }

    val hint = stringResource(R.string.calc_input_hint)
    val computeLabel = stringResource(R.string.calc_compute)
    val emptyLabel = stringResource(R.string.calc_empty)
    val sumLabel = stringResource(R.string.calc_sum)
    val avgLabel = stringResource(R.string.calc_avg)
    val maxLabel = stringResource(R.string.calc_max)
    val minLabel = stringResource(R.string.calc_min)
    val colFmt = stringResource(R.string.calc_col)
    val summaryFmt = stringResource(R.string.calc_summary)

    fun compute() {
        val rows = input.lines()
            .map { line ->
                line.split(Regex("[\\s,;\\t]+"))
                    .mapNotNull { it.toDoubleOrNull() }
            }
            .filter { it.isNotEmpty() }
        if (rows.isEmpty()) {
            result = null
            return
        }
        val cols = rows.maxOfOrNull { it.size } ?: 0
        val columns = (0 until cols).map { c ->
            val vals = rows.mapNotNull { if (c < it.size) it[c] else null }
            val sum = vals.sum()
            ColStat(
                sum = sum,
                avg = if (vals.isNotEmpty()) sum / vals.size else 0.0,
                max = vals.maxOrNull() ?: 0.0,
                min = vals.minOrNull() ?: 0.0,
            )
        }
        result = CalcResult(rows = rows.size, cols = cols, columns = columns)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_calc_title)) },
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
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(hint) },
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
            Button(onClick = { compute() }, modifier = Modifier.fillMaxWidth()) {
                Text(computeLabel)
            }

            val r = result
            if (r == null) {
                Text(
                    emptyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    String.format(summaryFmt, r.rows, r.cols),
                    style = MaterialTheme.typography.titleSmall,
                )
                // 表头
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text("", modifier = Modifier.weight(1f))
                    r.columns.forEachIndexed { i, _ ->
                        Text(
                            String.format(colFmt, i + 1),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                listOf(
                    sumLabel to r.columns.map { String.format(Locale.ROOT, "%.2f", it.sum) },
                    avgLabel to r.columns.map { String.format(Locale.ROOT, "%.2f", it.avg) },
                    maxLabel to r.columns.map { String.format(Locale.ROOT, "%.2f", it.max) },
                    minLabel to r.columns.map { String.format(Locale.ROOT, "%.2f", it.min) },
                ).forEach { (label, values) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        values.forEach { v ->
                            Text(
                                v,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
