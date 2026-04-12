package com.alexdremov.notate.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages canvas layers with visibility and locking controls.
 * Thread-safe via synchronized blocks.
 *
 * Layers are ordered from bottom (index 0) to top (last index).
 */
class LayerManager {
    private val lock = Any()
    private var layerCounter = 0L

    private val _layers = MutableStateFlow(listOf(Layer.defaultLayer()))
    val layers: StateFlow<List<Layer>> = _layers.asStateFlow()

    private val _activeLayerId = MutableStateFlow(Layer.DEFAULT_LAYER_ID)
    val activeLayerId: StateFlow<String> = _activeLayerId.asStateFlow()

    /**
     * Returns the currently active layer.
     */
    fun getActiveLayer(): Layer =
        synchronized(lock) {
            _layers.value.find { it.id == _activeLayerId.value }
                ?: _layers.value.first()
        }

    /**
     * Returns the active layer ID.
     */
    fun getActiveLayerId(): String = _activeLayerId.value

    /**
     * Sets the active layer.
     * @return true if the layer was found and set, false otherwise.
     */
    fun setActiveLayer(layerId: String): Boolean =
        synchronized(lock) {
            if (_layers.value.any { it.id == layerId }) {
                _activeLayerId.value = layerId
                true
            } else {
                false
            }
        }

    /**
     * Adds a new layer at the top of the stack.
     * @return the new layer.
     */
    fun addLayer(name: String): Layer =
        synchronized(lock) {
            layerCounter++
            val id = "layer_${System.currentTimeMillis()}_$layerCounter"
            val layer = Layer(id = id, name = name)
            _layers.value = _layers.value + layer
            _activeLayerId.value = id
            layer
        }

    /**
     * Removes a layer by ID.
     * Cannot remove the last remaining layer.
     * If the active layer is removed, the next layer becomes active.
     * @return true if removed, false if it was the last layer or not found.
     */
    fun removeLayer(layerId: String): Boolean =
        synchronized(lock) {
            val currentLayers = _layers.value
            if (currentLayers.size <= 1) return false
            val index = currentLayers.indexOfFirst { it.id == layerId }
            if (index == -1) return false

            val newLayers = currentLayers.toMutableList()
            newLayers.removeAt(index)
            _layers.value = newLayers

            if (_activeLayerId.value == layerId) {
                val newActiveIndex = (index - 1).coerceAtLeast(0)
                _activeLayerId.value = newLayers[newActiveIndex].id
            }
            true
        }

    /**
     * Returns the index of a layer in the list, or -1 if not found.
     */
    fun getLayerIndex(layerId: String): Int =
        synchronized(lock) {
            _layers.value.indexOfFirst { it.id == layerId }
        }

    /**
     * Restores a previously removed layer at a specific index.
     * Used by undo to re-insert a deleted layer.
     */
    fun restoreLayer(layer: Layer, atIndex: Int) {
        synchronized(lock) {
            val newLayers = _layers.value.toMutableList()
            val safeIndex = atIndex.coerceIn(0, newLayers.size)
            newLayers.add(safeIndex, layer)
            _layers.value = newLayers
        }
    }

    /**
     * Toggles the visibility of a layer.
     * @return the updated layer, or null if not found.
     */
    fun toggleVisibility(layerId: String): Layer? =
        synchronized(lock) {
            updateLayer(layerId) { it.copy(isVisible = !it.isVisible) }
        }

    /**
     * Sets the visibility of a layer.
     * @return the updated layer, or null if not found.
     */
    fun setVisibility(layerId: String, isVisible: Boolean): Layer? =
        synchronized(lock) {
            updateLayer(layerId) { it.copy(isVisible = isVisible) }
        }

    /**
     * Toggles the locked state of a layer.
     * @return the updated layer, or null if not found.
     */
    fun toggleLock(layerId: String): Layer? =
        synchronized(lock) {
            updateLayer(layerId) { it.copy(isLocked = !it.isLocked) }
        }

    /**
     * Sets the locked state of a layer.
     * @return the updated layer, or null if not found.
     */
    fun setLocked(layerId: String, isLocked: Boolean): Layer? =
        synchronized(lock) {
            updateLayer(layerId) { it.copy(isLocked = isLocked) }
        }

    /**
     * Renames a layer.
     * @return the updated layer, or null if not found.
     */
    fun renameLayer(layerId: String, newName: String): Layer? =
        synchronized(lock) {
            updateLayer(layerId) { it.copy(name = newName) }
        }

    /**
     * Moves a layer from one position to another.
     * @return true if moved successfully.
     */
    fun moveLayer(fromIndex: Int, toIndex: Int): Boolean =
        synchronized(lock) {
            val currentLayers = _layers.value
            if (fromIndex !in currentLayers.indices || toIndex !in currentLayers.indices) return false
            if (fromIndex == toIndex) return true

            val newLayers = currentLayers.toMutableList()
            val layer = newLayers.removeAt(fromIndex)
            newLayers.add(toIndex, layer)
            _layers.value = newLayers
            true
        }

    /**
     * Returns the set of layer IDs that are currently hidden (not visible).
     */
    fun getHiddenLayerIds(): Set<String> =
        synchronized(lock) {
            _layers.value.filter { !it.isVisible }.map { it.id }.toSet()
        }

    /**
     * Returns the set of layer IDs that are currently locked.
     */
    fun getLockedLayerIds(): Set<String> =
        synchronized(lock) {
            _layers.value.filter { it.isLocked }.map { it.id }.toSet()
        }

    /**
     * Returns the set of layer IDs that should be excluded from selection
     * (hidden or locked).
     */
    fun getUnselectableLayerIds(): Set<String> =
        synchronized(lock) {
            _layers.value.filter { !it.isVisible || it.isLocked }.map { it.id }.toSet()
        }

    /**
     * Returns true if the given layer is visible and unlocked (selectable).
     */
    fun isLayerSelectable(layerId: String): Boolean =
        synchronized(lock) {
            val layer = _layers.value.find { it.id == layerId }
            layer != null && layer.isVisible && !layer.isLocked
        }

    /**
     * Returns true if the given layer is visible.
     */
    fun isLayerVisible(layerId: String): Boolean =
        synchronized(lock) {
            _layers.value.find { it.id == layerId }?.isVisible ?: true
        }

    /**
     * Returns a layer by ID, or null if not found.
     */
    fun getLayer(layerId: String): Layer? =
        synchronized(lock) {
            _layers.value.find { it.id == layerId }
        }

    /**
     * Returns all layers in order.
     */
    fun getLayers(): List<Layer> = _layers.value

    /**
     * Replaces all layers (used when loading from persistence).
     * If the list is empty, creates a default layer.
     */
    fun setLayers(layers: List<Layer>) {
        synchronized(lock) {
            val resolved = layers.ifEmpty { listOf(Layer.defaultLayer()) }
            _layers.value = resolved

            // Ensure active layer is valid
            if (resolved.none { it.id == _activeLayerId.value }) {
                _activeLayerId.value = resolved.first().id
            }
        }
    }

    private fun updateLayer(layerId: String, transform: (Layer) -> Layer): Layer? {
        val currentLayers = _layers.value
        val index = currentLayers.indexOfFirst { it.id == layerId }
        if (index == -1) return null

        val updated = transform(currentLayers[index])
        val newLayers = currentLayers.toMutableList()
        newLayers[index] = updated
        _layers.value = newLayers
        return updated
    }
}
