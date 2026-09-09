package com.deavidig.mod.deanielig.tablayout.widget

import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.LinearLayout
import com.google.android.material.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.Tab

val Tab.isLastTab: Boolean get() = parent?.let { position == it.tabCount - 1 } == true
val Tab.isFirstTab: Boolean get(): Boolean = this.position == 0

fun TabLayout.selectTabAt(index: Int, updateIndicator: Boolean = true) {
	selectTab(getTabAt(index), updateIndicator)
}

fun TabLayout.setDividerInTabs(updateDivider: Boolean, widthDivider: Int = 4) {
	val tabStrip = getChildAt(0) as LinearLayout
	tabStrip.showDividers = if (updateDivider) LinearLayout.SHOW_DIVIDER_MIDDLE else LinearLayout.SHOW_DIVIDER_NONE
	tabStrip.dividerDrawable = GradientDrawable().apply {
		val typed = TypedValue()
		context?.theme?.resolveAttribute(R.attr.colorSurfaceContainerHighest, typed, true)
		setColor(typed.data)
		setSize(widthDivider, 20)
	}
}