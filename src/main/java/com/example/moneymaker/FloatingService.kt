package com.example.moneymaker
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.widget.Button
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.graphics.get
import androidx.core.graphics.set
import androidx.core.graphics.scale
import android.graphics.*
import android.os.Environment
import android.provider.MediaStore
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: Button

    private lateinit var mediaProjection: MediaProjection
    private lateinit var imageReader: ImageReader

    private var recordingState = 0

    private var etape = 0;
    private var isSolving = false

    private lateinit var params : WindowManager.LayoutParams

    private var screenHeight: Int? = 0
    private var screenWidth: Int? = 0

    //Props qui dépendent du user
    //propriétés de la grille
    private var beginScaledX = 0
    private var beginScaledY = 0
    private var endScaledX = 0
    private var endScaledY = 0
    private var beginRealX = 0
    private var beginRealY = 0
    private var endRealX = 0
    private var endRealY = 0
    private var firstButtonX = 0
    private var firstButtonY = 0
    private var closeSaveProgressButtonX = 0
    private var closeSaveProgressButtonY = 0
    private var closeSaveRateButtonY = 0
    private var returnButtonX = 0
    private var returnButtonY = 0
    private var realityWhyModifier = 0

    private var user = "Louan"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingButton = Button(this)
        floatingButton.text = "Off"
        if(user == "Louan"){
            beginScaledX = 512
            beginScaledY = 1352
            endScaledX = 3814
            endScaledY = 4640
            beginRealX = 25
            beginRealY = 420
            endRealX = 1050
            endRealY = 1450
            firstButtonX = 65
            firstButtonY = 2030
            closeSaveProgressButtonX = 960
            closeSaveProgressButtonY = 640
            closeSaveRateButtonY = 790
            returnButtonX = 70
            returnButtonY = 130
            realityWhyModifier = 0 //(Si l'ocr de TEXTE clique trop haut ou trop bas, ajuster cette merde)
        }




        params = WindowManager.LayoutParams(

            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,

            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,

            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,

            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 200

        windowManager.addView(floatingButton, params)


        floatingButton.setOnTouchListener(object : View.OnTouchListener {

            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {

                when (event.action) {

                    MotionEvent.ACTION_DOWN -> {

                        initialX = params.x
                        initialY = params.y

                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        return false
                    }

                    MotionEvent.ACTION_MOVE -> {

                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()

                        windowManager.updateViewLayout(floatingButton, params)

                        return true
                    }
                }

                return false
            }
        })
    }
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startMyForeground()

        val resultCode = intent?.getIntExtra("resultCode", -1)

        screenHeight = intent?.getIntExtra("height", 1920)
        screenWidth = intent?.getIntExtra("width", 1920)

        val data = intent?.getParcelableExtra<Intent>("data")

        println("$resultCode $data")
        if (resultCode == null || resultCode != Activity.RESULT_OK || data == null) {
            println("Erreur : permission MediaProjection manquante")
            stopSelf()

            return START_NOT_STICKY

        }


        val manager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as MediaProjectionManager


        mediaProjection =
            manager.getMediaProjection(resultCode, data)!!
        mediaProjection.registerCallback(

            object : MediaProjection.Callback() {

                override fun onStop() {

                    super.onStop()

                    println("MediaProjection stopped")

                }

            },

            Handler(Looper.getMainLooper())

        )



        floatingButton.setOnClickListener {
            //! ICI
            if (recordingState == 0){
                recordingState = 1;
                startScreenCapture()
                floatingButton.text = "On"
            }else{
                recordingState = -recordingState
                if(recordingState == 1){
                    floatingButton.text = "On"
                }else{
                    floatingButton.text = "Off"
                }
            }

        }


        return START_STICKY

    }

    private fun startMyForeground() {

        val channelId = "screen_capture"

        val channel = NotificationChannel(
            channelId,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )

        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(channel)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Capture active")
            .setContentText("Analyse de l'écran")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()


        startForeground(1, notification)

    }


    private fun startScreenCapture() {
        // Leopold, je t'en veux. --Louan
        imageReader = ImageReader.newInstance(
            1080,
            1920,
            PixelFormat.RGBA_8888,
            2
        )


        mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            1080,
            1920,
            resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )


        Thread {

            while (true) {
                if(recordingState != 1){
                    Thread.sleep(5000)
                    continue
                }

                val image = imageReader.acquireLatestImage()

                if (image != null) {

                    println("Image capturée")
                    if (etape < 4 && !isSolving || etape == 6) {
                        runOCR(imageToBitmap(image))
                    }
                    image.close()
                }
                Thread.sleep(2000)

            }

        }.start()

    }


    fun moveButton(x: Int, y : Int){
        params.x = x
        params.y = y
        windowManager.updateViewLayout(floatingButton, params)
    }

    fun realX(rect : Rect?): Int {
        return ((rect?.centerX() ?: 0).toFloat() *((screenWidth!!).toFloat() /1080.0F)).toInt()

    }
    fun realY(rect : Rect?): Int {
        return ((rect?.centerY() ?: 0).toFloat() *((screenHeight!!).toFloat() /(1750.0F+realityWhyModifier))).toInt()

    }

    fun runOCR(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (etape == 4) return@addOnSuccessListener
                var rect100: Rect? = null
                var is150 = false
                var rect150: Rect? = null
                var is200 = false
                var rect200: Rect? = null
                var is300 = false
                var rect300: Rect? = null
                var is_progress = false
                for (block in visionText.textBlocks) {
                    val text = block.text
                    val rect = block.boundingBox
                    if(text == "+150 points"){
                        is150 = true
                        rect150 = rect
                    }else if(text == "+200 points" || text ==  "+400 points"){
                        is200 = true
                        rect200 = rect
                    }else if(text == "+300 points" || text ==  "+600 points"){
                        is300 = true
                        rect300 = rect
                    }else if(text == "+100 points"){
                        rect100 = rect
                    }else if (text == "Saving your progress") {
                        is_progress = true
                        AutoService.instance.click(closeSaveProgressButtonX, closeSaveProgressButtonY)
                        etape = 0
                    }else if (text == "Rate Game") {
                        is_progress = true
                        AutoService.instance.click(closeSaveProgressButtonX, closeSaveRateButtonY)
                        etape = 0
                    }
                }
                for (block in visionText.textBlocks) {
                    val text = block.text
                    val rect = block.boundingBox
                    var real_y = realY(rect)
                    var real_x = realX(rect)
                    if (is_progress) {
                        continue
                    } else if (text == "Leave" && etape == 6){
                        AutoService.instance.click(real_x, real_y)
                        etape = 0
                    } else if (text == "New Game") {
                        AutoService.instance.click(real_x, real_y)
                        etape = 1
                    } else if (text == "Start" || text == "Continue" || text == "Main menu" || text == "OK" || text == "Claim") {
                        AutoService.instance.click(real_x, real_y)
                        etape = 0
                    } else if (text == "Select difficulty") {
                        if (is300){
                            real_y = realY(rect300)
                            real_x = realX(rect300)
                        }else if(is200){
                            real_y = realY(rect200)
                            real_x = realX(rect200)
                        }else if(is150){
                            real_y = realY(rect150)
                            real_x = realX(rect150)
                        }else{
                            real_y = realY(rect100)
                            real_x = realX(rect100)
                        }
                        AutoService.instance.click(real_x, real_y)
                        // Click dans le isRect max
                        etape = 2
                    } else if (text == "Notes" && etape < 3) {
                        Thread.sleep(4000)
                        flushImageReader()
                        etape = 5
                    } else if (etape == 3) {
                        etape = 4
                        makeSudoku(bitmap)
                    } else if (etape == 5) {
                        etape = 3
                        flushImageReader()
                    }
                    println("Texte trouvé: $text dans $rect")
                    // Tu peux détecter ici les boutons ou chiffres
                }
                is_progress = false
                println(etape)

            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }
    fun morphologicalDilate(src: Bitmap, radius: Int = 1): Bitmap {
        val width = src.width
        val height = src.height

        // Extraire tous les pixels en une fois
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height) { i -> Color.red(pixels[i]) }
        val temp = IntArray(width * height)
        val result = IntArray(width * height)

        // Passe horizontale
        for (y in 0 until height) {
            for (x in 0 until width) {
                var minVal = 255
                for (dx in -radius..radius) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    val v = gray[y * width + nx]
                    if (v < minVal) minVal = v
                }
                temp[y * width + x] = minVal
            }
        }

        // Passe verticale
        for (y in 0 until height) {
            for (x in 0 until width) {
                var minVal = 255
                for (dy in -radius..radius) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    val v = temp[ny * width + x]
                    if (v < minVal) minVal = v
                }
                val final = minVal
                result[y * width + x] = Color.rgb(final, final, final)
            }
        }

        val out = createBitmap(width, height)
        out.setPixels(result, 0, width, 0, 0, width, height)
        return out
    }


    fun makeSudoku(bitmap: Bitmap) {
        etape = 4
        isSolving = true
        val processed = toBlackWhite(morphologicalDilate(increaseContrast(bitmap)))
        val scaledBitmap = processed.scale(processed.width * 4, processed.height * 4)
        val image = InputImage.fromBitmap(scaledBitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        // A activer une fois et lancer sur une grille de sudoku pour pouvoir faire les mesures des scaled variables (avec paint depuis les photos de la galerie)
        // saveBitmapToGallery(this,scaledBitmap)

        // Coordonnées de la grille dans l'image scalée (x4)
        val width = endScaledX-beginScaledX
        val cell = width/9.0F

        val grid = Array(9) { IntArray(9) }

        // Coordonnées réelles pour cliquer sur l'écran
        val centerXs = Array(9) { IntArray(9) }
        val centerYs = Array(9) { IntArray(9) }
        for (i in 0..8) {
            for (j in 0..8) {
                // Conversion vers coords écran réel (1080x2400)
                centerXs[i][j] = beginRealX + ((endRealX-beginRealX) / 9f * (j + 0.5f)).toInt()
                centerYs[i][j] = beginRealY + ((endRealY-beginRealY) / 9f * (i + 0.5f)).toInt()
            }
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                var salope = 0
                for (block in visionText.textBlocks)
                    for (line in block.lines)
                        for (element in line.elements) {
                            val rect = element.boundingBox ?: continue
                            val text = element.text

                            for (i in text.indices) {
                                val c = text[i]
                                if (c !in '1'..'9') continue
                                // Position X du caractère dans l'image scalée
                                var charWidth = rect.width() / text.length.toFloat()
                                //if (c == '1') charWidth *= 2.0F
                                val charCenterX = rect.left + charWidth * (i + 0.5f)
                                val charCenterY = rect.centerY().toFloat()


                                val col = ((charCenterX - beginScaledX) / cell).toInt()
                                val row = ((charCenterY - beginScaledY) / cell).toInt()
                                println("Char '$c' at x=$charCenterX y=$charCenterY -> col=$col row=$row (gridLeft=$beginScaledX gridTop=$beginScaledY cellW=$cell cellH=$cell)")
                                if (row in 0..8 && col in 0..8) {
                                    salope += 1
                                    if (grid[row][col] == 0) {
                                        grid[row][col] = c.toString().toInt()
                                    } else if (grid[row][col] != c.toString().toInt()) {
                                        grid[row][col+1] = c.toString().toInt()
                                    }
                                }
                            }
                        }

                println("\n\nGrille OCR : " + grid.contentDeepToString())

                val solved = Array(9) { i -> grid[i].clone() }
                if (salope >= 17 && solve(solved)) {
                    println("\n\nSolution : " + solved.contentDeepToString())
                    CoroutineScope(Dispatchers.IO).launch {
                        click_on_solve_and_do_the_difference(
                            grid, solved,
                            centerXs, centerYs
                        )
                    }
                } else {
                    if (salope > 0) {
                        println("PAS SOLVE - grille mal lue")
                        etape = 6
                        isSolving = false
                        AutoService.instance.click(
                            returnButtonX,
                            returnButtonY
                        )
                    } else {
                        isSolving = false
                        etape = 0
                        println("Faux positif")
                    }
                    return@addOnSuccessListener
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                etape = 0
                isSolving = false
            }
    }

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap) {
        val filename = "image_${System.currentTimeMillis()}.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )

        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }
    }
    suspend fun click_on_solve_and_do_the_difference(
        grid: Array<IntArray>,
        solved: Array<IntArray>,
        centerXs: Array<IntArray>,
        centerYs: Array<IntArray>
    ) {
        AutoService.instance.click(
            firstButtonX,
            firstButtonY
        )
        for (n in 1..9) {
            delay(300)

            for (i in 0..8) {
                for (j in 0..8) {
                    if (grid[i][j] != solved[i][j] && solved[i][j] == n) {
                        AutoService.instance.click(
                            centerXs[i][j],
                            centerYs[i][j]
                        )

                        delay(100)
                    }
                }
            }
        }

        flushImageReader()
        isSolving = false
        delay(1000)
        flushImageReader()
        Thread.sleep(1000) // Laisse le temps à l'écran de changer
        etape = 0
    }

    fun increaseContrast(src: Bitmap): Bitmap {
        val bmp = createBitmap(src.width, src.height)

        val canvas = Canvas(bmp)
        val paint = Paint()

        val contrast = 3.5f
        val brightness = -70f

        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(src, 0f, 0f, paint)

        return bmp
    }
    fun toBlackWhite(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val bmp = createBitmap(width, height)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = src[x, y]

                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val gray = (0.3 * r + 0.59 * g + 0.11 * b).toInt()

                val threshold = 150
                val newPixel = if (gray > threshold) 255 else 0

                bmp[x, y] = Color.rgb(newPixel, newPixel, newPixel)
            }
        }
        return bmp
    }

    fun solve(grid: Array<IntArray>): Boolean {

        for(r in 0..8)
            for(c in 0..8)

                if(grid[r][c]==0){

                    for(n in 1..9)

                        if(valid(grid,r,c,n)){

                            grid[r][c]=n

                            if(solve(grid)) return true

                            grid[r][c]=0
                        }

                    return false
                }

        return true
    }

    fun valid(grid: Array<IntArray>, row: Int, col: Int, num: Int): Boolean {

        for (i in 0..8) {

            if (grid[row][i] == num) return false

            if (grid[i][col] == num) return false

            val boxRow = 3 * (row / 3) + i / 3
            val boxCol = 3 * (col / 3) + i % 3

            if (grid[boxRow][boxCol] == num)
                return false
        }

        return true
    }

    fun flushImageReader() {
        var img = imageReader.acquireLatestImage()
        while (img != null) {
            img.close()
            img = imageReader.acquireLatestImage()
        }
    }









    fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = createBitmap(width + rowPadding / pixelStride, height)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, width, height)
    }


    override fun onDestroy() {

        super.onDestroy()

        windowManager.removeView(floatingButton)

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
