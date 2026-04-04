package com.alexdremov.notate.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

/**
 * Compose panel for managing canvas layers.
 * Displays a list of layers with visibility, lock, and delete controls.
 * Integrates into the settings sidebar.
 */
@Composable
fun LayersPanel(
    layerManager: LayerManager,
    onLayerChanged: () -> Unit,
) {
    val layers by layerManager.layers.collectAsState()
    val activeLayerId by layerManager.activeLayerId.collectAsState()

    Column(
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        // Layer list
        LazyColumn(
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(layers.reversed()) { _, layer ->
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
                        layerManager.removeLayer(layer.id)
                        onLayerChanged()
                    },
                )
                HorizontalDivider(color = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Add layer button
        Button(
            onClick = {
                val count = layers.size + 1
                layerManager.addLayer("Layer $count")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = "Add layer",
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Layer")
        }

        Spacer(modifier = Modifier.height(8.dp))
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
    var isEditing by remember { mutableStateOf(false) }
    var editingName by remember(layer.name) { mutableStateOf(layer.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .background(
                if (isActive) Color(0xFFE8E8E8) else Color.Transparent,
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Visibility toggle
        IconButton(
            onClick = onToggleVisibility,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(
                    id = if (layer.isVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
                ),
                contentDescription = if (layer.isVisible) "Hide layer" else "Show layer",
                modifier = Modifier.size(20.dp),
                tint = if (layer.isVisible) Color.Black else Color.Gray,
            )
        }

        // Lock toggle
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(
                    id = if (layer.isLocked) R.drawable.ic_lock else R.drawable.ic_lock_open,
                ),
                contentDescription = if (layer.isLocked) "Unlock layer" else "Lock layer",
                modifier = Modifier.size(20.dp),
                tint = if (layer.isLocked) Color.Black else Color.Gray,
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Layer name (tap to edit)
        if (isEditing) {
            BasicTextField(
                value = editingName,
                onValueChange = { editingName = it },
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                ),
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
                        isEditing = false
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
                    .clickable { isEditing = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textDecoration = if (!layer.isVisible) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (layer.isVisible) Color.Black else Color.Gray,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Delete button
        if (canDelete && !isEditing) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = "Delete layer",
                    modifier = Modifier.size(18.dp),
                    tint = Color.Gray,
                )
            }
        }
    }
}
