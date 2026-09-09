package com.deavidig.mod.deanielig.appcompat.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.deavidig.skecth.project.utils.FileUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.edit

open class ComponentPermissionAppCompatActivity :
	ComponentAppCompatActivity() {

	companion object {
		private const val REQUEST_STORAGE_PERMISSION = 100
	}

	private var awaitingManageStorageResult = false
	private var accessFile = false
	private lateinit var sharedPreferences: SharedPreferences

	private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->

		if (uri == null) {
			onStoragePermissionDenied()
			return@registerForActivityResult
		}

		try {
			contentResolver.takePersistableUriPermission(
				uri,
				Intent.FLAG_GRANT_READ_URI_PERMISSION or
						Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			)

			FileUtil.saveTreeUri(this, uri)

			onStoragePermissionGranted()

		} catch (_: SecurityException) {

			onStoragePermissionDenied()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		sharedPreferences = getSharedPreferences("FileAccess", MODE_PRIVATE)

		accessFile = sharedPreferences.getBoolean("AccessFile-Settings", false)

		if (!accessFile) {
			checkRuntimePermission()
		}
	}

	override fun onResume() {
		super.onResume()

		if (awaitingManageStorageResult) {
			awaitingManageStorageResult = false
			checkManageExternalStorage()
		}
	}

	private fun checkRuntimePermission() {

		when {
			Build.VERSION.SDK_INT in
					Build.VERSION_CODES.M..Build.VERSION_CODES.P -> {
				checkLegacyStoragePermission()
			}

			Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
				checkAndroid10Storage()
			}

			Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
				checkManageExternalStorage()
			}
		}
	}

	private fun checkLegacyStoragePermission() {
		val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
		val missingPermissions = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

		if (missingPermissions.isNotEmpty()) {
			ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_STORAGE_PERMISSION)
		} else {
			onStoragePermissionGranted()
		}
	}

	private fun checkAndroid10Storage() {
		val savedUri = FileUtil.getSavedTreeUri(this)

		if (savedUri != null && FileUtil.hasPersistedPermission(this, savedUri)) {
			onStoragePermissionGranted()
		} else {
			openDocumentTreeLauncher.launch(null)
		}
	}

	private fun checkManageExternalStorage() {
		if (Environment.isExternalStorageManager()) {
			onStoragePermissionGranted()
		} else {
			awaitingManageStorageResult = true

			try {
				val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:$packageName") }
				startActivity(intent)
			} catch (_: Exception) {
				try {
					val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
					startActivity(intent)
				} catch (_: Exception) {
					awaitingManageStorageResult = false
					onStoragePermissionDenied()
				}
			}
		}
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)

		if (requestCode != REQUEST_STORAGE_PERMISSION) {
			return
		}

		val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

		if (granted) {
			onStoragePermissionGranted()
		} else {
			onStoragePermissionDenied()
		}
	}

	private fun onStoragePermissionGranted() {
		accessFile = true
		sharedPreferences.edit { putBoolean("AccessFile-Settings", accessFile) }
		MaterialAlertDialogBuilder(this)
			.setTitle("Access Granted")
			.setMessage("Storage access has been granted successfully.")
			.setPositiveButton("OK", null)
			.show()
	}

	private fun onStoragePermissionDenied() {
		accessFile = false
		sharedPreferences.edit { putBoolean("AccessFile-Settings", accessFile) }
		MaterialAlertDialogBuilder(this)
			.setTitle("Requirement Canceled")
			.setMessage(
				"Storage access is required for this operation. " +
						"Without this permission, the application cannot access the required files."
			)
			.setNegativeButton("Cancel", null)
			.setPositiveButton("Try Again") { _, _ ->
				checkRuntimePermission()
			}
			.show()
	}
}