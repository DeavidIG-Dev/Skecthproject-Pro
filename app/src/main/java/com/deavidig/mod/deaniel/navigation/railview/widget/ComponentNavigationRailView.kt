package com.deavidig.mod.deaniel.navigation.railview.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.navigationrail.NavigationRailView

class ComponentNavigationRailView(context: Context, attrs: AttributeSet) : NavigationRailView(context,attrs) {
	override fun getCollapsedMaxItemCount(): Int  = Int.MAX_VALUE
}