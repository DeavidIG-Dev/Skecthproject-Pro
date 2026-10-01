package com.deavidig.skecth.project.utils

import com.deavidig.sketchprojectpro.R

data class ProjectStructure(
	val project: Project,
	val gradles: ArrayList<Gradle>
)

data class Gradle(
	val name: String,
	val `package`: String,
	val icon: Int = R.mipmap.ic_launcher,
	val modules: Module
)

data class Module(
	val name: String,
	val suffixName: String,

	val versionCode: Int,
	val versionName: String,

	val minSDK: Int,
	val targetSDK: Int,
	val maxSDK: Int
)

data class Project(
	val description: String = "",
	val icon: Int = R.mipmap.ic_launcher,

	val language: Language,
	val version: JavaVersion
)

enum class Language {
	Java,
	Kotlin,
	Scala;
}

enum class JavaVersion(val version: String) {
	Java_11("Java 11"),
	Java_17("Java 17"),
	Java_21("Java 21");
}