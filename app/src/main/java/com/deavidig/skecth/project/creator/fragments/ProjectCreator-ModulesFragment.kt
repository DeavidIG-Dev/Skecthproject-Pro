package com.deavidig.skecth.project.creator.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.deavidig.sketchprojectpro.databinding.FragmentProjectCreatorModulesBinding

class `ProjectCreator-ModulesFragment`() : Fragment() {
	private lateinit var layout_binding: FragmentProjectCreatorModulesBinding

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = layout_binding.root

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		layout_binding = FragmentProjectCreatorModulesBinding.inflate(layoutInflater)
	}
}