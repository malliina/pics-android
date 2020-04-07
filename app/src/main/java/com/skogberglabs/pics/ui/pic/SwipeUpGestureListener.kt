package com.skogberglabs.pics.ui.pic

import android.view.GestureDetector
import android.view.MotionEvent

abstract class SwipeUpGestureListener : GestureDetector.OnGestureListener {
    abstract fun onSwipeUp(velocityY: Float)
    override fun onShowPress(e: MotionEvent?) {
    }

    override fun onSingleTapUp(e: MotionEvent?): Boolean = true

    override fun onDown(e: MotionEvent?): Boolean = true

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (velocityY < 0) {
            onSwipeUp(velocityY)
        }
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean = true

    override fun onLongPress(e: MotionEvent?) {
    }
}
