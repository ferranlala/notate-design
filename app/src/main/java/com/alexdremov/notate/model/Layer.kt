package com.alexdremov.notate.model

/**
 * Represents a layer on the canvas.
 * Each layer has visibility and locking controls.
 * Items are assigned to layers and rendered in layer order.
 */
data class Layer(
    val id: String,
    val name: String,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val opacity: Float = 1.0f,
) {
    companion object {
        const val DEFAULT_LAYER_ID = "default"

        fun defaultLayer(): Layer =
            Layer(
                id = DEFAULT_LAYER_ID,
                name = "Layer 1",
                isVisible = true,
                isLocked = false,
            )
    }
}
