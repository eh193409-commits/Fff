package com.example.sara.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

object AccessibilityBridge {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastObservedPackage = MutableStateFlow<String?>(null)
    val lastObservedPackage: StateFlow<String?> = _lastObservedPackage.asStateFlow()

    @Volatile
    private var serviceRef: WeakReference<AccessibilityAutomationService>? = null

    internal fun attach(service: AccessibilityAutomationService) {
        serviceRef = WeakReference(service)
        _isConnected.value = true
    }

    internal fun detach() {
        serviceRef?.clear()
        serviceRef = null
        _isConnected.value = false
    }

    internal fun updateActivePackage(pkg: String?) {
        if (!pkg.isNullOrBlank()) {
            _lastObservedPackage.value = pkg
        }
    }

    fun getService(): AccessibilityAutomationService? = serviceRef?.get()

    fun isServiceEnabledInSettings(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedComponent = ComponentName(context, AccessibilityAutomationService::class.java)
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            val componentStr = splitter.next()
            val enabled = ComponentName.unflattenFromString(componentStr)
            if (enabled == expectedComponent) return true
        }
        return false
    }

    fun openAccessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

class AccessibilityAutomationService : AccessibilityService() {

    private val sensitiveKeywords = listOf(
        "pay", "buy", "purchase", "checkout", "place order", "confirm order",
        "transfer", "send money", "wire", "delete", "erase", "factory reset",
        "uninstall", "remove account", "authorize", "confirm payment", "wallet"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero background logging/monitoring: only track current active package name for context.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityBridge.updateActivePackage(event.packageName?.toString())
        }
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AccessibilityBridge.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        AccessibilityBridge.detach()
        super.onDestroy()
    }

    fun inspectVisibleElements(maxItems: Int = 35): List<VisibleUiElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<VisibleUiElement>()
        collectNodes(root, elements, maxItems)
        return elements
    }

    private fun collectNodes(
        node: AccessibilityNodeInfo?,
        out: MutableList<VisibleUiElement>,
        maxItems: Int
    ) {
        if (node == null || out.size >= maxItems) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.trim().orEmpty()
        val className = node.className?.toString().orEmpty()

        val hasMeaningfulLabel = text.isNotEmpty() || desc.isNotEmpty()
        if (node.isVisibleToUser && (node.isClickable || node.isScrollable || hasMeaningfulLabel)) {
            if (hasMeaningfulLabel || node.isClickable) {
                out.add(
                    VisibleUiElement(
                        text = text,
                        contentDescription = desc,
                        viewId = viewId,
                        className = className,
                        isClickable = node.isClickable,
                        isScrollable = node.isScrollable,
                        bounds = rect
                    )
                )
            }
        }

        for (i in 0 until node.childCount) {
            if (out.size >= maxItems) break
            collectNodes(node.getChild(i), out, maxItems)
        }
    }

    fun clickElementByQuery(
        query: String,
        bypassConfirmation: Boolean = false
    ): ControlResult {
        val root = rootInActiveWindow
            ?: return ControlResult.Error(
                spokenResponse = "I can't inspect the active screen right now.",
                technicalReason = "rootInActiveWindow is null"
            )

        val cleaned = query.trim().lowercase()
        if (cleaned.isBlank()) {
            return ControlResult.Error("Please tell me which button or element to click.")
        }

        val match = findBestMatchingNode(root, cleaned)
            ?: return ControlResult.Error(
                spokenResponse = "I couldn't find \"$query\" on the current screen.",
                technicalReason = "No AccessibilityNodeInfo matched '$query'"
            )

        val targetLabel = match.text?.toString()?.takeIf { it.isNotBlank() }
            ?: match.contentDescription?.toString()?.takeIf { it.isNotBlank() }
            ?: query

        if (!bypassConfirmation && isSensitiveTarget(targetLabel)) {
            val action = PendingSensitiveAction(
                id = "click_${System.currentTimeMillis()}",
                title = "Confirm Sensitive Screen Click",
                description = "Sara detected a sensitive or destructive action (\"$targetLabel\"). Please confirm before clicking.",
                targetLabel = targetLabel,
                onConfirmAction = { clickElementByQuery(query, bypassConfirmation = true) }
            )
            return ControlResult.ConfirmationRequired(
                spokenResponse = "\"$targetLabel\" looks like a sensitive action. Please confirm on screen if you want me to tap it.",
                pendingAction = action
            )
        }

        return performClickOnNodeOrAncestor(match, targetLabel)
    }

    fun clickNthResult(indexZeroBased: Int, bypassConfirmation: Boolean = false): ControlResult {
        val root = rootInActiveWindow
            ?: return ControlResult.Error("I can't inspect the active screen right now.")

        val clickableItems = mutableListOf<AccessibilityNodeInfo>()
        collectClickableContentItems(root, clickableItems)

        if (clickableItems.isEmpty()) {
            return ControlResult.Error("I couldn't find selectable results on the current screen.")
        }

        val safeIndex = indexZeroBased.coerceIn(0, clickableItems.lastIndex)
        val node = clickableItems[safeIndex]
        val label = extractFirstReadableText(node) ?: "item ${safeIndex + 1}"

        if (!bypassConfirmation && isSensitiveTarget(label)) {
            val action = PendingSensitiveAction(
                id = "click_nth_${System.currentTimeMillis()}",
                title = "Confirm Sensitive Selection",
                description = "Selecting \"$label\" may trigger a sensitive or financial action. Confirm to proceed.",
                targetLabel = label,
                onConfirmAction = { clickNthResult(indexZeroBased, bypassConfirmation = true) }
            )
            return ControlResult.ConfirmationRequired(
                spokenResponse = "\"$label\" appears sensitive. Please confirm before I tap it.",
                pendingAction = action
            )
        }

        return performClickOnNodeOrAncestor(node, label)
    }

    fun scroll(direction: ScrollDirection): ControlResult {
        val root = rootInActiveWindow
            ?: return ControlResult.Error("I can't access the active window to scroll.")

        val scrollable = findFirstScrollableNode(root)
        return when (direction) {
            ScrollDirection.DOWN -> {
                val acted = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
                if (acted || performSwipeGesture(SwipeDirection.UP)) {
                    ControlResult.Success("Scrolling down.")
                } else {
                    ControlResult.Error("This screen doesn't appear to scroll further down.")
                }
            }
            ScrollDirection.UP -> {
                val acted = scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
                if (acted || performSwipeGesture(SwipeDirection.DOWN)) {
                    ControlResult.Success("Scrolling up.")
                } else {
                    ControlResult.Error("This screen doesn't appear to scroll further up.")
                }
            }
            ScrollDirection.BOTTOM -> {
                if (scrollable != null) {
                    var moved = false
                    repeat(5) {
                        if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                            moved = true
                        }
                    }
                    if (moved) ControlResult.Success("Scrolling to the bottom.")
                    else if (performSwipeGesture(SwipeDirection.UP, distanceFactor = 0.72f)) {
                        ControlResult.Success("Swiping toward the bottom.")
                    } else ControlResult.Error("Could not scroll to the bottom.")
                } else if (performSwipeGesture(SwipeDirection.UP, distanceFactor = 0.72f)) {
                    ControlResult.Success("Swiping toward the bottom.")
                } else {
                    ControlResult.Error("Could not scroll to the bottom.")
                }
            }
            ScrollDirection.TOP -> {
                if (scrollable != null) {
                    var moved = false
                    repeat(5) {
                        if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) {
                            moved = true
                        }
                    }
                    if (moved) ControlResult.Success("Scrolling to the top.")
                    else if (performSwipeGesture(SwipeDirection.DOWN, distanceFactor = 0.72f)) {
                        ControlResult.Success("Swiping toward the top.")
                    } else ControlResult.Error("Could not scroll to the top.")
                } else if (performSwipeGesture(SwipeDirection.DOWN, distanceFactor = 0.72f)) {
                    ControlResult.Success("Swiping toward the top.")
                } else {
                    ControlResult.Error("Could not scroll to the top.")
                }
            }
        }
    }

    fun swipe(direction: SwipeDirection): ControlResult {
        val ok = performSwipeGesture(direction)
        return if (ok) {
            ControlResult.Success("Swiping ${direction.name.lowercase()}.")
        } else {
            ControlResult.Error("Unable to dispatch ${direction.name.lowercase()} swipe gesture.")
        }
    }

    fun performGlobalNavigation(action: GlobalNavAction): ControlResult {
        val flag = when (action) {
            GlobalNavAction.BACK -> GLOBAL_ACTION_BACK
            GlobalNavAction.HOME -> GLOBAL_ACTION_HOME
            GlobalNavAction.RECENTS -> GLOBAL_ACTION_RECENTS
            GlobalNavAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            GlobalNavAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
        }
        val ok = performGlobalAction(flag)
        return if (ok) {
            val spoken = when (action) {
                GlobalNavAction.BACK -> "Navigating back."
                GlobalNavAction.HOME -> "Going to the home screen."
                GlobalNavAction.RECENTS -> "Opening recent apps."
                GlobalNavAction.NOTIFICATIONS -> "Opening the notification shade."
                GlobalNavAction.QUICK_SETTINGS -> "Opening Quick Settings."
            }
            ControlResult.Success(spoken)
        } else {
            ControlResult.Error("Android declined the global navigation action ${action.name}.")
        }
    }

    private fun performClickOnNodeOrAncestor(
        node: AccessibilityNodeInfo,
        label: String
    ): ControlResult {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ControlResult.Success("Clicked $label.")
            }
            current = current.parent
        }

        // Fallback: dispatch tap gesture on the node's screen bounds
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.isEmpty && performTapGesture(rect.exactCenterX(), rect.exactCenterY())) {
            return ControlResult.Success("Tapped $label.")
        }

        return ControlResult.Error("Found $label, but it is not clickable right now.")
    }

    private fun performTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 60L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun performSwipeGesture(
        direction: SwipeDirection,
        distanceFactor: Float = 0.52f
    ): Boolean {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat().coerceAtLeast(400f)
        val h = metrics.heightPixels.toFloat().coerceAtLeast(800f)
        val cx = w * 0.5f
        val cy = h * 0.5f

        val (startX, startY, endX, endY) = when (direction) {
            SwipeDirection.LEFT -> Quad(w * 0.82f, cy, w * (0.82f - distanceFactor), cy)
            SwipeDirection.RIGHT -> Quad(w * 0.18f, cy, w * (0.18f + distanceFactor), cy)
            SwipeDirection.UP -> Quad(cx, h * 0.72f, cx, h * (0.72f - distanceFactor))
            SwipeDirection.DOWN -> Quad(cx, h * 0.28f, cx, h * (0.28f + distanceFactor))
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 280L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private data class Quad(val sx: Float, val sy: Float, val ex: Float, val ey: Float)

    private fun isSensitiveTarget(label: String): Boolean {
        val lower = label.lowercase()
        return sensitiveKeywords.any { lower.contains(it) }
    }

    private fun findBestMatchingNode(
        root: AccessibilityNodeInfo,
        queryLower: String
    ): AccessibilityNodeInfo? {
        val allNodes = mutableListOf<AccessibilityNodeInfo>()
        flattenNodes(root, allNodes)

        // Exact match first, then substring match
        return allNodes.firstOrNull { node ->
            val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            text == queryLower || desc == queryLower || id == queryLower
        } ?: allNodes.firstOrNull { node ->
            val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val desc = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            val id = node.viewIdResourceName?.substringAfterLast('/')?.lowercase().orEmpty()
            text.contains(queryLower) || desc.contains(queryLower) || id.contains(queryLower)
        }
    }

    private fun flattenNodes(node: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (node == null || out.size > 300) return
        if (node.isVisibleToUser) {
            out.add(node)
        }
        for (i in 0 until node.childCount) {
            flattenNodes(node.getChild(i), out)
        }
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val found = findFirstScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun collectClickableContentItems(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null || out.size >= 25) return
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (node.isVisibleToUser && node.isClickable && rect.height() >= 48 && rect.top > 120) {
            val text = extractFirstReadableText(node)
            if (!text.isNullOrBlank()) {
                out.add(node)
                return
            }
        }
        for (i in 0 until node.childCount) {
            collectClickableContentItems(node.getChild(i), out)
        }
    }

    private fun extractFirstReadableText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        val t = node.text?.toString()?.trim()
        if (!t.isNullOrBlank()) return t
        val d = node.contentDescription?.toString()?.trim()
        if (!d.isNullOrBlank()) return d
        for (i in 0 until node.childCount) {
            val childText = extractFirstReadableText(node.getChild(i))
            if (!childText.isNullOrBlank()) return childText
        }
        return null
    }
}
