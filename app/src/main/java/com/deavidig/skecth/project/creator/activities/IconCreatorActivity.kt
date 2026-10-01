package com.deavidig.skecth.project.creator.activities

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import com.deavidig.mod.deanielig.appcompat.app.ComponentAppCompatActivity
import com.deavidig.sketchprojectpro.databinding.ActivityIconCreatorBinding

class IconCreatorActivity() : ComponentAppCompatActivity() {
	private lateinit var layout_binding: ActivityIconCreatorBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		layout_binding = ActivityIconCreatorBinding.inflate(layoutInflater)

		enableEdgeToEdge()
		setContentView(layout_binding)
		applyWindowInsets()
	}
}