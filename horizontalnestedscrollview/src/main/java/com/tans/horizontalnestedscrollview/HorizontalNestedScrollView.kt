package com.tans.horizontalnestedscrollview

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.*
import android.widget.EdgeEffect
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.core.view.ViewCompat
import androidx.core.widget.EdgeEffectCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 *
 * author: pengcheng.tan
 * date: 2020/3/23
 */

class HorizontalNestedScrollView : FrameLayout {

    private val viewConfiguration: ViewConfiguration by lazy { ViewConfiguration.get(context) }
    private val touchSlop: Int by lazy { viewConfiguration.scaledTouchSlop }
    private val minVelocity: Int by lazy { viewConfiguration.scaledMinimumFlingVelocity }
    private val maxVelocity: Int by lazy { viewConfiguration.scaledMaximumFlingVelocity }

    private val scroller: OverScroller by lazy { OverScroller(context) }
    private var velocityTracker: VelocityTracker? = null

    private val edgeGlowStart: EdgeEffect by lazy { EdgeEffect(context) }
    private val edgeGlowEnd: EdgeEffect by lazy { EdgeEffect(context) }
    // TODO: adjust over scroll enable.
    private val overScrollEnable: Boolean = true

    constructor(context: Context) : super(context) {
        initAttrs()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initAttrs()
    }

    constructor(context: Context, attrs: AttributeSet, style: Int) : super(context, attrs, style) {
        initAttrs()
    }

    private fun initAttrs() {
        isClickable = true
        isFocusable = true
        setWillNotDraw(false)
    }

    private var lastTouchX: Int = -1
    private var activePointerId: Int = -1
    private var isBeingDragged = false
    private var lastScrollerX = 0

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // TODO: add intercept logic.
        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        initVelocityTrackerIfNotExists()

        var result = true
        if (event != null) {
            val velocityEvent = MotionEvent.obtain(event)
            // TODO: Do offset
            // velocityEvent.offsetLocation(0f, 0f)

            val actionIndex = event.actionIndex
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {

                    activePointerId = event.getPointerId(actionIndex)
                    lastTouchX = (event.getX(event.findPointerIndex(activePointerId)) + 0.5).toInt()

                    if (!scroller.isFinished) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        abortAnimatedScroll()
                    }

                    if (childCount == 0) {
                        result = false
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    val activeActionIndex = event.findPointerIndex(activePointerId)
                    val x = (event.getX(activeActionIndex) + 0.5).toInt()
                    var dx = lastTouchX - x
                    lastTouchX = x
                    if (!isBeingDragged && abs(dx) > touchSlop) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        isBeingDragged = true
                        if (dx > 0) {
                            dx -= touchSlop
                        } else {
                            dx += touchSlop
                        }
                    }

                    if (isBeingDragged) {
                        // TODO: Do dispatchNestedPreScroll

                        val oldScrollX = scrollX
                        overScrollX(dx, scrollX, getScrollRangeX())
                        if (overScrollEnable) {
                            val pulledToX = oldScrollX + dx
                            if (pulledToX < 0) {
                                EdgeEffectCompat.onPull(edgeGlowStart, dx.toFloat() / height.toFloat(),
                                    event.getY(activeActionIndex) / height.toFloat())
                                if (!edgeGlowEnd.isFinished) {
                                    edgeGlowEnd.onRelease()
                                }
                            } else if (pulledToX > getScrollRangeX()) {
                                EdgeEffectCompat.onPull(edgeGlowEnd, dx.toFloat() / height.toFloat(),
                                    1f - event.getY(activeActionIndex) / height.toFloat())
                                if (!edgeGlowStart.isFinished) {
                                    edgeGlowStart.onRelease()
                                }
                            }

                            if (!edgeGlowEnd.isFinished || !edgeGlowStart.isFinished) {
                                ViewCompat.postInvalidateOnAnimation(this)
                            }
                        }

                    }
                }

                MotionEvent.ACTION_UP -> {
                    val velocityTracker = this.velocityTracker
                    velocityTracker?.computeCurrentVelocity(1000, maxVelocity.toFloat())
                    val velocityX = velocityTracker?.getXVelocity(activePointerId)?.toInt() ?: 0
                    if (abs(velocityX) > minVelocity) {
                        fling(-velocityX)
                    } else if (scroller.springBack(scrollX, scrollY, 0, getScrollRangeX(), 0, 0)) {
                        ViewCompat.postInvalidateOnAnimation(this)
                    }

                    endDrag()
                }

                MotionEvent.ACTION_CANCEL -> {

                    if (isBeingDragged && childCount > 0) {
                        if (scroller.springBack(scrollX, scrollY, 0, getScrollRangeX(), 0, 0)) {
                            ViewCompat.postInvalidateOnAnimation(this)
                        }
                    }

                    endDrag()
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    activePointerId = event.getPointerId(actionIndex)
                    lastTouchX = (event.getX(event.findPointerIndex(activePointerId)) + 0.5).toInt()
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    val currentId = event.getPointerId(actionIndex)
                    if (currentId == activePointerId) {
                        val newIndex = if (actionIndex == 0) 1 else 0
                        activePointerId = event.getPointerId(newIndex)
                        lastTouchX = (event.getX(newIndex) + 0.5).toInt()
                    }
                }
            }
            velocityTracker?.addMovement(velocityEvent)
            velocityEvent.recycle()
        }

        return result
    }

    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        if (canvas != null) {
            if (!edgeGlowStart.isFinished) {
                val restoreCount = canvas.save()
                val width = width.toFloat()
                val height = height.toFloat()
                canvas.rotate(270f)
                canvas.translate(-height, min(0f, scrollX.toFloat()))
                edgeGlowStart.setSize(height.toInt(), width.toInt())
                if (edgeGlowStart.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                canvas.restoreToCount(restoreCount)
            }

            if (!edgeGlowEnd.isFinished) {
                val restoreCount = canvas.save()
                val width = width.toFloat()
                val height = height.toFloat()
                canvas.rotate(90f)
                canvas.translate(0f, -(max(getScrollRangeX().toFloat(), scrollX.toFloat()) + width))
                edgeGlowEnd.setSize(height.toInt(), width.toInt())
                if (edgeGlowEnd.draw(canvas)) {
                    ViewCompat.postInvalidateOnAnimation(this)
                }
                canvas.restoreToCount(restoreCount)
            }
        }
    }

    // TODO: Do Nested Scroll.
    override fun computeScroll() {
        if (scroller.isFinished) {
            return
        }
        scroller.computeScrollOffset()
        val x = scroller.currX
        val dx = x - lastScrollerX
        lastScrollerX = x
        var unconsumed = dx
        if (unconsumed != 0) {
            val scrollXBefore = scrollX
            overScrollX(dx, scrollXBefore, getScrollRangeX())
            val scrollAfter = scrollX
            unconsumed -= scrollAfter - scrollXBefore
        }
        if (unconsumed < 0) {
            if (edgeGlowStart.isFinished) {
                edgeGlowStart.onAbsorb(scroller.currVelocity.toInt())
            }
            abortAnimatedScroll()
        }
        if (unconsumed > 0) {
            if (edgeGlowEnd.isFinished) {
                edgeGlowEnd.onAbsorb(scroller.currVelocity.toInt())
            }
            abortAnimatedScroll()
        }

        if (!scroller.isFinished) {
            ViewCompat.postInvalidateOnAnimation(this)
        }

    }

    override fun computeHorizontalScrollRange(): Int {
        if (childCount > 1) {
            error("HorizontalNestedScrollView only support one child.")
        }
        val parentSpace = width - paddingStart - paddingEnd
        if (childCount == 0) {
            return parentSpace
        }
        val child = getChildAt(0)
        val lp = child.layoutParams as? MarginLayoutParams
        var range = child.right + ((lp?.marginEnd) ?: 0)
        val overScrollRange = max(0, range - parentSpace)
        val currentScrollX = scrollX
        if (currentScrollX < 0) {
            range -= currentScrollX
        } else if (currentScrollX > overScrollRange) {
            range += currentScrollX - overScrollRange
        }

        return range
    }

    fun getScrollRangeX(): Int {
        val child = getChildAt(0)
        val lp = child.layoutParams as MarginLayoutParams
        val childSize = child.width + lp.leftMargin + lp.rightMargin
        val parentSpace = width - paddingLeft - paddingRight
        return max(0, childSize - parentSpace)
    }

    fun overScrollX(deltaX: Int, scrollX: Int, maxRange: Int): Boolean {
        val newScrollX = deltaX + scrollX
        return when {
            maxRange < newScrollX -> {
                scrollTo(maxRange, 0)
                true
            }
            newScrollX < 0 -> {
                scrollTo(0, 0)
                true
            }
            else -> {
                scrollTo(newScrollX, 0)
                false
            }
        }
    }

    fun fling(velocityX: Int) {
        if (childCount > 0) {
            scroller.fling(
                scrollX, scrollY,
                velocityX, 0,
                Int.MIN_VALUE, Int.MAX_VALUE,
                0, 0,
                0, 0
            )
            runAnimatedScroll(true)
        }
    }

    private fun runAnimatedScroll(nestedScrolling: Boolean) {
        // TODO: Deal nested scroll.
        if (nestedScrolling) {

        } else {

        }
        lastScrollerX = scrollX
        ViewCompat.postInvalidateOnAnimation(this)
    }

    private fun endDrag() {
        lastTouchX = -1
        activePointerId = -1
        isBeingDragged = false
        recycleVelocityTracker()
        edgeGlowStart.onRelease()
        edgeGlowEnd.onRelease()
    }

    private fun initOrResetVelocityTracker() {
        val velocityTracker = this.velocityTracker
        if (velocityTracker == null) {
            this.velocityTracker = VelocityTracker.obtain()
        } else {
            velocityTracker.clear()
        }
    }

    private fun initVelocityTrackerIfNotExists() {
        if (velocityTracker == null) {
            this.velocityTracker = VelocityTracker.obtain()
        }
    }

    private fun recycleVelocityTracker() {
        val velocityTracker = this.velocityTracker
        velocityTracker?.recycle()
        this.velocityTracker = null
    }

    private fun abortAnimatedScroll() {
        scroller.abortAnimation()
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams?): ViewGroup.LayoutParams {
        return MarginLayoutParams(lp)
    }

    override fun measureChild(
        child: View?,
        parentWidthMeasureSpec: Int,
        parentHeightMeasureSpec: Int
    ) {
        if (child != null) {
            val lp = child.layoutParams as MarginLayoutParams
            val childWidthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            val childHeightSpec = ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec, paddingTop + paddingBottom, lp.height)
            child.measure(childWidthSpec, childHeightSpec)
        }
    }

    override fun measureChildWithMargins(
        child: View?,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        if (child != null) {
            val lp = child.layoutParams as MarginLayoutParams
            val childWidthSpec = MeasureSpec.makeMeasureSpec(lp.leftMargin + lp.bottomMargin, MeasureSpec.UNSPECIFIED)
            val childHeightSpec = ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec,
                paddingTop + paddingBottom + lp.topMargin + lp.bottomMargin + heightUsed,
                lp.height)
            child.measure(childWidthSpec, childHeightSpec)
        }
    }


}