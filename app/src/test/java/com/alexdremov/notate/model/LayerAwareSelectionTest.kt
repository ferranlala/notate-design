package com.alexdremov.notate.model

import android.graphics.Path
import android.graphics.RectF
import com.alexdremov.notate.data.region.RegionData
import com.alexdremov.notate.data.region.RegionId
import com.alexdremov.notate.data.region.RegionManager
import com.alexdremov.notate.util.Quadtree
import com.alexdremov.notate.util.StrokeGeometry
import com.google.common.truth.Truth.assertThat
import com.onyx.android.sdk.data.note.TouchPoint
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LayerAwareSelectionTest {
    private lateinit var model: InfiniteCanvasModel
    private lateinit var regionManager: RegionManager

    @Before
    fun setup() {
        model = InfiniteCanvasModel()
        regionManager = mockk(relaxed = true)

        every { regionManager.getContentBounds() } returns RectF()
        every { regionManager.regionSize } returns 1000f
        coEvery { regionManager.addItem(any()) } just Runs
        coEvery { regionManager.removeItems(any()) } just Runs
        coEvery { regionManager.clear() } just Runs

        mockkObject(StrokeGeometry)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createTestStroke(
        order: Long = 0,
        bounds: RectF = RectF(10f, 10f, 20f, 20f),
        layerId: String = Layer.DEFAULT_LAYER_ID,
    ): Stroke {
        val points = listOf(
            TouchPoint(bounds.left, bounds.top, 0.5f, 5f, 100L),
            TouchPoint(bounds.right, bounds.bottom, 0.5f, 5f, 110L),
        )
        return Stroke(
            path = Path(),
            points = points,
            color = 0,
            width = 2f,
            style = StrokeType.FOUNTAIN,
            bounds = bounds,
            strokeOrder = order,
            layerId = layerId,
        )
    }

    private fun setupRegionWithStrokes(vararg strokes: Stroke) {
        val regionId = RegionId(0, 0)
        val region = RegionData(regionId)
        val quadtree = Quadtree(0, RectF(0f, 0f, 1000f, 1000f))
        region.quadtree = quadtree
        strokes.forEach { quadtree.insert(it) }

        coEvery { regionManager.getRegionIdsInRect(any()) } returns listOf(regionId)
        every { regionManager.getRegionReadOnly(any()) } returns region
        coEvery { regionManager.getRegionsInRect(any()) } returns listOf(region)
    }

    @Test
    fun `hitTest returns item on visible unlocked layer`() = runTest {
        model.initializeSession(regionManager)

        val stroke = createTestStroke(order = 1)
        setupRegionWithStrokes(stroke)

        val hit = model.hitTest(15f, 15f)
        assertThat(hit).isEqualTo(stroke)
    }

    @Test
    fun `hitTest skips item on hidden layer`() = runTest {
        model.initializeSession(regionManager)

        val hiddenLayer = model.layerManager.addLayer("Hidden")
        model.layerManager.setVisibility(hiddenLayer.id, false)

        val stroke = createTestStroke(order = 1, layerId = hiddenLayer.id)
        setupRegionWithStrokes(stroke)

        val hit = model.hitTest(15f, 15f)
        assertThat(hit).isNull()
    }

    @Test
    fun `hitTest skips item on locked layer`() = runTest {
        model.initializeSession(regionManager)

        val lockedLayer = model.layerManager.addLayer("Locked")
        model.layerManager.setLocked(lockedLayer.id, true)

        val stroke = createTestStroke(order = 1, layerId = lockedLayer.id)
        setupRegionWithStrokes(stroke)

        val hit = model.hitTest(15f, 15f)
        assertThat(hit).isNull()
    }

    @Test
    fun `hitTest returns item from unlocked layer when locked layer also has items`() = runTest {
        model.initializeSession(regionManager)

        val lockedLayer = model.layerManager.addLayer("Locked")
        model.layerManager.setLocked(lockedLayer.id, true)

        // Stroke on default (unlocked) layer
        val visibleStroke = createTestStroke(order = 2, bounds = RectF(10f, 10f, 20f, 20f))
        // Stroke on locked layer (higher order = sorted first)
        val lockedStroke = createTestStroke(order = 3, bounds = RectF(10f, 10f, 20f, 20f), layerId = lockedLayer.id)

        setupRegionWithStrokes(visibleStroke, lockedStroke)

        val hit = model.hitTest(15f, 15f)
        // Should return the visible stroke, not the locked one
        assertThat(hit).isEqualTo(visibleStroke)
    }

    @Test
    fun `hitTest selects across all unlocked visible layers`() = runTest {
        model.initializeSession(regionManager)

        val layer2 = model.layerManager.addLayer("Layer 2")

        // Item on default layer
        val stroke1 = createTestStroke(order = 1, bounds = RectF(10f, 10f, 20f, 20f))
        // Item on layer 2 — also unlocked and visible
        val stroke2 = createTestStroke(order = 2, bounds = RectF(30f, 30f, 40f, 40f), layerId = layer2.id)

        setupRegionWithStrokes(stroke1, stroke2)

        // Hit test on stroke1 area
        val hit1 = model.hitTest(15f, 15f)
        assertThat(hit1).isEqualTo(stroke1)

        // Hit test on stroke2 area
        val hit2 = model.hitTest(35f, 35f)
        assertThat(hit2).isEqualTo(stroke2)
    }

    @Test
    fun `erase skips items on locked layer`() = runTest {
        model.initializeSession(regionManager)

        val lockedLayer = model.layerManager.addLayer("Locked")
        model.layerManager.setLocked(lockedLayer.id, true)

        val stroke = createTestStroke(order = 1, layerId = lockedLayer.id)
        setupRegionWithStrokes(stroke)

        every { StrokeGeometry.strokeIntersects(any(), any()) } returns true

        val eraserStroke = createTestStroke(bounds = RectF(10f, 10f, 20f, 20f))
        model.erase(eraserStroke, EraserType.STROKE)

        // Should not have removed any items since they are on a locked layer
        coVerify(exactly = 0) { regionManager.removeItems(any()) }
    }

    @Test
    fun `erase skips items on hidden layer`() = runTest {
        model.initializeSession(regionManager)

        val hiddenLayer = model.layerManager.addLayer("Hidden")
        model.layerManager.setVisibility(hiddenLayer.id, false)

        val stroke = createTestStroke(order = 1, layerId = hiddenLayer.id)
        setupRegionWithStrokes(stroke)

        every { StrokeGeometry.strokeIntersects(any(), any()) } returns true

        val eraserStroke = createTestStroke(bounds = RectF(10f, 10f, 20f, 20f))
        model.erase(eraserStroke, EraserType.STROKE)

        coVerify(exactly = 0) { regionManager.removeItems(any()) }
    }

    @Test
    fun `new items are added to active layer`() = runTest {
        model.initializeSession(regionManager)

        val layer2 = model.layerManager.addLayer("Layer 2")
        model.layerManager.setActiveLayer(layer2.id)

        val stroke = createTestStroke()
        val added = model.addItem(stroke)

        assertThat(added).isNotNull()
        assertThat(added!!.layerId).isEqualTo(layer2.id)
    }

    @Test
    fun `new items default to default layer when active is default`() = runTest {
        model.initializeSession(regionManager)

        val stroke = createTestStroke()
        val added = model.addItem(stroke)

        assertThat(added).isNotNull()
        assertThat(added!!.layerId).isEqualTo(Layer.DEFAULT_LAYER_ID)
    }

    @Test
    fun `deleteItemsByLayerId removes all items on the given layer`() = runTest {
        // Set content bounds before initializeSession so the model picks them up
        every { regionManager.getContentBounds() } returns RectF(0f, 0f, 100f, 100f)
        model.initializeSession(regionManager)

        val layer2 = model.layerManager.addLayer("Layer 2")

        // Stroke on default layer
        val stroke1 = createTestStroke(order = 1, bounds = RectF(10f, 10f, 20f, 20f))
        // Stroke on layer 2
        val stroke2 = createTestStroke(order = 2, bounds = RectF(30f, 30f, 40f, 40f), layerId = layer2.id)

        setupRegionWithStrokes(stroke1, stroke2)

        // Mock visitItemsInRect to invoke the visitor with the strokes
        coEvery { regionManager.visitItemsInRect(any(), any()) } answers {
            val visitor = secondArg<(CanvasItem) -> Unit>()
            visitor(stroke1)
            visitor(stroke2)
        }

        model.deleteItemsByLayerId(layer2.id)

        // Should have removed only the layer 2 items
        coVerify { regionManager.removeItems(match { items ->
            items.size == 1 && items[0].layerId == layer2.id
        }) }
    }

    @Test
    fun `deleteItemsByLayerId does nothing when layer has no items`() = runTest {
        // Set content bounds before initializeSession so the model picks them up
        every { regionManager.getContentBounds() } returns RectF(0f, 0f, 100f, 100f)
        model.initializeSession(regionManager)

        val emptyLayer = model.layerManager.addLayer("Empty")

        setupRegionWithStrokes() // No strokes

        // Mock visitItemsInRect to invoke the visitor with no items
        coEvery { regionManager.visitItemsInRect(any(), any()) } answers { }

        model.deleteItemsByLayerId(emptyLayer.id)

        // Should not have called removeItems since there are no items to remove
        coVerify(exactly = 0) { regionManager.removeItems(any()) }
    }

    @Test
    fun `deleteLayerWithContents removes layer and items`() = runTest {
        every { regionManager.getContentBounds() } returns RectF(0f, 0f, 100f, 100f)
        model.initializeSession(regionManager)

        val layer2 = model.layerManager.addLayer("Layer 2")

        val stroke1 = createTestStroke(order = 1, bounds = RectF(10f, 10f, 20f, 20f))
        val stroke2 = createTestStroke(order = 2, bounds = RectF(30f, 30f, 40f, 40f), layerId = layer2.id)

        setupRegionWithStrokes(stroke1, stroke2)

        coEvery { regionManager.visitItemsInRect(any(), any()) } answers {
            val visitor = secondArg<(CanvasItem) -> Unit>()
            visitor(stroke1)
            visitor(stroke2)
        }

        model.deleteLayerWithContents(layer2.id)

        // Items should have been removed
        coVerify { regionManager.removeItems(match { items ->
            items.size == 1 && items[0].layerId == layer2.id
        }) }
        // Layer should have been removed from LayerManager
        assertThat(model.layerManager.getLayer(layer2.id)).isNull()
    }

    @Test
    fun `undo after deleteLayerWithContents restores layer and items`() = runTest {
        every { regionManager.getContentBounds() } returns RectF(0f, 0f, 100f, 100f)
        model.initializeSession(regionManager)

        val layer2 = model.layerManager.addLayer("Layer 2")
        val layer2Id = layer2.id

        val stroke = createTestStroke(order = 1, bounds = RectF(10f, 10f, 20f, 20f), layerId = layer2Id)

        setupRegionWithStrokes(stroke)

        coEvery { regionManager.visitItemsInRect(any(), any()) } answers {
            val visitor = secondArg<(CanvasItem) -> Unit>()
            visitor(stroke)
        }

        model.deleteLayerWithContents(layer2Id)

        // Layer should be gone
        assertThat(model.layerManager.getLayer(layer2Id)).isNull()

        // Undo should restore the layer and items
        model.undo()

        assertThat(model.layerManager.getLayer(layer2Id)).isNotNull()
        assertThat(model.layerManager.getLayer(layer2Id)!!.name).isEqualTo("Layer 2")
        coVerify { regionManager.addItem(stroke) }
    }

    @Test
    fun `redo after undo of deleteLayerWithContents re-deletes layer and items`() = runTest {
        every { regionManager.getContentBounds() } returns RectF(0f, 0f, 100f, 100f)
        model.initializeSession(regionManager)

        val layer2 = model.layerManager.addLayer("Layer 2")
        val layer2Id = layer2.id

        val stroke = createTestStroke(order = 1, bounds = RectF(10f, 10f, 20f, 20f), layerId = layer2Id)

        setupRegionWithStrokes(stroke)

        coEvery { regionManager.visitItemsInRect(any(), any()) } answers {
            val visitor = secondArg<(CanvasItem) -> Unit>()
            visitor(stroke)
        }

        model.deleteLayerWithContents(layer2Id)
        model.undo()

        // Layer should be restored
        assertThat(model.layerManager.getLayer(layer2Id)).isNotNull()

        // Redo should re-delete the layer
        model.redo()

        assertThat(model.layerManager.getLayer(layer2Id)).isNull()
    }
}
