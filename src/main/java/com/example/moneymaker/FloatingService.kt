package com.example.moneymaker
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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

class FloatingService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: Button

    private lateinit var mediaProjection: MediaProjection
    private lateinit var imageReader: ImageReader

    private var recordingState = 0

    private var etape = 0;
    val is_thomas = 0

    private lateinit var params : WindowManager.LayoutParams

    private var screenHeight: Int? = 0
    private var screenWidth: Int? = 0

    //Props qui dépendent du user
    //propriétés de la grille
    private var beginX = 0
    private var beginY = 0
    private var endX = 0
    private var endY = 0
    //Offset lol
    private var offset = 0

    private var user = "L"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingButton = Button(this)
        floatingButton.text = "Off"
        if(user == "L"){
            beginX = 10
            beginY = 295
            endX = 704
            endY = 982
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
        params.y = 300

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
                    Thread.sleep(500)
                    continue
                }

                val image = imageReader.acquireLatestImage()

                if (image != null) {

                    println("Image capturée")
                    if (etape < 4) {
                        runOCR(imageToBitmap((image)))
                    } else {
                        Thread.sleep(2000)
                    }
                    image.close()

                }

                Thread.sleep(5000)

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
        return ((rect?.centerY() ?: 0).toFloat() *((screenHeight!!).toFloat() /1920.0F)).toInt()

    }

    fun realX(x : Int): Int {
        return (x.toFloat() *((screenWidth!!).toFloat() /1080.0F)).toInt()

    }
    fun realY(y : Int): Int {
        return (y.toFloat() *((screenHeight!!).toFloat() /1920.0F)).toInt()

    }

    fun imaginaryX(x : Int): Int {
        return ((x ?: 0).toFloat() *(1080.0F/(screenWidth!!).toFloat())).toInt()

    }
    fun imaginaryY(y : Int): Int {
        return ((y ?: 0).toFloat() *(1920.0F/(screenHeight!!).toFloat())).toInt()

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
                for (block in visionText.textBlocks) {
                    val text = block.text
                    val rect = block.boundingBox
                    if(text == "+150 points"){
                        is150 = true
                        rect150 = rect
                    }else if(text == "+200 points"){
                        is200 = true
                        rect200 = rect
                    }else if(text == "+300 points"){
                        is300 = true
                        rect300 = rect
                    }else if(text == "+100 points"){
                        rect100 = rect
                    }
                }
                for (block in visionText.textBlocks) {
                    val text = block.text
                    val rect = block.boundingBox
                    var real_y = realY(rect)
                    var real_x = realX(rect)
                    if (text == "New Game") {
                        AutoService.instance.click(real_x, real_y)
                        //println("${real_x} " + real_y)
                        etape = 1
                    }
                    if (text == "Start" || text == "Continue") {
                        AutoService.instance.click(real_x, real_y)
                    }
                    if (text == "Select difficulty") {
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
                    }
                    if (text == "Notes" && etape < 3) {
                        Thread.sleep(2000)
                        etape = 3
                        makeSudoku(bitmap)
                        //println("\n\n\n\n\n\n\n\nHey it's okay it's here --> "+s.contentDeepToString())
                        // Passage à l'étape 4
                        // ICI appeler fonction sudoku(bitmap) qui passe etape à 4
                    }
                    println("Texte trouvé: $text dans $rect")
                    // Tu peux détecter ici les boutons ou chiffres
                }
                println(etape)

            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }


    fun makeSudoku(bitmap : Bitmap) {
        etape = 4
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val y = imaginaryY(((screenHeight?.toFloat())!!.times(0.15756302F)).toInt()+100*is_thomas)
        val x = imaginaryX(((screenWidth?.toFloat())!!.times(0.02347417F)).toInt())
        //val width = imaginaryX(((screenWidth?.toFloat())!!.times(0.9577464F)).toInt())
        val height = imaginaryY(((screenHeight?.toFloat())!!.times(0.4264705F)).toInt())
        val width = height
        //val height = width
        println("Ici : $x $y $width $height")

        //moveButton(x, realY(y))
        val centerXs = Array(9) { IntArray(9) }
        val centerYs = Array(9) { IntArray(9) }
        //Ultra responsive process de la mort qui tue (Pas testé)
        val gridHeight = endY-beginY
        val gridWidth = endX-beginX
        val cellWidth = gridWidth/9f
        val cellHeight = gridHeight/9f
        for (i in 0..8) {
            for (j in 0..8) {
                centerXs[i][j] = (x + cellWidth * j + cellWidth/2).toInt()
                centerYs[i][j] = (y + cellHeight * i + cellHeight/2).toInt()
            }
        }
        recognizer.process(image)
            .addOnSuccessListener({ visionText ->
                val grid = Array(9) { IntArray(9) }

                val cellWidth = width / 9f
                val cellHeight = height / 9f


                for (block in visionText.textBlocks)
                    for (line in block.lines)
                        for (element in line.elements) {

                            val rect = element.boundingBox ?: continue
                            val text = element.text

                            for (i in text.indices) {

                                val c = text[i]

                                if (c !in '1'..'9') continue

                                // largeur d’un caractère dans ce block
                                val charWidth = rect.width() / text.length.toFloat()

                                val centerX = rect.left + charWidth * (i + 0.5f)
                                val centerY = rect.centerY()

                                val col = ((centerX - x) / cellWidth).toInt()-1
                                val row = ((centerY - y) / cellHeight).toInt()

                                if (row in 0..8 && col in 0..8) {

                                    grid[row][col] = c.toString().toInt()

                                    println("Placed $c at [$row][$col]")
                                }
                            }
                        }
                println("\n\n " + grid.contentDeepToString())
                val solved = Array(9) { i ->
                    grid[i].clone()
                }
                if(solve(solved)){
                    println("\n\n " + solved.contentDeepToString())
                    val height = imaginaryY(((screenHeight?.toFloat())!!.times(0.0525210F)).toInt())
                    val y = imaginaryY(((screenHeight?.toFloat())!!.times(0.86974789F)).toInt()) - height
                    val width = imaginaryX((screenWidth?.toFloat())!!.times(0.11111111F).toInt())
                    println("\n\n sd" + centerXs.contentDeepToString())
                    CoroutineScope(Dispatchers.IO).launch {

                        click_on_solve_and_do_the_difference(grid, solved, height, y, width, centerXs, centerYs)

                    }


                }else{
                    println("PAS SOLVE")
                    etape = 2
                }
            })
    }


    suspend fun click_on_solve_and_do_the_difference(
        grid: Array<IntArray>,
        solved: Array<IntArray>,
        height: Int,
        y: Int,
        width: Int,
        centerXs: Array<IntArray>,
        centerYs: Array<IntArray>
    ) {
        println("\n\nqs " + centerXs.contentDeepToString())
        for (n in 1..9) {

            AutoService.instance.click(
                realX((n-1)*width + width/2),
                realY(y+height/2)
            )

            delay(3000)

            for (i in 0..8) {
                for (j in 0..8) {
                    println("JKF")
                    if (grid[i][j] != solved[i][j] && solved[i][j] == n) {
                        println("FOCK")
                        val handler = Handler(Looper.getMainLooper())
                        AutoService.instance.click(
                            realX(centerXs[i][j]),
                            realY(centerYs[i][j])
                        )
                        handler.post {

                            moveButton(
                                realX(centerXs[i][j]),
                                realY(centerYs[i][j])
                            )
                        }

                        delay(1000)
                    }
                }
            }
        }

        etape = 4
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
