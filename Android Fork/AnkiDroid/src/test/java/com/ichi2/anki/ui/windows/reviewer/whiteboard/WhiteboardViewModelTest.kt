// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WhiteboardViewModelTest {
    private lateinit var repository: WhiteboardRepository
    private lateinit var viewModel: WhiteboardViewModel

    @Before
    fun setUp() {
        repository = WhiteboardRepository(SPMockBuilder().createSharedPreferences())
        viewModel = WhiteboardViewModel(repository)
        viewModel.loadState(isDarkMode = false)
    }

    @Test
    fun `pressing stylus button enables eraser when brush was active`() {
        assertFalse(viewModel.isEraserActive)
        val initialTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(initialTool)
        val initialBrushWidth = initialTool.width
        val initialBrushColor = initialTool.color

        viewModel.setStylusButtonPressed(true)

        assertTrue(viewModel.isEraserActive)
        val eraserTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Eraser>(eraserTool)
        assertEquals(viewModel.eraser.width, eraserTool.width)

        viewModel.setStylusButtonPressed(false)

        assertFalse(viewModel.isEraserActive)
        val restoredTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(restoredTool)
        assertEquals(initialBrushWidth, restoredTool.width)
        assertEquals(initialBrushColor, restoredTool.color)
    }

    @Test
    fun `releasing stylus button leaves eraser active if eraser was already active before pressing`() {
        viewModel.enableEraser()
        assertTrue(viewModel.isEraserActive)
        assertIs<WhiteboardTool.Eraser>(viewModel.activeTool.value)

        viewModel.setStylusButtonPressed(true)
        assertTrue(viewModel.isEraserActive)
        assertIs<WhiteboardTool.Eraser>(viewModel.activeTool.value)

        viewModel.setStylusButtonPressed(false)
        assertTrue(viewModel.isEraserActive)
        assertIs<WhiteboardTool.Eraser>(viewModel.activeTool.value)
    }

    @Test
    fun `selecting brush while stylus button is pressed keeps brush after button is released`() {
        viewModel.setStylusButtonPressed(true)
        assertTrue(viewModel.isEraserActive)

        viewModel.setActiveBrush(0)
        assertFalse(viewModel.isEraserActive)
        assertIs<WhiteboardTool.Brush>(viewModel.activeTool.value)

        viewModel.setStylusButtonPressed(false)
        assertFalse(viewModel.isEraserActive)
        assertIs<WhiteboardTool.Brush>(viewModel.activeTool.value)
    }

    @Test
    fun `stylus button preserves selected brush index when restoring`() {
        viewModel.addBrush(0xFF00FF)
        val secondBrushIndex = viewModel.brushes.value.lastIndex
        viewModel.setActiveBrush(secondBrushIndex)
        val selectedBrush = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(selectedBrush)
        val selectedBrushWidth = selectedBrush.width
        val selectedBrushColor = selectedBrush.color

        viewModel.setStylusButtonPressed(true)
        assertTrue(viewModel.isEraserActive)
        assertIs<WhiteboardTool.Eraser>(viewModel.activeTool.value)

        viewModel.setStylusButtonPressed(false)
        assertFalse(viewModel.isEraserActive)
        val restoredTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(restoredTool)
        assertEquals(secondBrushIndex, viewModel.activeBrushIndex.value)
        assertEquals(selectedBrushWidth, restoredTool.width)
        assertEquals(selectedBrushColor, restoredTool.color)
    }

    @Test
    fun `toggleEraser toggles between eraser and brush`() {
        assertFalse(viewModel.isEraserActive)
        val initialBrush = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(initialBrush)
        val initialBrushIndex = viewModel.activeBrushIndex.value

        viewModel.toggleEraser()
        assertTrue(viewModel.isEraserActive)
        val eraserTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Eraser>(eraserTool)
        assertEquals(viewModel.eraser.width, eraserTool.width)

        viewModel.toggleEraser()
        assertFalse(viewModel.isEraserActive)
        val restoredBrush = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(restoredBrush)
        assertEquals(initialBrushIndex, viewModel.activeBrushIndex.value)
    }

    @Test
    fun `setActiveStrokeWidth updates active brush or eraser depending on active tool`() {
        viewModel.setActiveStrokeWidth(42f)
        val activeBrush = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(activeBrush)
        assertEquals(42f, activeBrush.width)
        assertEquals(42f, viewModel.brushes.value[viewModel.activeBrushIndex.value].width)

        viewModel.enableEraser()
        val eraserTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Eraser>(eraserTool)
        assertEquals(viewModel.eraser.width, eraserTool.width)

        viewModel.setActiveStrokeWidth(35f)
        val updatedEraser = viewModel.activeTool.value
        assertIs<WhiteboardTool.Eraser>(updatedEraser)
        assertEquals(35f, updatedEraser.width)
        assertEquals(35f, viewModel.eraser.width)
        assertEquals(35f, viewModel.eraser.inkWidth)
    }

    @Test
    fun `setEraserMode updates eraser config and active tool when eraser is active`() {
        viewModel.enableEraser()
        viewModel.setActiveStrokeWidth(25f)

        viewModel.setEraserMode(EraserMode.STROKE)
        assertEquals(EraserMode.STROKE, viewModel.eraser.mode)
        val eraserTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Eraser>(eraserTool)
        assertEquals(EraserMode.STROKE, eraserTool.mode)
        assertEquals(viewModel.eraser.strokeEraserWidth, eraserTool.width)

        viewModel.setEraserMode(EraserMode.INK)
        assertEquals(25f, (viewModel.activeTool.value as WhiteboardTool.Eraser).width)
        assertEquals(25f, viewModel.eraser.inkWidth)
    }

    @Test
    fun `setEraserStrokeWidth updates eraser config even when brush is active`() {
        assertFalse(viewModel.isEraserActive)
        viewModel.setEraserStrokeWidth(48f)
        assertEquals(48f, viewModel.eraser.width)
        assertEquals(48f, viewModel.eraser.inkWidth)
        assertFalse(viewModel.isEraserActive)

        viewModel.enableEraser()
        assertEquals(48f, viewModel.activeTool.value.width)
    }

    @Test
    fun `updateBrushColor updates active tool and brushes list`() {
        viewModel.updateBrushColor(0x123456)
        val brushTool = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(brushTool)
        assertEquals(0x123456, brushTool.color)
        assertEquals(0x123456, viewModel.brushes.value[viewModel.activeBrushIndex.value].color)
    }

    @Test
    fun `removeBrush updates palette and active brush index`() {
        viewModel.addBrush(0x111111)
        viewModel.addBrush(0x222222)
        val lastIndex = viewModel.brushes.value.lastIndex
        viewModel.setActiveBrush(lastIndex)
        assertEquals(lastIndex, viewModel.activeBrushIndex.value)

        viewModel.removeBrush(lastIndex)
        val newBrush = viewModel.activeTool.value
        assertIs<WhiteboardTool.Brush>(newBrush)
        assertEquals(lastIndex - 1, viewModel.activeBrushIndex.value)
    }

    @Test
    fun `currentBrushColor returns active brush color or fallback`() {
        val initialBrushColor = (viewModel.activeTool.value as WhiteboardTool.Brush).color
        assertEquals(initialBrushColor, viewModel.currentBrushColor)

        viewModel.updateBrushColor(0x123456)
        assertEquals(0x123456, viewModel.currentBrushColor)

        viewModel.enableEraser()
        assertEquals(0x123456, viewModel.currentBrushColor)
    }
}

private val WhiteboardViewModel.isEraserActive: Boolean
    get() = activeTool.value is WhiteboardTool.Eraser
