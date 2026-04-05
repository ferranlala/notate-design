package com.alexdremov.notate.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alexdremov.notate.R
import com.alexdremov.notate.model.Layer
import com.alexdremov.notate.model.LayerManager
import kotlinx.coroutines.launch

/**
 * Compose dropdown panel for managing canvas layers.
 * Shown as a popup from a toolbar button.
 *
 * - Tap a layer row to select it as active.
 * - 3-dots button on each row opens a context menu (rename / delete).
 * - Visibility and lock toggles are inline.
 * - "Add Layer" button at the bottom.
 */
@Composable
fun LayersDropdownPanel(
    layerManager: LayerManager,
    onLayerChanged: () -> Unit,
    onDeleteLayerContents: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val layers by layerManager.layers.collectAsState()
    val activeLayerId by layerManager.activeLayerId.collectAsState()
    val scope = rememberCoroutineScope()
    var layerPendingDelete by remember { mutableStateOf<Layer?>(null) }

    // Confirmation dialog for layer deletion
    layerPendingDelete?.let { layer ->
        AlertDialog(
            onDismissRequest = { layerPendingDelete = null },
            title = { Text("Delete Layer") },
            text = { Text("Delete \"${layer.name}\" and all its contents? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = layer.id
                    layerPendingDelete = null
                    scope.launch {
                        onDeleteLayerContents(id)
                        layerManager.removeLayer(id)
                        onLayerChanged()
                    }
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { layerPendingDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .border(1.dp, Color.Black, RoundedCornerShape(12.dp)),
    ) {
        Column {
            // Layer list (reversed so topmost layer is first)
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .fillMaxWidth(),
            ) {
                itemsIndexed(layers.reversed()) { index, layer ->
                    LayerRow(
                        layer = layer,
                        isActive = layer.id == activeLayerId,
                        canDelete = layers.size > 1,
                        onSelect = {
                            layerManager.setActiveLayer(layer.id)
                        },
                        onToggleVisibility = {
                            layerManager.toggleVisibility(layer.id)
                            onLayerChanged()
                        },
                        onToggleLock = {
                            layerManager.toggleLock(layer.id)
                        },
                        onRename = { newName ->
                            layerManager.renameLayer(layer.id, newName)
                        },
                        onDelete = {
                            layerPendingDelete = layer
                        },
                    )
                    if (index < layers.size - 1) {
                        HorizontalDivider(color = Color(0xFFE0E0E0))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE0E0E0))

            // Add layer button
            TextButton(
                onClick = {
                    val count = layers.size + 1
                    layerManager.addLayer("Layer $count")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = "Add layer",
                    modifier = Modifier.size(18.dp),
                    tint = Color.Black,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Layer", color = Color.Black)
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: Layer,
    isActive: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleLock: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var isRenaming by remember { mutableStateOf(false) }
    var editingName by remember(layer.name) { mutableStateOf(layer.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .background(
                if (isActive) Color(0xFFE8E8E8) else Color.Transparent,
            )
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Visibility toggle
        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(
                    id = if (layer.isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                ),
                contentDescription = if (layer.isVisible) "Hide layer" else "Show layer",
                modifier = Modifier.size(18.dp),
                tint = if (layer.isVisible) Color.Black else Color.Gray,
            )
        }

        // Lock toggle
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                painter = painterResource(
                    id = if (layer.isLocked) R.drawable.ic_lock else R.drawable.ic_lock_open,
                ),
                contentDescription = if (layer.isLocked) "Unlock layer" else "Lock layer",
                modifier = Modifier.size(18.dp),
                tint = if (layer.isLocked) Color.Black else Color.Gray,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Layer name: inline rename or label
        if (isRenaming) {
            BasicTextField(
                value = editingName,
                onValueChange = { editingName = it },
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                textStyle = TextStyle(fontSize = 14.sp),
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Done",
                modifier = Modifier
                    .clickable {
                        if (editingName.isNotBlank()) {
                            onRename(editingName)
                        }
                        isRenaming = false
                    }
                    .padding(4.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        } else {
            Text(
                text = layer.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (!layer.isVisible) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (layer.isVisible) Color.Black else Color.Gray,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // 3-dots menu button
            Box {
                IconButton(
                    onClick = { showContextMenu = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_more_vert),
                        contentDescription = "Layer options",
                        modifier = Modifier.size(18.dp),
                        tint = Color.Gray,
                    )
                }

                // Context menu
                DropdownMenu(
                    expanded = showContextMenu,
                    onDismissRequest = { showContextMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showContextMenu = false
                            isRenaming = true
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = {
                                showContextMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.Red,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
