package com.tickclear.app.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LotteryScreen(
    vm: LotteryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val options by vm.options.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("pick") } // pick / dice / coin

    // 非组合上下文（onClick）内禁止调用 stringResource，故在此预取格式串与文案。
    val pickFmt = stringResource(R.string.lottery_pick_result)
    val diceFmt = stringResource(R.string.lottery_dice_result)
    val coinFmt = stringResource(R.string.lottery_coin_result)
    val heads = stringResource(R.string.lottery_coin_heads)
    val tails = stringResource(R.string.lottery_coin_tails)
    val emptyMsg = stringResource(R.string.lottery_empty)

    fun roll() {
        result = when (mode) {
            "dice" -> java.lang.String.format(diceFmt, Random.nextInt(1, 7))
            "coin" -> java.lang.String.format(coinFmt, if (Random.nextBoolean()) heads else tails)
            else -> if (options.isNotEmpty()) {
                java.lang.String.format(pickFmt, options[Random.nextInt(options.size)])
            } else {
                emptyMsg
            }
        }
    }

    val actionLabel = when (mode) {
        "dice" -> stringResource(R.string.lottery_action_dice)
        "coin" -> stringResource(R.string.lottery_action_coin)
        else -> stringResource(R.string.lottery_action_pick)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tools_lottery_title)) },
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
            Text(stringResource(R.string.lottery_options_title), style = MaterialTheme.typography.titleSmall)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.lottery_option_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        vm.addOption(input)
                        input = ""
                    },
                    enabled = input.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lottery_add))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                itemsIndexed(options) { index, option ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(option, style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { vm.removeOption(index) }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.lottery_delete))
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                FilterChip(
                    selected = mode == "pick",
                    onClick = { mode = "pick" },
                    label = { Text(stringResource(R.string.lottery_draw)) },
                )
                FilterChip(
                    selected = mode == "dice",
                    onClick = { mode = "dice" },
                    label = { Text(stringResource(R.string.lottery_dice)) },
                )
                FilterChip(
                    selected = mode == "coin",
                    onClick = { mode = "coin" },
                    label = { Text(stringResource(R.string.lottery_coin)) },
                )
            }

            Button(
                onClick = { roll() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }

            if (result != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.lottery_result),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            result ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            if (options.isNotEmpty()) {
                TextButton(
                    onClick = {
                        vm.clearOptions()
                        result = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.lottery_clear))
                }
            }
        }
    }
}
