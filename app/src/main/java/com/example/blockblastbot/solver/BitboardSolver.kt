package com.example.blockblastbot.solver
import android.graphics.PointF
import com.example.blockblastbot.core.BotStrategy
data class Piece(val slotIndex: Int, val cells: List<Pair<Int, Int>>, val trayOriginScreen: PointF, val height: Int = (cells.maxOfOrNull { it.first } ?: 0) + 1, val width: Int = (cells.maxOfOrNull { it.second } ?: 0) + 1)
data class Move(val piece: Piece, val targetRow: Int, val targetCol: Int)
data class Solution(val moves: List<Move>, val score: Double)
object BitboardSolver {
    private val ROW_MASKS = ULongArray(8) { r -> 0xFFUL shl (r * 8) }
    private val COL_MASKS = ULongArray(8) { c -> var m = 0UL; for (r in 0..7) m = m or (1UL shl (r * 8 + c)); m }
    fun findBestSequence(initialBoard: ULong, pieces: List<Piece>, strategy: BotStrategy): Solution? {
        if (pieces.isEmpty()) return null
        var best: Solution? = null
        for (order in permute(pieces)) {
            val cand = dfs(initialBoard, order, 0, 0, emptyList(), strategy)
            if (cand != null && (best == null || cand.score > best.score)) best = cand
        }
        return best
    }
    private fun dfs(board: ULong, pieces: List<Piece>, index: Int, clearedTotal: Int, moves: List<Move>, strategy: BotStrategy): Solution? {
        if (index == pieces.size) return Solution(moves, evaluate(board, clearedTotal, strategy))
        val piece = pieces[index]
        if (piece.height > 8 || piece.width > 8) return null
        var bestBranch: Solution? = null
        for (r in 0..(8 - piece.height)) {
            for (c in 0..(8 - piece.width)) {
                var mask = 0UL
                for ((dr, dc) in piece.cells) mask = mask or (1UL shl ((r + dr) * 8 + (c + dc)))
                if ((board and mask) == 0UL) {
                    val (nextBoard, clearedNow) = placeAndClear(board, mask)
                    val branch = dfs(nextBoard, pieces, index + 1, clearedTotal + clearedNow, moves + Move(piece, r, c), strategy)
                    if (branch != null && (bestBranch == null || branch.score > bestBranch.score)) bestBranch = branch
                }
            }
        }
        return bestBranch
    }
    private fun placeAndClear(board: ULong, pieceMask: ULong): Pair<ULong, Int> {
        val placed = board or pieceMask
        var clearMask = 0UL; var lines = 0
        for (r in 0..7) if ((placed and ROW_MASKS[r]) == ROW_MASKS[r]) { clearMask = clearMask or ROW_MASKS[r]; lines++ }
        for (c in 0..7) if ((placed and COL_MASKS[c]) == COL_MASKS[c]) { clearMask = clearMask or COL_MASKS[c]; lines++ }
        return Pair(placed and clearMask.inv(), lines)
    }
    private fun evaluate(board: ULong, clearedLines: Int, strategy: BotStrategy): Double {
        val occupied = board.countOneBits()
        var transitions = 0; var isolatedHoles = 0
        for (r in 0..7) for (c in 0..7) {
            val cur = (board and (1UL shl (r * 8 + c))) != 0UL
            if (c < 7 && cur != ((board and (1UL shl (r * 8 + c + 1))) != 0UL)) transitions++
            if (r < 7 && cur != ((board and (1UL shl ((r + 1) * 8 + c))) != 0UL)) transitions++
            if (!cur) {
                val up = r == 0 || (board and (1UL shl ((r - 1) * 8 + c))) != 0UL
                val down = r == 7 || (board and (1UL shl ((r + 1) * 8 + c))) != 0UL
                val left = c == 0 || (board and (1UL shl (r * 8 + c - 1))) != 0UL
                val right = c == 7 || (board and (1UL shl (r * 8 + c + 1))) != 0UL
                if (up && down && left && right) isolatedHoles++
            }
        }
        return when (strategy) {
            BotStrategy.BALANCED -> (clearedLines * 140.0) - (occupied * 2.5) - (transitions * 3.2) - (isolatedHoles * 24.0)
            BotStrategy.SAFE -> (clearedLines * 90.0) - (occupied * 3.0) - (transitions * 5.0) - (isolatedHoles * 45.0)
            BotStrategy.CLEAR_FIELD -> (clearedLines * 180.0) - (occupied * 6.0) - (transitions * 2.0) - (isolatedHoles * 15.0)
        }
    }
    private fun permute(list: List<Piece>): List<List<Piece>> {
        if (list.size <= 1) return listOf(list)
        val out = mutableListOf<List<Piece>>()
        for (i in list.indices) {
            val item = list[i]; val rest = list.take(i) + list.drop(i + 1)
            for (p in permute(rest)) out.add(listOf(item) + p)
        }
        return out
    }
}
