package com.deavidig.skecth.project.creator.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deavidig.mod.deaniel.appcompat.app.applyWindowInsets
import com.deavidig.skecth.project.creator.fragment.ActivityManagerCreatorGradleViewPagerFragment
import com.deavidig.skecth.project.creator.fragment.ActivityManagerCreatorHomeViewPagerFragment
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorBinding
import com.google.android.material.tabs.TabLayout
import androidx.core.net.toUri

class ProjectCreatorActivity : AppCompatActivity() {
	lateinit var layout_binding: ActivityProjectCreatorBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		layout_binding = ActivityProjectCreatorBinding.inflate(layoutInflater)

		enableEdgeToEdge()
		setContentView(layout_binding.root)
		applyWindowInsets()

		layout_binding.pagerFilter.adapter =
			MainProjectCreatorViewPagerFragmentAdapter(this)
		layout_binding.pagerFilter.isUserInputEnabled = false
		layout_binding.tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			override fun onTabSelected(tab: TabLayout.Tab) {
				layout_binding.bar.getLeftMenu()!![0].isVisible = tab.position == 1

				// layout_binding.bar.setNavigationIcon(if (tab.position == 1) R.drawable.ic_login_24_reverse else R.drawable.ic_arrow_back_24)

				layout_binding.bar.setSubtitle(if (tab.position == 0) "Home" else if (tab.position == 1) "Gradle" else "XML")
				layout_binding.pagerFilter.setCurrentItem(tab.position, false)
			}

			override fun onTabUnselected(tab: TabLayout.Tab) { }
			override fun onTabReselected(tab: TabLayout.Tab) { }
		})

		layout_binding.bar.setNavigationOnClickListener { onBackPressed() }
	}

	private class MainProjectCreatorViewPagerFragmentAdapter(activity: ProjectCreatorActivity) : FragmentStateAdapter(activity) {
		override fun createFragment(position: Int): Fragment {
			return when (position) {
				0 -> ActivityManagerCreatorHomeViewPagerFragment()
				1 -> ActivityManagerCreatorGradleViewPagerFragment()
				else -> throw RuntimeException("The fragment in position $position not founded!")
			}
		}

		override fun getItemCount(): Int = 3
	}
}