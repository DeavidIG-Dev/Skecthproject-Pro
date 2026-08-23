package com.deavidig.skecth.project.main.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.graphics.drawable.toDrawable
import com.deavidig.mod.deaniel.appcompat.app.getAttributeToColor
import com.deavidig.skecth.project.main.adapter.MainActivityDialogProjectCreatorViewPagerFragmentAdapter
import com.deavidig.sketchprojectpro.databinding.ActivityMainDialogProjectCreatorBinding
import com.google.android.material.tabs.TabLayout

class ProjectManagerDialogFragment : AppCompatDialogFragment() {
	lateinit var layoutBinding: ActivityMainDialogProjectCreatorBinding

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		layoutBinding = ActivityMainDialogProjectCreatorBinding.inflate(inflater)

		layoutBinding.pagerFilter.adapter = MainActivityDialogProjectCreatorViewPagerFragmentAdapter(this)
		layoutBinding.pagerFilter.isUserInputEnabled = false
		layoutBinding.tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			override fun onTabSelected(tab: TabLayout.Tab) {
				layoutBinding.bar.subtitle = if (tab.position == 0) "Home" else if (tab.position == 1) "Gradle" else "XML"
				layoutBinding.pagerFilter.setCurrentItem(tab.position, false)
			}

			override fun onTabUnselected(tab: TabLayout.Tab) { }
			override fun onTabReselected(tab: TabLayout.Tab) { }
		})

		layoutBinding.bar.setNavigationOnClickListener {  }

		return layoutBinding.root
	}

	override fun onStart() {
		super.onStart()
		dialog?.window?.let { window ->
			window.setLayout(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
			window.setBackgroundDrawable(getAttributeToColor(com.google.android.material.R.attr.colorSurface).toDrawable())
		}
	}
}