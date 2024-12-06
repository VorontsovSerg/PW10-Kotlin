package com.example.pw10

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Card
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.*
import kotlinx.serialization.json.*

@Serializable
data class ImageData(
    val url: String,
    val bitmap: Bitmap
)

class MainActivity : ComponentActivity() {

    private val imageFileName = "downloaded_images.json"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var url by remember { mutableStateOf("") }
            var images by remember { mutableStateOf(listOf<ImageData>()) }

            // Загружаем изображения при старте приложения
            LaunchedEffect(Unit) {
                images = loadImagesFromInternalStorage()
            }

            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f).padding(8.dp)
                    )
                    Button(onClick = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val bitmap = downloadImage(url)
                            if (bitmap != null) {
                                val imageData = ImageData(url, bitmap)
                                saveImageToInternalStorage(imageData)
                                images = loadImagesFromInternalStorage()
                            }
                        }
                    }) {
                        Text("Загрузить")
                    }
                }
                LazyColumn {
                    items(images) { image ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { /* Handle click */ }
                        ) {
                            Column {
                                Image(
                                    bitmap = image.bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.height(200.dp)
                                )
                                Text(text = image.url, modifier = Modifier.padding(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadImage(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                Glide.with(this@MainActivity)
                    .asBitmap()
                    .load(url)
                    .submit()
                    .get()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun saveImageToInternalStorage(imageData: ImageData) {
        try {
            val file = File(filesDir, imageFileName)
            val outputStream = FileOutputStream(file, true)

            // Сериализация объекта ImageData в строку JSON
            val json = Json.encodeToString(imageData)
            outputStream.write(json.toByteArray())
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun loadImagesFromInternalStorage(): List<ImageData> {
        val file = File(filesDir, imageFileName)
        if (!file.exists()) return emptyList()

        val jsonString = file.readText()
        return Json.decodeFromString<List<ImageData>>(jsonString)
    }
}
