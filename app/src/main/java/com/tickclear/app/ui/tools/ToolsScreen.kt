package com.tickclear.app.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing

private data class ToolEntry(
    val route: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
)

private data class ToolCategory(
    val titleRes: Int,
    val entries: List<ToolEntry>,
)

private val TOOL_CATEGORIES = listOf(
    ToolCategory(
        titleRes = R.string.tools_cat_health,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_WATER,
                titleRes = R.string.tools_water_title,
                descRes = R.string.tools_water_desc,
                icon = Icons.Filled.LocalDrink,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_REST,
                titleRes = R.string.tools_rest_title,
                descRes = R.string.tools_rest_desc,
                icon = Icons.Filled.Weekend,
            ),
        ),
    ),
    ToolCategory(
        titleRes = R.string.tools_cat_security,
        entries = listOf(
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_VOICE,
                titleRes = R.string.tools_voice_title,
                descRes = R.string.tools_voice_desc,
                icon = Icons.Filled.Mic,
            ),
            ToolEntry(
                route = com.tickclear.app.ui.navigation.Routes.TOOLS_VAULT,
                titleRes = R.string.tools_vault_title,
                descRes = R.string.tools_vault_desc,
                icon = Icons.Filled.Lock,
            ),
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigate: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.tools_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            TOOL_CATEGORIES.forEach { category ->
                Text(
                    text = stringResource(category.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    userScrollEnabled = false,
                ) {
                    items(category.entries, key = { it.route }) { entry ->
                        ToolCard(entry = entry, onClick = { onNavigate(entry.route) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    entry: ToolEntry,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(entry.titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(entry.descRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
