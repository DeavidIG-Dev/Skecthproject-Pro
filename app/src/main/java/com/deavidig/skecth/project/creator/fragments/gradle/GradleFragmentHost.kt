package com.deavidig.skecth.project.creator.fragments.gradle

import android.content.res.Resources
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorFrontGradleBinding

interface GradleFragmentHost {
	var layout_binding: ActivityProjectCreatorFrontGradleBinding // fake-base

	fun getResources(): Resources // fake-base
}