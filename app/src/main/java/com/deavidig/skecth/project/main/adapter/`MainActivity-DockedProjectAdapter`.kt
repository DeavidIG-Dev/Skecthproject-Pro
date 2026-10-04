package com.deavidig.skecth.project.main.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.deavidig.skecth.project.utils.ProjectStructure
import com.deavidig.sketchprojectpro.databinding.ActivityMainComponentdockedsearchbarRecyclerviewProjectBinding

class `MainActivity-DockedProjectAdapter`(
	val projectList: ArrayList<ProjectStructure>,
	val state: (String) -> Unit = { _ -> }
) :
	RecyclerView.Adapter<`MainActivity-DockedProjectAdapter`.ViewHolder>() {
	override fun onCreateViewHolder(
		parent: ViewGroup,
		type: Int
	): ViewHolder =
		ViewHolder(
			ActivityMainComponentdockedsearchbarRecyclerviewProjectBinding.inflate(
				LayoutInflater.from(
					parent.context
				)
			)
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

		holder.layout_binding.applicationTitle.text = projectList[position].gradles[0].name
		holder.layout_binding.applicationPackage.text =
			"${projectList[position].gradles[0].`package`} - ${projectList[position].gradles[0].modules.versionCode} (${projectList[position].gradles[0].modules.versionName} ${projectList[position].gradles[0].modules.suffixName})"

		holder.layout_binding.badgeModule.text = projectList[position].project.language.name

		holder.layout_binding.root.setOnClickListener {
			state(holder.layout_binding.applicationTitle.text.toString())
		}
	}

	override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
		super.onDetachedFromRecyclerView(recyclerView)
	}


	override fun getItemCount(): Int = projectList.size

	class ViewHolder(val layout_binding: ActivityMainComponentdockedsearchbarRecyclerviewProjectBinding) :
		RecyclerView.ViewHolder(layout_binding.root)
}