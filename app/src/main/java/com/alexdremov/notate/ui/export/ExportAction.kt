package com.alexdremov.notate.ui.export

sealed interface ExportAction {
    data class Export(
        val isVector: Boolean,
        val includeHiddenLayers: Boolean = false,
    ) : ExportAction

    data class Share(
        val isVector: Boolean,
        val includeHiddenLayers: Boolean = false,
    ) : ExportAction
}
