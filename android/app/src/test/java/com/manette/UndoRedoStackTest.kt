package com.manette

import com.manette.editor.UndoRedoStack
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests unitaires JVM pour [UndoRedoStack] (P1-1 & P2-6).
 */
class UndoRedoStackTest {

    @Test
    fun `pile vide ne peut ni undo ni redo`() {
        val stack = UndoRedoStack<String>()
        assertFalse(stack.canUndo)
        assertFalse(stack.canRedo)
        assertNull(stack.undo("current"))
        assertNull(stack.redo("current"))
    }

    @Test
    fun `push permet undo puis redo`() {
        val stack = UndoRedoStack<String>()
        stack.push("state_1")
        assertTrue(stack.canUndo)
        assertFalse(stack.canRedo)

        val undone = stack.undo("state_2")
        assertEquals("state_1", undone)
        assertFalse(stack.canUndo)
        assertTrue(stack.canRedo)

        val redone = stack.redo("state_1")
        assertEquals("state_2", redone)
        assertTrue(stack.canUndo)
        assertFalse(stack.canRedo)
    }

    @Test
    fun `nouvelle action vide la pile redo`() {
        val stack = UndoRedoStack<String>()
        stack.push("state_1")
        stack.undo("state_2")
        assertTrue(stack.canRedo)

        // Nouvelle action
        stack.push("state_3")
        assertFalse("Le redo doit être effacé après une nouvelle action", stack.canRedo)
    }

    @Test
    fun `capacite maximale respectee`() {
        val maxCap = 3
        val stack = UndoRedoStack<Int>(maxCapacity = maxCap)
        for (i in 1..5) {
            stack.push(i)
        }
        assertEquals(maxCap, stack.undoCount)
        // Les états les plus anciens (1 et 2) doivent avoir été évincés
        assertEquals(5, stack.undo(6))
        assertEquals(4, stack.undo(5))
        assertEquals(3, stack.undo(4))
        assertNull(stack.undo(3))
    }
}
