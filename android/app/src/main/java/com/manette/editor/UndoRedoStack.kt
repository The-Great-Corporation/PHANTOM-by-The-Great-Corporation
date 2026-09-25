package com.manette.editor

/**
 * Pile d'annulation / rétablissement (Undo / Redo) — Kotlin pur sans dépendance Android (P1-1).
 *
 * Permet de gérer l'historique d'états d'édition de façon fiable et testable en JVM.
 */
class UndoRedoStack<T>(private val maxCapacity: Int = 50) {

    private val undoStack = mutableListOf<T>()
    private val redoStack = mutableListOf<T>()

    val canUndo: Boolean
        get() = undoStack.isNotEmpty()

    val canRedo: Boolean
        get() = redoStack.isNotEmpty()

    val undoCount: Int
        get() = undoStack.size

    val redoCount: Int
        get() = redoStack.size

    /**
     * Enregistre un nouvel état sur la pile undo et vide la pile redo.
     */
    fun push(state: T) {
        if (undoStack.size >= maxCapacity) {
            undoStack.removeAt(0)
        }
        undoStack.add(state)
        redoStack.clear()
    }

    /**
     * Annule l'action courante en renvoyant l'état précédent et en poussant [currentState] sur redo.
     * Retourne null si aucune annulation n'est possible.
     */
    fun undo(currentState: T): T? {
        if (undoStack.isEmpty()) return null
        val previousState = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(currentState)
        return previousState
    }

    /**
     * Rétablit l'action annulée en renvoyant l'état rétabli et en poussant [currentState] sur undo.
     * Retourne null si aucun rétablissement n'est possible.
     */
    fun redo(currentState: T): T? {
        if (redoStack.isEmpty()) return null
        val nextState = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(currentState)
        return nextState
    }

    /**
     * Réinitialise les deux piles.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
