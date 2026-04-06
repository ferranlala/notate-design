package com.alexdremov.notate.ui.dialog

import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.alexdremov.notate.R
import com.alexdremov.notate.databinding.DialogSelectionActionsBinding
import com.alexdremov.notate.model.Layer
import com.alexdremov.notate.util.EpdFastModeController
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.api.device.epd.UpdateMode

/**
 * Selection Actions Popup (Copy/Delete/Move).
 * Highly optimized for zero-lag synchronous updates.
 */
class SelectionActionPopup(
    private val context: Context,
    private val container: FrameLayout,
    private val onCopy: () -> Unit,
    private val onDelete: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onMoveToLayer: ((String) -> Unit)? = null,
    private val layerProvider: (() -> List<Layer>)? = null,
) {
    private val binding = DialogSelectionActionsBinding.inflate(LayoutInflater.from(context), container, false)
    private var isVisible = false
    private var layerPickerPopup: PopupWindow? = null

    // Cache dimensions to avoid expensive measure()
    private var cachedWidth = 0
    private var cachedHeight = 0
    private var isSizeValid = false

    // Reuse arrays to avoid allocations during move
    private val ptsBuffer = FloatArray(4) // [topCenterX, topCenterY, bottomCenterX, bottomCenterY]

    init {
        binding.root.visibility = View.GONE
        container.addView(binding.root)

        binding.btnCopy.setOnClickListener {
            onCopy()
            dismiss()
        }

        binding.btnDelete.setOnClickListener {
            onDelete()
            dismiss()
        }

        if (onMoveToLayer != null && layerProvider != null) {
            binding.dividerMove.visibility = View.VISIBLE
            binding.btnMoveToLayer.visibility = View.VISIBLE
            binding.btnMoveToLayer.setOnClickListener { anchor ->
                showLayerPicker(anchor)
            }
        }
    }

    private fun showLayerPicker(anchor: View) {
        val layers = layerProvider?.invoke() ?: return
        if (layers.size < 2) return

        val inflater = LayoutInflater.from(context)
        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_popup_outlined)
        }

        for (layer in layers) {
            val itemView = inflater.inflate(R.layout.item_layer_picker, listLayout, false)
            val textView = itemView.findViewById<TextView>(R.id.layerName)
            textView.text = layer.name
            itemView.setOnClickListener {
                layerPickerPopup?.dismiss()
                layerPickerPopup = null
                onMoveToLayer?.invoke(layer.id)
                dismiss()
            }
            listLayout.addView(itemView)
        }

        layerPickerPopup?.dismiss()
        layerPickerPopup = PopupWindow(
            listLayout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            isOutsideTouchable = true
            setOnDismissListener { layerPickerPopup = null }
            showAsDropDown(anchor, 0, 0, Gravity.BOTTOM or Gravity.START)
        }

        EpdFastModeController.exitFastMode()
        listLayout.post {
            EpdController.invalidate(listLayout, UpdateMode.GC)
        }
    }

    fun show(
        selectionBounds: RectF,
        matrix: android.graphics.Matrix,
    ) {
        isVisible = true
        container.visibility = View.VISIBLE
        binding.root.visibility = View.VISIBLE
        isSizeValid = false // Force re-measure on show
        updatePosition(selectionBounds, matrix)

        // Force refresh for E-Ink
        EpdFastModeController.exitFastMode()
        binding.root.post {
            EpdController.invalidate(binding.root, UpdateMode.GC)
        }
    }

    fun updatePosition(
        selectionBounds: RectF,
        matrix: android.graphics.Matrix,
    ) {
        if (!isVisible) return
        calculateAndApplyPosition(selectionBounds, matrix)
    }

    private fun calculateAndApplyPosition(
        selectionBounds: RectF,
        matrix: android.graphics.Matrix,
    ) {
        // Calculate screen coordinates using reused buffer
        val cx = selectionBounds.centerX()
        ptsBuffer[0] = cx
        ptsBuffer[1] = selectionBounds.top
        ptsBuffer[2] = cx
        ptsBuffer[3] = selectionBounds.bottom

        matrix.mapPoints(ptsBuffer)

        val screenX = ptsBuffer[0]
        val screenYTop = ptsBuffer[1]
        val screenYBottom = ptsBuffer[3]

        if (!isSizeValid) {
            binding.root.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            cachedWidth = binding.root.measuredWidth
            cachedHeight = binding.root.measuredHeight
            isSizeValid = true
        }

        val popupWidth = cachedWidth.toFloat()
        val popupHeight = cachedHeight.toFloat()

        // Bounds check
        val displayMetrics = context.resources.displayMetrics

        // Default: Show above selection with clearance for Rotation Handle
        val handleClearance = 90f
        var x = screenX - (popupWidth / 2f)
        var y = screenYTop - popupHeight - handleClearance

        // If no space on top, flip to bottom
        if (y < 0) {
            y = screenYBottom + 40f
        }

        // Horizontal Clamping
        if (x < 0) x = 0f
        if (x + popupWidth > displayMetrics.widthPixels) x = displayMetrics.widthPixels.toFloat() - popupWidth

        binding.root.translationX = x
        binding.root.translationY = y
    }

    fun dismiss() {
        if (isVisible) {
            isVisible = false
            layerPickerPopup?.dismiss()
            layerPickerPopup = null
            binding.root.visibility = View.GONE
            container.visibility = View.GONE
            onDismiss()
        }
    }

    fun isShowing() = isVisible

    fun destroy() {
        dismiss()
        container.removeView(binding.root)
    }
}
