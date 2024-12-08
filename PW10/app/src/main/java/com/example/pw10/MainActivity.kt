package com.example.pw10

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(imageFileName = "saved_image.jpg", filesDir = filesDir)
        }
    }
}

data class ListItem(
    val drawable: Drawable
)

@Composable
fun MainScreen(imageFileName: String, filesDir: File) {
    val context = LocalContext.current
    val items = remember { mutableStateListOf<ListItem>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val savedPaths = loadSavedImagePaths(context)
            savedPaths.forEach { path ->
                val drawable = loadImageFromInternalStorage(File(path))
                if (drawable != null) {
                    withContext(Dispatchers.Main) {
                        items.add(ListItem(drawable = drawable))
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var url by remember { mutableStateOf("") }

        BasicTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(MaterialTheme.colors.surface)
                        .padding(8.dp)
                ) {
                    if (url.isEmpty()) Text("Введите ссылку на изображение")
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (url.isNotEmpty()) {
                    scope.launch(Dispatchers.IO) {
                        val downloadedDrawable = downloadImage(url, context)
                        if (downloadedDrawable != null) {
                            val savedPath = saveImageToInternalStorage(downloadedDrawable, filesDir)
                            if (savedPath != null) {
                                withContext(Dispatchers.Main) {
                                    items.add(ListItem(drawable = downloadedDrawable))
                                    Toast.makeText(context, "Изображение добавлено", Toast.LENGTH_SHORT).show()
                                }
                                saveImagePath(context, savedPath)
                            } else {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Ошибка сохранения изображения", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Введите ссылку", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Загрузить изображение")
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items.size) { item ->
                ImageCard(item = items[item])
            }
        }
    }
}

@Composable
fun ImageCard(item: ListItem) {
    Card(
        backgroundColor = Color.Gray,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = 8.dp
    ) {
        Image(
            painter = createImagePainter(item.drawable),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}

@Composable
fun createImagePainter(drawable: Drawable): Painter {
    val bitmap = (drawable as? BitmapDrawable)?.bitmap
    return remember(bitmap) {
        bitmap?.asImageBitmap()?.let { BitmapPainter(it) } ?: EmptyPainter()
    }
}

class EmptyPainter : Painter() {
    override val intrinsicSize: Size
        get() = Size.Unspecified

    override fun DrawScope.onDraw() {}
}

@SuppressLint("MutatingSharedPrefs")
fun saveImagePath(context: Context, path: String) {
    val sharedPreferences = context.getSharedPreferences("image_paths", Context.MODE_PRIVATE)
    val editor = sharedPreferences.edit()
    val paths = sharedPreferences.getStringSet("paths", mutableSetOf()) ?: mutableSetOf()
    paths.add(path)
    editor.putStringSet("paths", paths)
    editor.apply()
}

fun loadSavedImagePaths(context: Context): List<String> {
    val sharedPreferences = context.getSharedPreferences("image_paths", Context.MODE_PRIVATE)
    return sharedPreferences.getStringSet("paths", emptySet())?.toList() ?: emptyList()
}

fun saveImageToInternalStorage(drawable: Drawable, filesDir: File): String? {
    try {
        val file = File(filesDir, "${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        val bitmap = (drawable as BitmapDrawable).bitmap
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        return file.absolutePath
    } catch (e: IOException) {
        e.printStackTrace()
    }
    return null
}

fun loadImageFromInternalStorage(file: File): Drawable? {
    return if (file.exists()) {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        BitmapDrawable(Resources.getSystem(), bitmap)
    } else {
        null
    }
}

suspend fun downloadImage(url: String, context: Context): Drawable? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()

        val result = (ImageLoader(context).execute(request) as? SuccessResult)?.drawable
        result
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
