package com.deavidig.skecth.project.main.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.deavidig.skecth.project.utils.ProjectStructure
import com.deavidig.sketchprojectpro.databinding.ActivityMainRecyclerviewProjectBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class `MainActivity-ProjectAdapter`(val projectList: ArrayList<ProjectStructure>) :
	RecyclerView.Adapter<`MainActivity-ProjectAdapter`.ViewHolder>() {
	override fun onCreateViewHolder(
		parent: ViewGroup,
		type: Int
	): ViewHolder =
		ViewHolder(
			ActivityMainRecyclerviewProjectBinding.inflate(LayoutInflater.from(parent.context))
				.apply {
					root.layoutParams = ViewGroup.MarginLayoutParams(-1, -2).apply {
						setMargins(0, 8, 8, 0)
					}
				}
		)

	override fun onBindViewHolder(
		holder: ViewHolder,
		position: Int
	) {

		holder.layout_binding.applicationIcon.setBackgroundResource(projectList[position].gradles[0].icon)
		holder.layout_binding.applicationTitle.text = projectList[position].gradles[0].name
		holder.layout_binding.applicationPackage.text =
			"${projectList[position].gradles[0].`package`} - ${projectList[position].gradles[0].modules.versionCode} (${projectList[position].gradles[0].modules.versionName} ${projectList[position].gradles[0].modules.suffixName})"

		holder.layout_binding.badgeModule.text =
			if (projectList[position].gradles[0].modules.name.length >= 6) projectList[position].gradles[0].modules.name.substring(
				5
			) + "..." else projectList[position].gradles[0].modules.name
		holder.layout_binding.badgeLanguage.text = projectList[position].project.language.name

		if (!projectList[position].project.description.isEmpty()) {
			holder.layout_binding.projectDivider.visibility = View.VISIBLE
			holder.layout_binding.projectDescription.visibility = View.VISIBLE
			holder.layout_binding.projectDescription.text =
				projectList[position].project.description
		}

		if (projectList[position].project.icon != 0) {
			holder.layout_binding.projectIcon.visibility = View.VISIBLE
			holder.layout_binding.projectIcon.setBackgroundResource(projectList[position].project.icon)
		}

		holder.layout_binding.badgeModule.setOnClickListener {
			if (projectList[position].gradles.size >= 2) {
				MaterialAlertDialogBuilder(holder.layout_binding.root.context).setTitle("Select At Module")
					.setItems(projectList[position].gradles.map { it.modules.name }
						.toTypedArray()) { _, which ->
						holder.layout_binding.badgeModule.text =
							if (projectList[position].gradles[which].modules.name.length >= 6) projectList[position].gradles[which].modules.name.substring(
								startIndex = 0,
								endIndex = 4
							) + "..." else projectList[position].gradles[which].modules.name
						holder.layout_binding.applicationPackage.text =
							"${projectList[position].gradles[which].`package`} - ${projectList[position].gradles[which].modules.versionCode} (${projectList[position].gradles[which].modules.versionName} ${projectList[position].gradles[which].modules.suffixName})"
					}
					.setNegativeButton("Cancel", null)
					.show()
			}
		}

		holder.layout_binding.root.setOnClickListener {
			if (projectList[position].gradles.size >= 2) {
				MaterialAlertDialogBuilder(holder.layout_binding.root.context).setTitle("Select At Module")
					.setItems(projectList[position].gradles.map { it.modules.name }
						.toTypedArray()) { _, _ -> }
					.setNegativeButton("Cancel", null)
					.show()
			}
		}
	}

	override fun getItemCount(): Int = projectList.size

	class ViewHolder(val layout_binding: ActivityMainRecyclerviewProjectBinding) :
		RecyclerView.ViewHolder(layout_binding.root)
}