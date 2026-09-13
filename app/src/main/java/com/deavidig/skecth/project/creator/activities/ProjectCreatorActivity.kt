package com.deavidig.skecth.project.creator.activities

import android.animation.ValueAnimator
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.deavidig.mod.deanielig.appcompat.app.ComponentAppCompatActivity
import com.deavidig.mod.deanielig.backdrop.widget.ComponentBackdropAdapter
import com.deavidig.skecth.project.creator.fragments.ContentFragment
import com.deavidig.skecth.project.creator.fragments.ModulesFragment
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProjectCreatorActivity : ComponentAppCompatActivity() {
	lateinit var layout_binding: ActivityProjectCreatorBinding

	private val animation: ValueAnimator = ValueAnimator()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		enableEdgeToEdge()
		layout_binding = ActivityProjectCreatorBinding.inflate(layoutInflater)
		setContentView(layout_binding.root)
		applyWindowInsets()

		layout_binding.bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

		layout_binding.bar.setOnLeftItemMenuClickListener {
			if (it.itemId == R.id.menu_item) {
				layout_binding.backdrop.toggle()
			}
		}

		layout_binding.bar.setOnRightItemMenuClickListener {
			MaterialAlertDialogBuilder(this@ProjectCreatorActivity)
				.setTitle("Save Progress?")
				.setMessage("Are you sure you want to save your progress and finish creating your application?")
				.setPositiveButton("Yes") { _, _ -> savedProjectInJSON() }
				.setNegativeButton("Cancel", null)
				.show()
		}

		layout_binding.backdrop.adapter = ComponentBackDropFragment(this)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (isEnabled) {
					MaterialAlertDialogBuilder(this@ProjectCreatorActivity)
						.setTitle("Exit Project?")
						.setMessage("Are you sure you want to exit and cancel creating your new project?")
						.setNegativeButton("Cancel", null)
						.setPositiveButton("Exit") { _, _ ->
							isEnabled = false; onBackPressedDispatcher.onBackPressed()
						}
						.show()
				}
			}
		})
	}

	private fun savedProjectInJSON() {
		finish()
	}

	class ComponentBackDropFragment(activity: AppCompatActivity) :
		ComponentBackdropAdapter.Companion.ComponentBaseBackdropAdapter(activity) {
		override fun createBackFragment(): Fragment = ModulesFragment()

		override fun createFrontFragment(): Fragment = ContentFragment()
	}
}