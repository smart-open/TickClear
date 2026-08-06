package com.tickclear.app.ui.tools

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.tickclear.app.R
import com.tickclear.app.ui.theme.Spacing
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ArrivalScreen(
    onBack: () -> Unit,
    viewModel: ArrivalViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val snackbarHostState = remember { SnackbarHostState() }
    var permissionRequested by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(permissionState.status) {
        if (permissionRequested && permissionState.status is PermissionStatus.Denied) {
            snackbarHostState.showSnackbar(context.getString(R.string.arrival_permission_required))
        }
    }
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { snackbarHostState.showSnackbar(it) }
    }

    fun requestAndEnable() {
        when (permissionState.status) {
            is PermissionStatus.Granted -> viewModel.setEnabled(true)
            is PermissionStatus.Denied -> {
                permissionRequested = true
                permissionState.launchPermissionRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.arrival_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(Spacing.md)) {
                    Text(
                        text = stringResource(R.string.arrival_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.arrival_monitor),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    enabled = stations.isNotEmpty(),
                    onCheckedChange = {
                        if (it) {
                            if (permissionState.status is PermissionStatus.Granted) {
                                viewModel.setEnabled(true)
                            } else {
                                permissionRequested = true
                                permissionState.launchPermissionRequest()
                            }
                        } else {
                            viewModel.setEnabled(false)
                        }
                    },
                )
            }

            Button(
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = stations.size < 20,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.arrival_add))
            }

            if (stations.isEmpty()) {
                Text(
                    text = stringResource(R.string.arrival_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height((stations.size * 76).dp.coerceAtMost(380.dp)),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(stations, key = { it.id }) { st ->
                        Card {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(st.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(
                                            R.string.arrival_station_meta,
                                            String.format(Locale.US, "%.5f", st.lat),
                                            String.format(Locale.US, "%.5f", st.lng),
                                            st.radius,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { viewModel.removeStation(st.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddStationDialog(
            currentLocation = currentLocation,
            onUseCurrent = { viewModel.fetchCurrentLocation() },
            onConfirm = { name, lat, lng, radius ->
                viewModel.addStation(name, lat, lng, radius)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddStationDialog(
    currentLocation: Pair<Double, Double>?,
    onUseCurrent: () -> Unit,
    onConfirm: (name: String, lat: Double, lng: Double, radius: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf(currentLocation?.first?.let { "%.5f".format(Locale.US, it) } ?: "") }
    var lngText by remember { mutableStateOf(currentLocation?.second?.let { "%.5f".format(Locale.US, it) } ?: "") }
    var radius by remember { mutableIntStateOf(300) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val lat = latText.toDoubleOrNull()
                val lng = lngText.toDoubleOrNull()
                if (name.isBlank()) { error = context.getString(R.string.arrival_name_required); return@TextButton }
                if (lat == null || lng == null) { error = context.getString(R.string.arrival_coord_invalid); return@TextButton }
                onConfirm(name.trim(), lat, lng, radius)
            }) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(stringResource(R.string.arrival_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.arrival_station_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text(stringResource(R.string.arrival_lat)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text(stringResource(R.string.arrival_lng)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(onClick = onUseCurrent, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null)
                    Text(stringResource(R.string.arrival_use_current))
                }
                Text(stringResource(R.string.arrival_radius, radius), style = MaterialTheme.typography.bodyMedium)
                androidx.compose.material3.Slider(
                    value = radius.toFloat(),
                    onValueChange = { radius = it.toInt() },
                    valueRange = 50f..2000f,
                    steps = 38,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.arrival_coord_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
    )
}
