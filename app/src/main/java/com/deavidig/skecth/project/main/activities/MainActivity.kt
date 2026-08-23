package com.deavidig.skecth.project.main.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.deavidig.mod.deaniel.appcompat.app.applyWindowInsets
import com.deavidig.skecth.project.main.dialogs.ProjectManagerDialogFragment
import com.deavidig.sketchprojectpro.databinding.ActivityMainBinding
import com.deavidig.sketchprojectpro.databinding.ActivityMainBottomsheetFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {
	private lateinit var layoutBinding: ActivityMainBinding
	private lateinit var renameBottomSheetFilterBinding: ActivityMainBottomsheetFilterBinding
	private lateinit var newTabBottomSheetFilterBinding: ActivityMainBottomsheetFilterBinding

	private lateinit var renameBottomSheetDialog: BottomSheetDialog
	private lateinit var newTabBottomSheetDialog: BottomSheetDialog

	private val projectManager = ProjectManagerDialogFragment()

	@SuppressLint("RestrictedApi")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		DynamicColors.applyToActivityIfAvailable(this)

		bindings()

		enableEdgeToEdge()
		setContentView(layoutBinding.root)
		applyWindowInsets()
		setupTabLayout()
	}

	private fun bindings() {
		layoutBinding = ActivityMainBinding.inflate(layoutInflater)
		renameBottomSheetFilterBinding = ActivityMainBottomsheetFilterBinding.inflate(layoutInflater)
		newTabBottomSheetFilterBinding = ActivityMainBottomsheetFilterBinding.inflate(layoutInflater)

		renameBottomSheetDialog = BottomSheetDialog(this)
		renameBottomSheetDialog.setContentView(renameBottomSheetFilterBinding.root)

		newTabBottomSheetDialog = BottomSheetDialog(this)
		newTabBottomSheetDialog.setContentView(newTabBottomSheetFilterBinding.root)
		newTabBottomSheetFilterBinding.nameFilterContainer.hint = "Name Filter"

		setupBottomSheetListeners()
		setupFabListeners()
	}

	private fun setupFabListeners() {
		layoutBinding.projectCreator.setOnClickListener {
			projectManager.show(supportFragmentManager, "");
		}
	}

	private fun setupBottomSheetListeners() {
		newTabBottomSheetFilterBinding.acceptButton.setOnClickListener {
			val newName = newTabBottomSheetFilterBinding.nameFilter.text?.toString()?.trim()!!

			if (!newName.isEmpty() && newName.length <= newTabBottomSheetFilterBinding.nameFilterContainer.counterMaxLength) {
				newTabBottomSheetFilterBinding.nameFilterContainer.isErrorEnabled = false

				val newTab = layoutBinding.tabFilter.newTab().apply { text = newName }
				val lastIndex = layoutBinding.tabFilter.tabCount - 1

				layoutBinding.tabFilter.addTab(newTab, lastIndex)

				newTabBottomSheetFilterBinding.nameFilter.setText("")
				newTabBottomSheetDialog.dismiss()
			} else if (newName.length > newTabBottomSheetFilterBinding.nameFilterContainer.counterMaxLength) {
				newTabBottomSheetFilterBinding.nameFilter.setText(newTabBottomSheetFilterBinding.nameFilter.text!!.subSequence(0, newTabBottomSheetFilterBinding.nameFilterContainer.counterMaxLength))
				newTabBottomSheetFilterBinding.nameFilter.setSelection(newTabBottomSheetFilterBinding.nameFilterContainer.counterMaxLength)
			} else {
				newTabBottomSheetFilterBinding.nameFilterContainer.isErrorEnabled = true
				newTabBottomSheetFilterBinding.nameFilterContainer.error = "Name can't be empty for the Filter."
			}
		}

		newTabBottomSheetFilterBinding.cancelButton.setOnClickListener {
			newTabBottomSheetDialog.dismiss()
		}

		renameBottomSheetFilterBinding.cancelButton.setOnClickListener {
			renameBottomSheetDialog.dismiss()
		}
	}

	private fun setupTabLayout() {
		layoutBinding.tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			private lateinit var oldTab: TabLayout.Tab

			override fun onTabSelected(tab: TabLayout.Tab) {
				val lastIndex = layoutBinding.tabFilter.tabCount - 1

				if (tab.position == lastIndex) {
					oldTab.select()

					newTabBottomSheetFilterBinding.nameFilterContainer.setErrorEnabled(false)
					newTabBottomSheetFilterBinding.nameFilter.setText("")
					newTabBottomSheetDialog.show()
					return
				}

				oldTab = tab
			}

			override fun onTabUnselected(tab: TabLayout.Tab) {
				val lastIndex = layoutBinding.tabFilter.tabCount - 1
				if (tab.position != lastIndex) {
					this.oldTab = tab
				}
			}

			override fun onTabReselected(tab: TabLayout.Tab) {
				val lastIndex = layoutBinding.tabFilter.tabCount - 1

				if (tab.position == 0) {
					Toast.makeText(this@MainActivity, "You can't modify the Main Tab", Toast.LENGTH_SHORT).show()
					return
				} else if (tab.position == lastIndex) {
					newTabBottomSheetDialog.show()
					return
				}

				renameBottomSheetFilterBinding.nameFilter.setText(tab.text)
				renameBottomSheetDialog.show()

				renameBottomSheetFilterBinding.acceptButton.setOnClickListener {
					val newName = renameBottomSheetFilterBinding.nameFilter.text?.toString()?.trim()

					if (newName.isNullOrEmpty()) {
						MaterialAlertDialogBuilder(this@MainActivity)
							.setTitle("Delete tab?")
							.setMessage("Leaving the name empty will delete this tab.")
							.setPositiveButton("Delete") { _, _ ->
								layoutBinding.tabFilter.removeTab(tab)
								renameBottomSheetDialog.dismiss()
							}
							.setNegativeButton("Cancel", null)
							.show()
					} else {
						tab.text = newName
						renameBottomSheetDialog.dismiss()
					}
				}
			}
		})
	}
}