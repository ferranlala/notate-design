package com.alexdremov.notate.model

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class LayerManagerTest {
    private lateinit var layerManager: LayerManager

    @Before
    fun setUp() {
        layerManager = LayerManager()
    }

    @Test
    fun `default state has one default layer`() {
        val layers = layerManager.getLayers()
        assertEquals(1, layers.size)
        assertEquals(Layer.DEFAULT_LAYER_ID, layers[0].id)
        assertEquals("Layer 1", layers[0].name)
        assertTrue(layers[0].isVisible)
        assertFalse(layers[0].isLocked)
    }

    @Test
    fun `active layer defaults to default layer`() {
        assertEquals(Layer.DEFAULT_LAYER_ID, layerManager.getActiveLayerId())
        assertEquals(Layer.DEFAULT_LAYER_ID, layerManager.getActiveLayer().id)
    }

    @Test
    fun `addLayer creates new layer and sets it active`() {
        val newLayer = layerManager.addLayer("Layer 2")

        assertEquals("Layer 2", newLayer.name)
        assertTrue(newLayer.isVisible)
        assertFalse(newLayer.isLocked)
        assertEquals(2, layerManager.getLayers().size)
        assertEquals(newLayer.id, layerManager.getActiveLayerId())
    }

    @Test
    fun `setActiveLayer switches active layer`() {
        val layer2 = layerManager.addLayer("Layer 2")

        // Switch back to default
        assertTrue(layerManager.setActiveLayer(Layer.DEFAULT_LAYER_ID))
        assertEquals(Layer.DEFAULT_LAYER_ID, layerManager.getActiveLayerId())

        // Switch to layer 2
        assertTrue(layerManager.setActiveLayer(layer2.id))
        assertEquals(layer2.id, layerManager.getActiveLayerId())
    }

    @Test
    fun `setActiveLayer returns false for nonexistent layer`() {
        assertFalse(layerManager.setActiveLayer("nonexistent"))
    }

    @Test
    fun `removeLayer removes and adjusts active layer`() {
        val layer2 = layerManager.addLayer("Layer 2")
        assertEquals(layer2.id, layerManager.getActiveLayerId())

        // Remove active layer 2
        assertTrue(layerManager.removeLayer(layer2.id))
        assertEquals(1, layerManager.getLayers().size)
        assertEquals(Layer.DEFAULT_LAYER_ID, layerManager.getActiveLayerId())
    }

    @Test
    fun `removeLayer cannot remove last layer`() {
        assertFalse(layerManager.removeLayer(Layer.DEFAULT_LAYER_ID))
        assertEquals(1, layerManager.getLayers().size)
    }

    @Test
    fun `removeLayer returns false for nonexistent layer`() {
        assertFalse(layerManager.removeLayer("nonexistent"))
    }

    @Test
    fun `toggleVisibility flips visibility`() {
        val layer = layerManager.getLayers()[0]
        assertTrue(layer.isVisible)

        val toggled = layerManager.toggleVisibility(layer.id)
        assertNotNull(toggled)
        assertFalse(toggled!!.isVisible)

        val toggledBack = layerManager.toggleVisibility(layer.id)
        assertNotNull(toggledBack)
        assertTrue(toggledBack!!.isVisible)
    }

    @Test
    fun `setVisibility sets visibility explicitly`() {
        val layerId = layerManager.getLayers()[0].id

        layerManager.setVisibility(layerId, false)
        assertFalse(layerManager.getLayer(layerId)!!.isVisible)

        layerManager.setVisibility(layerId, true)
        assertTrue(layerManager.getLayer(layerId)!!.isVisible)
    }

    @Test
    fun `toggleLock flips lock state`() {
        val layer = layerManager.getLayers()[0]
        assertFalse(layer.isLocked)

        val toggled = layerManager.toggleLock(layer.id)
        assertNotNull(toggled)
        assertTrue(toggled!!.isLocked)

        val toggledBack = layerManager.toggleLock(layer.id)
        assertNotNull(toggledBack)
        assertFalse(toggledBack!!.isLocked)
    }

    @Test
    fun `setLocked sets locked state explicitly`() {
        val layerId = layerManager.getLayers()[0].id

        layerManager.setLocked(layerId, true)
        assertTrue(layerManager.getLayer(layerId)!!.isLocked)

        layerManager.setLocked(layerId, false)
        assertFalse(layerManager.getLayer(layerId)!!.isLocked)
    }

    @Test
    fun `renameLayer changes name`() {
        val layerId = layerManager.getLayers()[0].id
        val renamed = layerManager.renameLayer(layerId, "Sketch Layer")

        assertNotNull(renamed)
        assertEquals("Sketch Layer", renamed!!.name)
        assertEquals("Sketch Layer", layerManager.getLayer(layerId)!!.name)
    }

    @Test
    fun `moveLayer reorders layers`() {
        layerManager.addLayer("Layer 2")
        layerManager.addLayer("Layer 3")

        val originalOrder = layerManager.getLayers().map { it.name }
        assertEquals(listOf("Layer 1", "Layer 2", "Layer 3"), originalOrder)

        assertTrue(layerManager.moveLayer(0, 2))
        val newOrder = layerManager.getLayers().map { it.name }
        assertEquals(listOf("Layer 2", "Layer 3", "Layer 1"), newOrder)
    }

    @Test
    fun `moveLayer returns false for invalid indices`() {
        assertFalse(layerManager.moveLayer(-1, 0))
        assertFalse(layerManager.moveLayer(0, 5))
    }

    @Test
    fun `getHiddenLayerIds returns only hidden layers`() {
        layerManager.addLayer("Layer 2")
        val layers = layerManager.getLayers()

        layerManager.setVisibility(layers[0].id, false)

        val hidden = layerManager.getHiddenLayerIds()
        assertEquals(1, hidden.size)
        assertTrue(hidden.contains(layers[0].id))
        assertFalse(hidden.contains(layers[1].id))
    }

    @Test
    fun `getLockedLayerIds returns only locked layers`() {
        layerManager.addLayer("Layer 2")
        val layers = layerManager.getLayers()

        layerManager.setLocked(layers[1].id, true)

        val locked = layerManager.getLockedLayerIds()
        assertEquals(1, locked.size)
        assertTrue(locked.contains(layers[1].id))
        assertFalse(locked.contains(layers[0].id))
    }

    @Test
    fun `getUnselectableLayerIds returns hidden and locked layers`() {
        layerManager.addLayer("Layer 2")
        layerManager.addLayer("Layer 3")
        val layers = layerManager.getLayers()

        layerManager.setVisibility(layers[0].id, false) // hidden
        layerManager.setLocked(layers[1].id, true)      // locked

        val unselectable = layerManager.getUnselectableLayerIds()
        assertEquals(2, unselectable.size)
        assertTrue(unselectable.contains(layers[0].id))
        assertTrue(unselectable.contains(layers[1].id))
        assertFalse(unselectable.contains(layers[2].id))
    }

    @Test
    fun `isLayerSelectable returns correct result`() {
        layerManager.addLayer("Layer 2")
        val layers = layerManager.getLayers()

        assertTrue(layerManager.isLayerSelectable(layers[0].id))
        assertTrue(layerManager.isLayerSelectable(layers[1].id))

        layerManager.setLocked(layers[0].id, true)
        assertFalse(layerManager.isLayerSelectable(layers[0].id))

        layerManager.setVisibility(layers[1].id, false)
        assertFalse(layerManager.isLayerSelectable(layers[1].id))
    }

    @Test
    fun `isLayerVisible returns correct result`() {
        val layerId = layerManager.getLayers()[0].id
        assertTrue(layerManager.isLayerVisible(layerId))

        layerManager.setVisibility(layerId, false)
        assertFalse(layerManager.isLayerVisible(layerId))

        // Unknown layer treated as visible
        assertTrue(layerManager.isLayerVisible("unknown"))
    }

    @Test
    fun `setLayers replaces all layers`() {
        val newLayers = listOf(
            Layer("a", "Alpha"),
            Layer("b", "Beta"),
            Layer("c", "Gamma"),
        )
        layerManager.setLayers(newLayers)

        assertEquals(3, layerManager.getLayers().size)
        assertEquals(listOf("Alpha", "Beta", "Gamma"), layerManager.getLayers().map { it.name })
        // Active layer should fall back to first since default no longer exists
        assertEquals("a", layerManager.getActiveLayerId())
    }

    @Test
    fun `setLayers with empty list creates default layer`() {
        layerManager.setLayers(emptyList())
        assertEquals(1, layerManager.getLayers().size)
        assertEquals(Layer.DEFAULT_LAYER_ID, layerManager.getLayers()[0].id)
    }

    @Test
    fun `removeLayer selects previous layer when active is removed`() {
        layerManager.addLayer("Layer 2")
        layerManager.addLayer("Layer 3")
        val layers = layerManager.getLayers()

        // Activate middle layer
        layerManager.setActiveLayer(layers[1].id)

        // Remove it
        layerManager.removeLayer(layers[1].id)

        // Should select the previous layer (index 0)
        assertEquals(layers[0].id, layerManager.getActiveLayerId())
    }

    @Test
    fun `getLayer returns null for unknown id`() {
        assertNull(layerManager.getLayer("unknown"))
    }

    @Test
    fun `toggleVisibility returns null for unknown layer`() {
        assertNull(layerManager.toggleVisibility("unknown"))
    }

    @Test
    fun `toggleLock returns null for unknown layer`() {
        assertNull(layerManager.toggleLock("unknown"))
    }

    @Test
    fun `renameLayer returns null for unknown layer`() {
        assertNull(layerManager.renameLayer("unknown", "New Name"))
    }

    @Test
    fun `getLayerIndex returns correct index`() {
        val layer2 = layerManager.addLayer("Layer 2")
        val layer3 = layerManager.addLayer("Layer 3")

        assertEquals(0, layerManager.getLayerIndex(Layer.DEFAULT_LAYER_ID))
        assertEquals(1, layerManager.getLayerIndex(layer2.id))
        assertEquals(2, layerManager.getLayerIndex(layer3.id))
    }

    @Test
    fun `getLayerIndex returns -1 for unknown id`() {
        assertEquals(-1, layerManager.getLayerIndex("unknown"))
    }

    @Test
    fun `restoreLayer inserts layer at correct position`() {
        val layer2 = layerManager.addLayer("Layer 2")
        val layer3 = layerManager.addLayer("Layer 3")

        // Remove layer 2
        layerManager.removeLayer(layer2.id)
        assertEquals(2, layerManager.getLayers().size)
        assertNull(layerManager.getLayer(layer2.id))

        // Restore layer 2 at original position (index 1)
        layerManager.restoreLayer(layer2, 1)
        val layers = layerManager.getLayers()
        assertEquals(3, layers.size)
        assertEquals(Layer.DEFAULT_LAYER_ID, layers[0].id)
        assertEquals(layer2.id, layers[1].id)
        assertEquals(layer3.id, layers[2].id)
    }

    @Test
    fun `restoreLayer clamps index to valid range`() {
        // Restore at index beyond current size
        val layer = Layer(id = "restored", name = "Restored")
        layerManager.restoreLayer(layer, 100)
        val layers = layerManager.getLayers()
        assertEquals(2, layers.size)
        assertEquals("restored", layers.last().id)
    }
}
