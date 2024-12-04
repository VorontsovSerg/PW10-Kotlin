package com.example.pw10

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
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    private val imageFileName = "downloaded_image.jpg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApp {
                MainScreen(imageFileName, filesDir)
            }
        }
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}

@Composable
fun MainScreen(imageFileName: String, filesDir: File) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var drawable by remember { mutableStateOf<Drawable?>(null) }
    val items = remember { mutableStateListOf<Any>() }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val savedImages = loadAllImagesFromInternalStorage(filesDir)
        if (savedImages.isNotEmpty()) {
            items.addAll(savedImages)
            Toast.makeText(context, "Изображения загружены из памяти", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Нет сохранённых изображений", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    if (url.isEmpty()) Text("Введите ссылку на изображение", fontSize = 14.sp)
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (url.isNotEmpty()) {
                    coroutineScope.launch {
                        val downloadedDrawable = downloadImage(url, context)
                        if (downloadedDrawable != null) {
                            val newFileName = "image_${System.currentTimeMillis()}.jpg" // Уникальное имя файла
                            saveImageToInternalStorage(downloadedDrawable, filesDir, newFileName)
                            items.add(0, downloadedDrawable) // Добавляем изображение в начало списка
                            Toast.makeText(context, "Изображение сохранено", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
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

        Spacer(modifier = Modifier.height(16.dp))

        // Динамический список элементов
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { item ->
                Card(
                    backgroundColor = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    elevation = 8.dp
                ) {
                    Image(
                        painter = createImagePainter(item),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                }
            }
        }
    }
}

// Функция для создания Painter из Drawable
@Composable
fun createImagePainter(drawable: Drawable): Painter {
    val bitmap = (drawable as? BitmapDrawable)?.bitmap
    return remember(bitmap) {
        bitmap?.asImageBitmap()?.let { BitmapPainter(it) } ?: EmptyPainter()
    }
}

// Вспомогательный объект для пустого Painter
class EmptyPainter : Painter() {
    override val intrinsicSize: Size
        get() = Size.Unspecified

    override fun DrawScope.onDraw() {
        // Пустой метод рисования
    }
}

// Загрузка изображения с URL
suspend fun downloadImage(url: String, context: Context): Drawable? {
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()

        // Получаем результат загрузки
        val result = (ImageLoader(context).execute(request) as? SuccessResult)?.drawable

        result
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Сохранение изображения во внутренней памяти устройства
fun saveImageToInternalStorage(drawable: Drawable, filesDir: File, fileName: String) {
    try {
        val file = File(filesDir, fileName)
        val outputStream = FileOutputStream(file)
        val bitmap = (drawable as BitmapDrawable).bitmap
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

// Загрузка изображений из внутренней памяти
fun loadAllImagesFromInternalStorage(filesDir: File): List<Drawable> {
    val images = mutableListOf<Drawable>()
    val files = filesDir.listFiles { _, name -> name.endsWith(".jpg") || name.endsWith(".png") }
    files?.forEach { file ->
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        images.add(BitmapDrawable(Resources.getSystem(), bitmap))
    }
    return images
}
