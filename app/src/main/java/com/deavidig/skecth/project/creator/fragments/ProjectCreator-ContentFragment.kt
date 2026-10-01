package com.deavidig.skecth.project.creator.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.ArraySet
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.deavidig.mod.deanielig.badge.widget.ComponentBadge
import com.deavidig.mod.deanielig.kotlin.toArrayList
import com.deavidig.mod.deanielig.tooltip.widget.ComponentTooltip
import com.deavidig.skecth.project.creator.activities.ProjectModulesCreatorActivity
import com.deavidig.skecth.project.utils.FileUtil
import com.deavidig.sketchprojectpro.databinding.FragmentProjectCreatorContentBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class `ProjectCreator-ContentFragment`() : Fragment() {
	private val gson = Gson()

	private lateinit var layout_binding: FragmentProjectCreatorContentBinding

	private lateinit var vModulesList: ArrayList<String>

	private var vFirstTimeToViewProjectIcon: Boolean = true
	private var vFirstTimeToEmptyPackage: Boolean = true

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		layout_binding = FragmentProjectCreatorContentBinding.inflate(layoutInflater)

		vModulesList = ArraySet(
			gson.fromJson<ArrayList<String>>(
				FileUtil.readFile(
					FileUtil.externalStorageDir +
							FileUtil.separator +
							"Sketchproject Pro" +
							FileUtil.separator +
							"prefix.json"
				),
				object : TypeToken<ArrayList<String>>() {}.type
			)
		).toArrayList()
	}

	override fun onStart() {
		super.onStart()

		layout_binding.applicationName.getEditText()!!.doOnTextChanged { text, _, _, _ ->
			val text: String = text!!.toString()

			layout_binding.applicationPackage.getEditText()!!
				.setText(
					text.trim()
						.replace(' ', '.')
						.replace("(?<=[a-z0-9])(?=[A-Z])".toRegex(), "_")
						.lowercase()
				)
		}

		layout_binding.applicationIcon.setOnLongClickListener {
			val isCurrentlyVisible = layout_binding.projectIcon.isVisible

			val vTooltipApplication: ComponentTooltip =
				ComponentTooltip(layout_binding.applicationIcon).setMessage("Icon for the Application")
					.setCancelable(false)
					.setDismissAfter(1000L)
			val vTooltipProject: ComponentTooltip =
				ComponentTooltip(layout_binding.projectIcon).setMessage("Icon for the Project")
					.setCancelable(false)
					.setDismissAfter(1000L)

			if (isCurrentlyVisible) {

				layout_binding.applicationIcon.animate()
					.translationX(0f)
					.setDuration(300)
					.start()

				layout_binding.projectIcon.animate()
					.alpha(0f)
					.translationX(0f)
					.setDuration(300)
					.withEndAction {
						layout_binding.projectIcon.isVisible = false
					}
					.start()

				layout_binding.descriptionProject.apply {
					pivotX = 0f
					pivotY = 0f

					animate()
						.alpha(0f)
						.scaleY(0f)
						.setDuration(300)
						.withEndAction {
							isVisible = false
							clearFocus()
							getEditText()!!.setText("")
						}
						.start()
				}

			} else {

				layout_binding.applicationIcon.animate()
					.translationX(-(layout_binding.root.width / 4f))
					.setDuration(300)
					.withEndAction {
						if (vFirstTimeToViewProjectIcon) {
							vTooltipApplication.show()
						}
					}
					.start()

				layout_binding.projectIcon.apply {
					alpha = 0f
					isVisible = true

					animate()
						.alpha(1f)
						.translationX(layout_binding.root.width / 4f)
						.setDuration(300)
						.withEndAction {
							if (vFirstTimeToViewProjectIcon) {
								vTooltipProject.show()
							}

							vFirstTimeToViewProjectIcon = false
						}
						.start()
				}

				layout_binding.descriptionProject.apply {
					pivotX = 0f
					pivotY = 0f

					alpha = 0f
					scaleY = 0f
					isVisible = true

					animate()
						.alpha(1f)
						.scaleY(1f)
						.setDuration(300)
						.start()
				}
			}

			true
		}

		layout_binding.applicationPackage.setPrefixTextList(vModulesList)
		layout_binding.applicationPackage.setPrefixEnabled(true)
		layout_binding.applicationPackage.getEditText()!!.doOnTextChanged { text, _, _, _ ->
			val text: String = text!!.toString()

			if (text.isEmpty() && vFirstTimeToEmptyPackage && layout_binding.applicationPackage.isPrefixEnabled()) {
				ComponentTooltip(layout_binding.applicationPackage).setMessage("It is not recommended to empty the package.")
					.show()
				vFirstTimeToEmptyPackage = false
			} else if (text.isEmpty() && !layout_binding.applicationPackage.isPrefixEnabled()) {
				layout_binding.applicationPackage.setErrorEnabled(true)
				layout_binding.applicationPackage.setErrorText("Cannot full empty the package, please, set a namespace valid.")
			}
		}

		layout_binding.applicationPackage.setEndLayoutOnClickListener {
			startActivity(
				Intent(
					context,
					ProjectModulesCreatorActivity::class.java
				)
			)
		}

		layout_binding.applicationPackage.setStartImageOnClickListener {
			layout_binding.applicationPackage.setPrefixEnabled(!layout_binding.applicationPackage.isPrefixEnabled())
			if (layout_binding.applicationPackage.isErrorEnabled() && layout_binding.applicationPackage.isPrefixEnabled())
				layout_binding.applicationPackage.setErrorEnabled(false)
			if (!layout_binding.applicationPackage.isPrefixEnabled() && layout_binding.applicationPackage.getEditText()!!.text.isEmpty()) {
				layout_binding.applicationPackage.setErrorEnabled(true)
				layout_binding.applicationPackage.setErrorText("Cannot full empty the package, please, set a namespace valid.")
			}
		}

		ComponentBadge(this).setText("Experimental").show(layout_binding.languageOptionScala)
	}

	override fun onDestroyView() {
		super.onDestroyView()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = layout_binding.root
}