package com.example.util

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display

data class DisplayInfo(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val refreshRate: Float,
    val isDefault: Boolean,
    val isLikelyPip: Boolean,
    val stateDescription: String
)

object DisplayHelper {

    fun getAllDisplays(context: Context): List<DisplayInfo> {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            ?: return emptyList()

        val displays = displayManager.displays
        return displays.map { display ->
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)

            val isDefault = display.displayId == Display.DEFAULT_DISPLAY
            val nameLower = (display.name ?: "").lowercase()
            
            // Check heuristic for PIP display (often non-default, named virtual, pip, sub, or id >= 1)
            val isLikelyPip = !isDefault && (
                nameLower.contains("pip") ||
                nameLower.contains("virtual") ||
                nameLower.contains("sub") ||
                nameLower.contains("secondary") ||
                nameLower.contains("activityview") ||
                nameLower.contains("overlay") ||
                display.displayId == 2 ||
                display.displayId > 0
            )

            val stateStr = when (display.state) {
                Display.STATE_ON -> "Bật (ON)"
                Display.STATE_OFF -> "Tắt (OFF)"
                Display.STATE_DOZE -> "Ngủ (DOZE)"
                else -> "Hoạt động"
            }

            DisplayInfo(
                id = display.displayId,
                name = display.name ?: "Display #${display.displayId}",
                width = point.x,
                height = point.y,
                refreshRate = display.refreshRate,
                isDefault = isDefault,
                isLikelyPip = isLikelyPip,
                stateDescription = stateStr
            )
        }
    }

    /**
     * Resolves the target display ID dynamically.
     * If autoDetect is true:
     * - Searches for active displays with ID != DEFAULT_DISPLAY.
     * - Prioritizes displays whose name contains "pip" or id == 2 or highest secondary ID.
     * - Falls back to manualId or DEFAULT_DISPLAY if no secondary display is found.
     */
    fun resolveTargetDisplayId(context: Context, autoDetect: Boolean, manualId: Int): Int {
        val displays = getAllDisplays(context)
        if (displays.isEmpty()) return Display.DEFAULT_DISPLAY

        if (!autoDetect) {
            // Check if manualId exists in active displays
            val exists = displays.any { it.id == manualId }
            return if (exists) manualId else {
                // If manualId not found, fallback to first non-default display or default
                displays.firstOrNull { !it.isDefault }?.id ?: Display.DEFAULT_DISPLAY
            }
        }

        // Auto-detect mode:
        // 1. Look for display with "pip" in name
        val pipNamed = displays.firstOrNull { !it.isDefault && it.name.lowercase().contains("pip") }
        if (pipNamed != null) return pipNamed.id

        // 2. Look for display with ID 2 specifically (as mentioned in prompt "pip 2")
        val displayTwo = displays.firstOrNull { it.id == 2 }
        if (displayTwo != null) return displayTwo.id

        // 3. Look for any non-default display (highest ID or likely pip)
        val likelyPip = displays.filter { it.isLikelyPip }.maxByOrNull { it.id }
        if (likelyPip != null) return likelyPip.id

        val anySecondary = displays.filter { !it.isDefault }.maxByOrNull { it.id }
        if (anySecondary != null) return anySecondary.id

        // Fallback to default display 0
        return Display.DEFAULT_DISPLAY
    }
}
