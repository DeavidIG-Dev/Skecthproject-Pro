package com.deavidig.mod.deaniel.viewswipe.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ViewFlipper
import kotlin.math.abs

class ViewSwipe @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : ViewFlipper(context, attrs) {

	private var downX = 0f

	private var listener: OnDisplayedChildChangedListener? = null

	fun interface OnDisplayedChildChangedListener {
		fun onDisplayedChildChanged(oldChild: Int, newChild: Int)
	}

	override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				downX = event.x
			}

			MotionEvent.ACTION_MOVE -> {
				val deltaX = event.x - downX

				if (abs(deltaX) > 100f) {
					return true
				}
			}
		}

		return false
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_UP -> {
				val deltaX = event.x - downX

				when {
					deltaX > 100f -> {
						if (displayedChild >= 1) {
							showPrevious()
						}
					}

					deltaX < -100f -> {
						if (displayedChild < childCount - 1) {
							showNext()
						}
					}
				}
			}
		}

		return true
	}

	override fun setDisplayedChild(whichChild: Int) {
		val oldChild = displayedChild
		super.setDisplayedChild(whichChild)
		this.listener?.onDisplayedChildChanged(oldChild, whichChild)
	}

	fun setOnDisplayedChildChangedListener(listener: OnDisplayedChildChangedListener) {
		this.listener = listener
	}
}