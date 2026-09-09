package com.deavidig.skecth.project.utils

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.net.URLConnection
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.LinkedList
import java.util.Locale
import java.util.zip.ZipInputStream


@Suppress("unused")
object FileUtil {
	val separator = File.separator

	fun getFileSize(file: File?): Long {
		if (file == null || !file.exists()) {
			return 0
		}

		if (!file.isDirectory()) {
			return file.length()
		}

		val dirs: MutableList<File> = LinkedList<File>()
		dirs.add(file)

		var result: Long = 0
		while (!dirs.isEmpty()) {
			val dir = dirs.removeAt(0)
			if (!dir.exists()) continue
			val listFiles = dir.listFiles()
			if (listFiles == null) continue
			for (child in listFiles) {
				if (child.isDirectory()) {
					dirs.add(child)
				} else {
					result += child.length()
				}
			}
		}

		return result
	}

	@JvmOverloads
	fun formatFileSize(size: Long, removeZero: Boolean = false): String {
		val units = arrayOf<String?>("B", "KiB", "MiB", "GiB")
		var value = size.toFloat()
		var unitIndex = 0

		while (value >= 1024 && unitIndex < units.size - 1) {
			value /= 1024f
			unitIndex++
		}

		return if (removeZero && (value - value.toInt()) * 10 == 0f) {
			String.format("%d %s", value.toInt(), units[unitIndex])
		} else {
			String.format("%.1f %s", value, units[unitIndex])
		}
	}

	fun renameFile(str: String, str2: String): Boolean {
		return File(str).renameTo(File(str2))
	}

	/**
	 * @return A filename without its extension,
	 * e.g. "FileUtil" for "FileUtil.java", or "FileUtil" for "/sdcard/Documents/FileUtil.java"
	 */
	fun getFileNameNoExtension(filePath: String): String {
		if (filePath.trim { it <= ' ' }.isEmpty()) return ""

		val lastPos = filePath.lastIndexOf('.')
		val lastSep = filePath.lastIndexOf(File.separator)

		if (lastSep == -1) {
			return (if (lastPos == -1) filePath else filePath.substring(0, lastPos))
		} else if (lastPos == -1 || lastSep > lastPos) {
			return filePath.substring(lastSep + 1)
		}
		return filePath.substring(lastSep + 1, lastPos)
	}

	/**
	 * @return A file's filename extension,
	 * e.g. "java" for "/sdcard/Documents/FileUtil.java", but "" for "/sdcard/Documents/fileWithoutExtension"
	 */
	fun getFileExtension(filePath: String): String {
		if (filePath.isEmpty()) return ""

		val last = filePath.lastIndexOf('.')
		val lastSep = filePath.lastIndexOf(File.separator)

		if (last == -1 || lastSep >= last) return ""
		return filePath.substring(last + 1)
	}

	fun createNewFileIfNotPresent(path: String) {
		val lastSep = path.lastIndexOf(File.separator)
		if (lastSep > 0) {
			val dirPath = path.substring(0, lastSep)
			makeDir(dirPath)
		}

		val file = File(path)

		try {
			if (!file.exists()) file.createNewFile()
		} catch (e: IOException) {
			e.printStackTrace()
		}
	}

	fun readFile(path: String): String {
		createNewFileIfNotPresent(path)

		val sb = StringBuilder()
		try {
			FileReader(path).use { fr ->
				val buff = CharArray(1024)
				var length: Int
				while ((fr.read(buff).also { length = it }) > 0) {
					sb.append(String(buff, 0, length))
				}
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}

		return sb.toString()
	}

	fun readFileIfExist(path: String): String {
		val sb = StringBuilder()
		try {
			FileReader(path).use { fr ->
				val buff = CharArray(1024)
				var length: Int
				while ((fr.read(buff).also { length = it }) > 0) {
					sb.append(String(buff, 0, length))
				}
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}

		return sb.toString()
	}

	fun writeFile(path: String, str: String) {
		createNewFileIfNotPresent(path)

		try {
			FileWriter(path, false).use { fileWriter ->
				fileWriter.write(str)
				fileWriter.flush()
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}
	}

	fun copyFile(sourcePath: String, destPath: String) {
		if (!isExistFile(sourcePath)) return
		createNewFileIfNotPresent(destPath)

		try {
			FileInputStream(sourcePath).use { fis ->
				FileOutputStream(destPath, false).use { fos ->
					val buffer = ByteArray(1024)
					var length: Int
					while ((fis.read(buffer).also { length = it }) > 0) {
						fos.write(buffer, 0, length)
					}
				}
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}
	}

	/**
	 * Copies an entire directory, recursively.
	 *
	 * @param source   The directory whose contents to copy.
	 * @param copyInto The directory to copy files into.
	 * @throws IOException Thrown when something goes wrong while copying.
	 */
	@Throws(IOException::class)
	fun copyDirectory(source: File, copyInto: File) {
		if (!source.isDirectory()) {
			val parentFile = copyInto.getParentFile()
			if (parentFile == null || parentFile.exists() || parentFile.mkdirs()) {
				try {
					FileInputStream(source).use { fileInputStream ->
						FileOutputStream(copyInto).use { fileOutputStream ->
							val bArr = ByteArray(2048)
							while (true) {
								val read = fileInputStream.read(bArr)
								if (read <= 0) {
									return
								}
								fileOutputStream.write(bArr, 0, read)
							}
						}
					}
				} catch (e: IOException) {
					Log.e(
						"FileUtil",
						"Error copying file " + source.getAbsolutePath() + " to " + copyInto.getAbsolutePath(),
						e
					)
					throw e
				}
			} else {
				throw IOException("Cannot create dir " + parentFile.getAbsolutePath())
			}
		} else if (copyInto.exists() || copyInto.mkdirs()) {
			val list = source.list()
			if (list != null) {
				for (s in list) {
					copyDirectory(File(source, s), File(copyInto, s))
				}
			}
		} else {
			throw IOException("Cannot create dir " + copyInto.getAbsolutePath())
		}
	}

	@Throws(IOException::class)
	fun extractFileFromZip(inputStream: InputStream, file: File) {
		FileOutputStream(file).use { outputStream ->
			val bArr = ByteArray(1024)
			while (true) {
				val read = inputStream.read(bArr)
				if (read > 0) {
					outputStream.write(bArr, 0, read)
				} else {
					return
				}
			}
		}
	}

	fun moveFile(sourcePath: String, destPath: String) {
		copyFile(sourcePath, destPath)
		deleteFile(sourcePath)
	}

	fun deleteFile(path: String) {
		val file = File(path)

		if (!file.exists()) return

		if (file.isFile()) {
			if (!file.delete()) {
				Log.e("FileUtil", "Failed to delete file: " + file.getAbsolutePath())
			}
			return
		}

		val fileArr = file.listFiles()

		if (fileArr != null) {
			for (subFile in fileArr) {
				if (subFile.isDirectory()) {
					deleteFile(subFile.getAbsolutePath())
				}

				if (subFile.isFile()) {
					subFile.delete()
				}
			}
		}

		if (!file.delete()) {
			Log.e("FileUtil", "Failed to delete directory: " + file.getAbsolutePath())
		}
	}

	fun isExistFile(path: String): Boolean {
		return File(path).exists()
	}

	fun makeDir(path: String) {
		if (!isExistFile(path)) {
			try {
				File(path).mkdirs()
			} catch (e: SecurityException) {
				Log.e("FileUtil", "Error creating directory: " + path, e)
			}
		}
	}

	fun listDir(path: String, list: ArrayList<String>?) {
		var listFiles: Array<File> = arrayOf()
		val dir = File(path)
		if (dir.exists() && !dir.isFile() && (dir.listFiles()
				.also { listFiles = it }) != null && listFiles.size > 0 && list != null
		) {
			list.clear()
			for (file in listFiles) {
				list.add(file.getAbsolutePath())
			}
		}
	}

	fun listDirAsFile(path: String, list: ArrayList<File?>?) {
		var listFiles: Array<File> = arrayOf()
		val dir = File(path)
		if (dir.exists() && !dir.isFile() && (dir.listFiles()
				.also { listFiles = it }) != null && listFiles!!.size > 0 && list != null
		) {
			list.clear()
			Collections.addAll<File?>(list, *listFiles)
		}
	}

	/**
	 * @return List of files that have the filename extension `extension`.
	 */
	fun listFiles(dir: String, extension: String): ArrayList<String?> {
		val list = ArrayList<String?>()
		val files = ArrayList<String>()
		listDir(dir, files)
		for (str in files) {
			if (str.endsWith(extension) && isFile(str)) {
				list.add(str)
			}
		}
		return list
	}

	fun listFilesRecursively(
		directory: File,
		optionalFilenameExtension: String?
	): MutableList<File?> {
		val files: MutableList<File?> = LinkedList<File?>()

		val directoryFiles = directory.listFiles()
		if (directoryFiles != null) {
			for (file in directoryFiles) {
				if (file.isFile()) {
					if (optionalFilenameExtension != null && file.getName()
							.endsWith(optionalFilenameExtension)
					) {
						files.add(file)
					}
				} else {
					files.addAll(listFilesRecursively(file, optionalFilenameExtension))
				}
			}
		}

		return files
	}

	fun isDirectory(path: String): Boolean {
		if (!isExistFile(path)) {
			return false
		}
		return File(path).isDirectory()
	}

	fun isFile(path: String): Boolean {
		if (!isExistFile(path)) {
			return false
		}
		return File(path).isFile()
	}

	fun getFileLength(path: String): Long {
		if (!isExistFile(path)) {
			return 0
		}
		return File(path).length()
	}

	val externalStorageDir: String
		get() = Environment.getExternalStorageDirectory().absolutePath

	fun getPackageDataDir(context: Context): String {
		return context.getExternalFilesDir(null)!!.absolutePath
	}

	fun getPublicDir(type: String?): String {
		return Environment.getExternalStoragePublicDirectory(type).absolutePath
	}

	fun convertUriToFilePath(context: Context, uri: Uri): String? {
		var path: String? = null
		if (DocumentsContract.isDocumentUri(context, uri)) {
			if (isExternalStorageDocument(uri)) {
				val docId = DocumentsContract.getDocumentId(uri)
				val split: Array<String?> =
					docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
				val type = split[0]

				if ("primary".equals(type, ignoreCase = true)) {
					path = Environment.getExternalStorageDirectory().toString() + "/" + split[1]
				}
			} else if (isDownloadsDocument(uri)) {
				val id = DocumentsContract.getDocumentId(uri)

				if (!TextUtils.isEmpty(id)) {
					if (id.startsWith("raw:")) {
						return id.replaceFirst("raw:".toRegex(), "")
					}
				}

				val contentUri = ContentUris
					.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())

				path = getDataColumn(context, contentUri, null, null)
			} else if (isMediaDocument(uri)) {
				val docId = DocumentsContract.getDocumentId(uri)
				val split: Array<String?> =
					docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
				val type = split[0]

				var contentUri: Uri? = null
				if ("image" == type) {
					contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
				} else if ("video" == type) {
					contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
				} else if ("audio" == type) {
					contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
				}

				val selection = "_id=?"
				val selectionArgs = arrayOf<String?>(
					split[1]
				)

				path = FileUtil.getDataColumn(context, contentUri!!, selection, selectionArgs)
			}
		} else if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme(), ignoreCase = true)) {
			path = getDataColumn(context, uri, null, null)
		} else if (ContentResolver.SCHEME_FILE.equals(uri.getScheme(), ignoreCase = true)) {
			path = uri.getPath()
		}

		if (path != null) {
			try {
				return URLDecoder.decode(path, StandardCharsets.UTF_8)
			} catch (e: Exception) {
				return null
			}
		}
		return null
	}

	private fun getDataColumn(
		context: Context,
		uri: Uri,
		selection: String?,
		selectionArgs: Array<String?>?
	): String? {
		var cursor: Cursor? = null

		val column = MediaStore.Images.Media.DATA
		val projection = arrayOf<String?>(
			column
		)

		try {
			cursor =
				context.getContentResolver().query(uri, projection, selection, selectionArgs, null)
			if (cursor != null && cursor.moveToFirst()) {
				val column_index = cursor.getColumnIndexOrThrow(column)
				return cursor.getString(column_index)
			}
		} catch (ignored: Exception) {
		} finally {
			if (cursor != null) {
				cursor.close()
			}
		}
		return null
	}

	private fun isExternalStorageDocument(uri: Uri): Boolean {
		return "com.android.externalstorage.documents" == uri.getAuthority()
	}

	private fun isDownloadsDocument(uri: Uri): Boolean {
		return "com.android.providers.downloads.documents" == uri.getAuthority()
	}

	private fun isMediaDocument(uri: Uri): Boolean {
		return "com.android.providers.media.documents" == uri.getAuthority()
	}

	private fun saveBitmap(bitmap: Bitmap, destPath: String) {
		createNewFileIfNotPresent(destPath)

		try {
			FileOutputStream(destPath).use { out ->
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
			}
		} catch (e: IOException) {
			e.printStackTrace()
		}
	}

	fun getScaledBitmap(str: String?, i: Int): Bitmap {
		var i = i
		val i2: Int
		val decodeFile = BitmapFactory.decodeFile(str)
		val width = decodeFile.getWidth()
		val height = decodeFile.getHeight()
		if (width > height) {
			val i3 = i / width * height
			i2 = i
			i = i3
		} else {
			i2 = width * i / height
		}
		return Bitmap.createScaledBitmap(decodeFile, i2, i, true)
	}

	fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
		val width = options.outWidth
		val height = options.outHeight
		var inSampleSize = 1

		if (height > reqHeight || width > reqWidth) {
			val halfHeight = height / 2
			val halfWidth = width / 2

			while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
				inSampleSize *= 2
			}
		}

		return inSampleSize
	}

	fun decodeSampleBitmapFromPath(path: String?, reqWidth: Int, reqHeight: Int): Bitmap? {
		val options = BitmapFactory.Options()
		options.inJustDecodeBounds = true
		BitmapFactory.decodeFile(path, options)
		options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
		options.inJustDecodeBounds = false
		return BitmapFactory.decodeFile(path, options)
	}

	fun resizeBitmapFileRetainRatio(fromPath: String, destPath: String, max: Int) {
		if (isExistFile(fromPath)) {
			saveBitmap(getScaledBitmap(fromPath, max), destPath)
		}
	}

	fun resizeBitmapFileToSquare(fromPath: String, destPath: String, max: Int) {
		if (isExistFile(fromPath)) {
			saveBitmap(
				Bitmap.createScaledBitmap(
					BitmapFactory.decodeFile(fromPath),
					max,
					max,
					true
				), destPath
			)
		}
	}

	fun resizeBitmapFileToCircle(fromPath: String, destPath: String) {
		if (!isExistFile(fromPath)) return

		val decodeFile = BitmapFactory.decodeFile(fromPath)
		val createBitmap = Bitmap.createBitmap(
			decodeFile.getWidth(),
			decodeFile.getHeight(),
			Bitmap.Config.ARGB_8888
		)
		val canvas = Canvas(createBitmap)
		val paint = Paint()
		val rect = Rect(0, 0, decodeFile.getWidth(), decodeFile.getHeight())
		paint.setAntiAlias(true)
		canvas.drawARGB(0, 0, 0, 0)
		paint.setColor(-12434878)
		canvas.drawCircle(
			(decodeFile.getWidth() / 2).toFloat(),
			(decodeFile.getHeight() / 2).toFloat(),
			(decodeFile.getWidth() / 2).toFloat(),
			paint
		)
		paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
		canvas.drawBitmap(decodeFile, rect, rect, paint)
		saveBitmap(createBitmap, destPath)
	}

	fun resizeBitmapFileWithRoundedBorder(fromPath: String, destPath: String, pixels: Int) {
		if (!isExistFile(fromPath)) return

		val decodeFile = BitmapFactory.decodeFile(fromPath)
		val createBitmap = Bitmap.createBitmap(
			decodeFile.getWidth(),
			decodeFile.getHeight(),
			Bitmap.Config.ARGB_8888
		)
		val canvas = Canvas(createBitmap)
		val paint = Paint()
		val rect = Rect(0, 0, decodeFile.getWidth(), decodeFile.getHeight())
		val rectF = RectF(rect)
		val f = pixels.toFloat()
		paint.setAntiAlias(true)
		canvas.drawARGB(0, 0, 0, 0)
		paint.setColor(-12434878)
		canvas.drawRoundRect(rectF, f, f, paint)
		paint.setXfermode(PorterDuffXfermode(PorterDuff.Mode.SRC_IN))
		canvas.drawBitmap(decodeFile, rect, rect, paint)
		saveBitmap(createBitmap, destPath)
	}

	fun cropBitmapFileFromCenter(fromPath: String, destPath: String, w: Int, h: Int) {
		if (!isExistFile(fromPath)) return
		val src = BitmapFactory.decodeFile(fromPath)

		val width = src.getWidth()
		val height = src.getHeight()

		if (width < w && height < h) return

		var x = 0
		var y = 0

		if (width > w) x = (width - w) / 2

		if (height > h) y = (height - h) / 2

		var cw = w
		var ch = h

		if (w > width) cw = width

		if (h > height) ch = height

		val bitmap = Bitmap.createBitmap(src, x, y, cw, ch)
		saveBitmap(bitmap, destPath)
	}

	fun rotateBitmapFile(fromPath: String, destPath: String, angle: Float) {
		if (!isExistFile(fromPath)) return

		val decodeFile = BitmapFactory.decodeFile(fromPath)
		val matrix = Matrix()
		matrix.postRotate(angle)
		saveBitmap(
			Bitmap.createBitmap(
				decodeFile,
				0,
				0,
				decodeFile.getWidth(),
				decodeFile.getHeight(),
				matrix,
				true
			), destPath
		)
	}

	fun scaleBitmapFile(fromPath: String, destPath: String, x: Float, y: Float) {
		if (!isExistFile(fromPath)) return

		val decodeFile = BitmapFactory.decodeFile(fromPath)
		val matrix = Matrix()
		matrix.postScale(x, y)
		saveBitmap(
			Bitmap.createBitmap(
				decodeFile,
				0,
				0,
				decodeFile.getWidth(),
				decodeFile.getHeight(),
				matrix,
				true
			), destPath
		)
	}

	fun skewBitmapFile(fromPath: String, destPath: String, x: Float, y: Float) {
		if (!isExistFile(fromPath)) return

		val decodeFile = BitmapFactory.decodeFile(fromPath)
		val matrix = Matrix()
		matrix.postSkew(x, y)
		saveBitmap(
			Bitmap.createBitmap(
				decodeFile,
				0,
				0,
				decodeFile.getWidth(),
				decodeFile.getHeight(),
				matrix,
				true
			), destPath
		)
	}

	fun setBitmapFileColorFilter(fromPath: String, destPath: String, color: Int) {
		if (!isExistFile(fromPath)) return

		val decodeFile = BitmapFactory.decodeFile(fromPath)
		val createBitmap = Bitmap.createBitmap(
			decodeFile,
			0,
			0,
			decodeFile.getWidth() - 1,
			decodeFile.getHeight() - 1
		)
		val paint = Paint()
		paint.setColorFilter(LightingColorFilter(color, 1))
		Canvas(createBitmap).drawBitmap(createBitmap, 0.0f, 0.0f, paint)
		saveBitmap(createBitmap, destPath)
	}

	fun setBitmapFileBrightness(fromPath: String, destPath: String, brightness: Float) {
		if (!isExistFile(fromPath)) return

		val src = BitmapFactory.decodeFile(fromPath)
		val cm = ColorMatrix(
			floatArrayOf(
				1f, 0f, 0f, 0f, brightness,
				0f, 1f, 0f, 0f, brightness,
				0f, 0f, 1f, 0f, brightness,
				0f, 0f, 0f, 1f, 0f
			)
		)

		val bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig()!!)
		val canvas = Canvas(bitmap)
		val paint = Paint()
		paint.setColorFilter(ColorMatrixColorFilter(cm))
		canvas.drawBitmap(src, 0f, 0f, paint)
		saveBitmap(bitmap, destPath)
	}

	fun setBitmapFileContrast(fromPath: String, destPath: String, contrast: Float) {
		if (!isExistFile(fromPath)) return

		val src = BitmapFactory.decodeFile(fromPath)
		val cm = ColorMatrix(
			floatArrayOf(
				contrast, 0f, 0f, 0f, 0f,
				0f, contrast, 0f, 0f, 0f,
				0f, 0f, contrast, 0f, 0f,
				0f, 0f, 0f, 1f, 0f
			)
		)

		val bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig()!!)
		val canvas = Canvas(bitmap)
		val paint = Paint()
		paint.setColorFilter(ColorMatrixColorFilter(cm))
		canvas.drawBitmap(src, 0f, 0f, paint)

		saveBitmap(bitmap, destPath)
	}

	fun getJpegRotate(filePath: String): Int {
		var rotate = 0
		try {
			val exif = ExifInterface(filePath)
			val iOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1)

			rotate = when (iOrientation) {
				ExifInterface.ORIENTATION_ROTATE_90 -> 90
				ExifInterface.ORIENTATION_ROTATE_180 -> 180
				ExifInterface.ORIENTATION_ROTATE_270 -> 270
				else -> rotate
			}
		} catch (e: IOException) {
			return 0
		}

		return rotate
	}

	fun createNewPictureFile(context: Context): File {
		return File(
			context.getExternalFilesDir(Environment.DIRECTORY_DCIM)!!
				.getAbsolutePath() + File.separator + SimpleDateFormat(
				"yyyyMMdd_HHmmss",
				Locale.ENGLISH
			).format(
				Date()
			) + ".jpg"
		)
	}

	fun readFromInputStream(stream: InputStream): ByteArray {
		var available: Int

		try {
			available = stream.available()
		} catch (e: IOException) {
			available = 0
		}

		val outputStream = ByteArrayOutputStream()
		val buffer = ByteArray(available)

		try {
			var len = stream.read(buffer)
			while (len != -1) {
				outputStream.write(buffer, 0, len)
				len = stream.read(buffer)
			}
		} catch (e: IOException) {
			return ByteArray(0)
		}

		return outputStream.toByteArray()
	}

	/**
	 * Write bytes to a file.
	 *
	 * @param target The file to write the data to. Note that it'll get created, even parent directories
	 * @param data   The data in bytes to write to. [FileUtil.readFromInputStream]
	 * for example, reads bytes
	 * @throws IOException Thrown when any exception occurs while operating
	 */
	@Throws(IOException::class)
	fun writeBytes(target: File, data: ByteArray) {
		if (!target.exists()) {
			target.getParentFile().mkdirs()
		}
		val outputStream = BufferedOutputStream(FileOutputStream(target))
		outputStream.write(data)
		outputStream.flush()
		outputStream.close()
	}

	@Throws(IOException::class)
	fun extractZipTo(input: ZipInputStream, outPath: String) {
		val outDir = File(outPath)
		if (!outDir.exists()) {
			outDir.mkdirs()
		}

		var entry = input.getNextEntry()
		while (entry != null) {
			val entryPathExtracted = File(outPath, entry.getName()).getAbsolutePath()

			if (!entry.isDirectory()) {
				File(entryPathExtracted).getParentFile().mkdirs()
				writeBytes(File(entryPathExtracted), readFromInputStream(input))
			}
			input.closeEntry()
			entry = input.getNextEntry()
		}
		input.close()
	}

	/**
	 * Asks the user to grant the current app [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE].
	 * Will silently ignore cases where a screen to manage that permission doesn't exist, except on
	 * devices with an API level of 29 or lower.
	 *
	 * @throws AssertionError Thrown if the device's API level is 29 or lower
	 */
	fun requestAllFilesAccessPermission(context: Context) {
		if (Build.VERSION.SDK_INT > 29) {
			if (!Environment.isExternalStorageManager()) {
				val intent = Intent()
				intent.action = Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
				intent.data = Uri.parse("package:" + context.getPackageName())
				intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
				try {
					context.startActivity(intent)
				} catch (e: ActivityNotFoundException) {
					Log.e(
						"FileUtil",
						"Activity to manage apps' all files access permission not found!"
					)
				}
			}
		} else {
			throw AssertionError("Not on an API level 30 or higher device!")
		}
	}

	/**
	 * Checks if a provided file is image or not. I don't know if it throws any exceptions
	 * TODO: Find a better solution if available
	 */
	fun isImageFile(path: String?): Boolean {
		val mimeType = URLConnection.guessContentTypeFromName(path)
		return mimeType != null && mimeType.startsWith("image")
	}

	// ============================================================
	// Acceso universal a almacenamiento compartido (todas las
	// versiones de Android), usando SAF (Storage Access Framework)
	// como respaldo en Android 10 (Q), donde File() directo a
	// rutas arbitrarias fuera del directorio propio de la app
	// no funciona con targetSdkVersion >= 30.
	// ============================================================

	private const val PREFS_NAME = "file_util_saf_prefs"
	private const val KEY_TREE_URI = "saf_tree_uri"

	/**
	 * true si File() directo a rutas arbitrarias del almacenamiento
	 * compartido va a funcionar: API <= 28, o API >= 30 con
	 * MANAGE_EXTERNAL_STORAGE concedido. En API 29 (Q) siempre
	 * es false con targetSdkVersion >= 30.
	 */
	fun hasDirectFileAccess(): Boolean {
		return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ||
				(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
						Environment.isExternalStorageManager())
	}

	/**
	 * Guarda el Uri del árbol de directorios elegido por el
	 * usuario mediante ACTION_OPEN_DOCUMENT_TREE, para reutilizarlo
	 * en futuras aperturas de la app sin volver a pedirlo.
	 */
	fun saveTreeUri(context: Context, uri: Uri) {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.edit()
			.putString(KEY_TREE_URI, uri.toString())
			.apply()
	}

	/**
	 * Devuelve el Uri de árbol guardado previamente, o null si el
	 * usuario nunca ha elegido uno.
	 */
	fun getSavedTreeUri(context: Context): Uri? {
		val str = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
			.getString(KEY_TREE_URI, null) ?: return null
		return Uri.parse(str)
	}

	/**
	 * true si la app todavía conserva permiso persistente de
	 * lectura y escritura sobre el Uri dado (los permisos SAF
	 * pueden revocarse manualmente por el usuario o al reinstalar).
	 */
	fun hasPersistedPermission(context: Context, uri: Uri): Boolean {
		return context.contentResolver.persistedUriPermissions.any {
			it.uri == uri && it.isReadPermission && it.isWritePermission
		}
	}

	/**
	 * Navega o crea la cadena de subcarpetas/archivo indicada por
	 * relativePath (separado por "/") dentro de root, usando la
	 * API de DocumentFile. El último segmento se trata como
	 * archivo; los anteriores, como carpetas.
	 */
	private fun resolveOrCreateDocument(
		root: androidx.documentfile.provider.DocumentFile,
		relativePath: String
	): androidx.documentfile.provider.DocumentFile? {
		val segments = relativePath.split("/").filter { it.isNotEmpty() }
		if (segments.isEmpty()) return null

		var current = root
		for (i in segments.indices) {
			val name = segments[i]
			val isLast = i == segments.lastIndex

			val existing = current.findFile(name)

			current = if (existing != null) {
				existing
			} else if (isLast) {
				val mimeType = URLConnection.guessContentTypeFromName(name)
					?: "application/octet-stream"
				current.createFile(mimeType, name) ?: return null
			} else {
				current.createDirectory(name) ?: return null
			}
		}
		return current
	}

	/**
	 * Escribe texto en relativePath (p.ej. "MiCarpeta/notas.txt")
	 * dentro del almacenamiento compartido, sin importar la
	 * versión de Android. Usa File() directo cuando es posible,
	 * y SAF (requiere haber guardado un Uri de árbol con
	 * saveTreeUri) cuando no.
	 *
	 * @return true si escribió correctamente.
	 */
	fun writeFileAnywhere(context: Context, relativePath: String, content: String): Boolean {
		if (hasDirectFileAccess()) {
			writeFile(externalStorageDir + File.separator + relativePath, content)
			return true
		}

		val treeUri = getSavedTreeUri(context) ?: return false
		if (!hasPersistedPermission(context, treeUri)) return false

		val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
			?: return false
		val doc = resolveOrCreateDocument(root, relativePath) ?: return false

		return try {
			context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
				out.write(content.toByteArray(StandardCharsets.UTF_8))
			}
			true
		} catch (e: IOException) {
			e.printStackTrace()
			false
		}
	}

	/**
	 * Igual que [writeFileAnywhere] pero para bytes crudos
	 * (imágenes, binarios, etc.).
	 */
	fun writeBytesAnywhere(context: Context, relativePath: String, data: ByteArray): Boolean {
		if (hasDirectFileAccess()) {
			writeBytes(File(externalStorageDir + File.separator + relativePath), data)
			return true
		}

		val treeUri = getSavedTreeUri(context) ?: return false
		if (!hasPersistedPermission(context, treeUri)) return false

		val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
			?: return false
		val doc = resolveOrCreateDocument(root, relativePath) ?: return false

		return try {
			context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
				out.write(data)
			}
			true
		} catch (e: IOException) {
			e.printStackTrace()
			false
		}
	}

	/**
	 * Lee el contenido de texto de relativePath, sin importar la
	 * versión de Android. Devuelve "" si no existe o no se pudo
	 * leer.
	 */
	fun readFileAnywhere(context: Context, relativePath: String): String {
		if (hasDirectFileAccess()) {
			return readFileIfExist(externalStorageDir + File.separator + relativePath)
		}

		val treeUri = getSavedTreeUri(context) ?: return ""
		if (!hasPersistedPermission(context, treeUri)) return ""

		val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
			?: return ""

		val segments = relativePath.split("/").filter { it.isNotEmpty() }
		var current: androidx.documentfile.provider.DocumentFile? = root
		for (name in segments) {
			current = current?.findFile(name)
		}
		val doc = current ?: return ""

		return try {
			context.contentResolver.openInputStream(doc.uri)?.use { input ->
				String(readFromInputStream(input), StandardCharsets.UTF_8)
			} ?: ""
		} catch (e: IOException) {
			e.printStackTrace()
			""
		}
	}

	/**
	 * true si relativePath existe, sin importar la versión de
	 * Android.
	 */
	fun existsAnywhere(context: Context, relativePath: String): Boolean {
		if (hasDirectFileAccess()) {
			return isExistFile(externalStorageDir + File.separator + relativePath)
		}

		val treeUri = getSavedTreeUri(context) ?: return false
		if (!hasPersistedPermission(context, treeUri)) return false

		val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
			?: return false

		val segments = relativePath.split("/").filter { it.isNotEmpty() }
		var current: androidx.documentfile.provider.DocumentFile? = root
		for (name in segments) {
			current = current?.findFile(name)
		}
		return current != null && current.exists()
	}
}