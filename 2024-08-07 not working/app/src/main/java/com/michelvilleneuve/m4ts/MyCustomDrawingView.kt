package com.michelvilleneuve.m4ts

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.text.InputType
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlin.math.pow
import kotlin.math.sqrt
import android.os.Parcel
import kotlin.math.max
import kotlin.math.min
import com.google.gson.Gson
import java.io.File
import java.io.Serializable
import android.graphics.PointF
import android.util.Log
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Log.d("TAG", "Current Drawing Mode: $currentDrawingMode")

enum class DrawingMode {
    NONE,FREEHAND, AUTO, CIRCLE, ARC, RECTANGLE, TEXT, ERASE, ERASER_EFFECT
}

enum class Handle {
    START, END, MIDDLE
}

fun Parcel.readPointF(): PointF {
    val x = readFloat()
    val y = readFloat()
    return PointF(x, y)
}

// Extension function to write PointF to a Parcel
fun Parcel.writePointF(point: PointF) {
    writeFloat(point.x)
    writeFloat(point.y)
}


@SuppressLint("ClickableViewAccessibility")
class MyCustomDrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var currentMode: DrawingMode = DrawingMode.NONE
    private val handler = Handler(Looper.getMainLooper())

    private var path = Path()
    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
 //   private val textPaint = Paint().apply {
 //       color = Color.BLACK
 //       textSize = 40f // Default text size
 //       isAntiAlias = true
 //   }


 //   var textSize = 40f
 //       set(value) {
 //           field = value
 //           textPaint.textSize = value
 //       }

    private val eraserEffectPath = Path()

    private val eraserEffectPaint = Paint().apply {
        color = Color.WHITE // Color of the canvas background
        strokeWidth = 50f // Adjust this to change the size of the eraser
        style = Paint.Style.STROKE
    }


    var lines: MutableList<Line> = mutableListOf()
    var texts: MutableList<Text> = mutableListOf()
    var circles: MutableList<Circle> = mutableListOf()
    var arcs: MutableList<Arc> = mutableListOf()
    var rectangles: MutableList<Rectangle> = mutableListOf()
//    val textElements = mutableListOf<TextElement>()
    private val eraserTrail = mutableListOf<PointF>()
    private val shapes: MutableList<Shape> = mutableListOf()


    private var startX = 0f
    private var startY = 0f
    private var snapRadius = 50f
    private var lastX = 0f
    private var lastY = 0f

    private val freehandPath = Path()
    private val freehandLines = mutableListOf<Line>()
    private val paths = mutableListOf<Path>()

    private val densityDpi = resources.displayMetrics.densityDpi.toFloat()

    //   private val sheetWidth = inchesToPixels(8.5f, densityDpi)
    //   private val sheetHeight = inchesToPixels(11f, densityDpi)
    //   val inchToPixel = resources.displayMetrics.densityDpi / 160f

    val sheetWidthInInches = 8.5f
    val sheetHeightInInches = 11f


    val inchToPixel = resources.displayMetrics.xdpi // Use xdpi for more accurate conversion

    //  val sheetWidth = (sheetWidthInInches * inchToPixel).toInt()
    //  val sheetHeight = (sheetHeightInInches * inchToPixel).toInt()
    val spacing = (0.25f * inchToPixel).toInt()
// Ensure spacing is positive

    private var sheetWidth = 8.5f * 72f // Example width in points (8.5 inches * 72 points per inch)
    private var sheetHeight = 11f * 72f // Example height in points (11 inches * 72 points per inch)


    private val borderPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private var scale = 1.0f
    private var currentPageNumber: Int = 1
    private val canvasWidthInPixels = 8.5f * resources.displayMetrics.xdpi
    private val canvasHeightInPixels = 11f * resources.displayMetrics.ydpi
    private var timerRunnable: Runnable? = null

    private var translationX = 0f
    private var translationY = 0f
    private val matrix = Matrix()

    private var currentX = 0f
    private var currentY = 0f

    private var selectedLine: Line? = null
    private var selectedRectangle: Rectangle? = null
    private var selectedCircle: Circle? = null
    private var selectedArc: Arc? = null
    private var selectedHandle: Handle? = null
    private var selectedText: Text? = null
    enum class Handle {
        START, END, MIDDLE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, RIGHT, TOP, BOTTOM
    }
  //  private enum class Handle { START, MIDDLE, END, LEFT_TOP, RIGHT_TOP, LEFT_BOTTOM, RIGHT_BOTTOM,
 //       TOP, RIGHT, BOTTOM, LEFT }
    private val handleRadius = 20f

    private val gestureDetector = GestureDetector(context, GestureListener())
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPressTriggered = false
    private var isDrawingInProgress = false
    private val longPressTimeout = 5000 // 3 second
    private var areGripsDisplayed = false

    private var canvasWidth: Int
    private var canvasHeight: Int
    var selectedShape: Shape? = null

    data class PointF(
        val x: Float,
        val y: Float
    )

    interface Grippable {
        fun getGrips(): List<PointF>
    }

    sealed class Shape {
        abstract val color: Int
        abstract val locked: Boolean
    }

    data class Line(
        var startX: Float,
        var startY: Float,
        var endX: Float,
        var endY: Float,
        override var locked: Boolean = false,
        override var color: Int = Color.BLACK
    ) : Shape(), Grippable {
        fun getMidPoint(): Pair<Float, Float> {
            return Pair((startX + endX) / 2, (startY + endY) / 2)
        }

        fun contains(x: Float, y: Float): Boolean {
            val buffer = 20 // The buffer distance within which a point is considered on the line

            // Calculate distance from point (x, y) to the line segment (startX, startY) - (endX, endY)
            val distance = Math.abs((endY - startY) * x - (endX - startX) * y + endX * startY - endY * startX) /
                    Math.sqrt(((endY - startY).toDouble() * (endY - startY) + (endX - startX).toDouble() * (endX - startX)).toFloat()
                        .toDouble())

            return distance <= buffer
        }

        override fun getGrips(): List<PointF> {
            return listOf(PointF(startX, startY), PointF(endX, endY))
        }
    }

    data class Rectangle(
        var startX: Float,
        var startY: Float,
        var endX: Float,
        var endY: Float,
        var left: Float = 0f,
        var top: Float = 0f,
        var right: Float = 0f,
        var bottom: Float = 0f,
        override var locked: Boolean = false,
        override var color: Int = Color.BLACK
    ) : Shape(), Grippable {

        fun contains(x: Float, y: Float): Boolean {
            return x in left..right && y in top..bottom
        }

        fun updateBounds() {
            left = minOf(startX, endX)
            top = minOf(startY, endY)
            right = maxOf(startX, endX)
            bottom = maxOf(startY, endY)
        }

        override fun getGrips(): List<PointF> {
            return listOf(
                PointF(left, top), PointF(right, top),
                PointF(left, bottom), PointF(right, bottom)
            )
        }
    }

    data class Circle(
        var centerX: Float,
        var centerY: Float,
        var radius: Float,
        override var locked: Boolean = false,
        override var color: Int = Color.BLACK
    ) : Shape(), Grippable {
        fun contains(x: Float, y: Float): Boolean {
            val distance =
                Math.sqrt(((x - centerX) * (x - centerX) + (y - centerY) * (y - centerY)).toDouble())
            return distance <= radius
        }

        override fun getGrips(): List<PointF> {
            return listOf(
                PointF(centerX - radius, centerY),
                PointF(centerX + radius, centerY),
                PointF(centerX, centerY - radius),
                PointF(centerX, centerY + radius)
            )
        }
    }

    data class Arc(
        var centerX: Float,
        var centerY: Float,
        var radiusX: Float,
        var radiusY: Float,
        var startAngle: Float,
        var sweepAngle: Float,
        override var locked: Boolean = false,
        override var color: Int = Color.BLACK
    ) : Shape(), Grippable {
        override fun getGrips(): List<PointF> {
            return listOf(
                PointF(centerX - radiusX, centerY),
                PointF(centerX + radiusX, centerY),
                PointF(centerX, centerY - radiusY),
                PointF(centerX, centerY + radiusY)
            )
        }
    }

    data class Text(
        var text: String,
        var x: Float,
        var y: Float,
        var width: Float,
        var height: Float,
        override var locked: Boolean = false,
        override var color: Int = Color.BLACK
    ) : Shape()

    data class Freehand(
        val points: MutableList<PointF> = mutableListOf(),
        override var locked: Boolean = false,
        override var color: Int = Color.BLACK
    ) : Shape(), Grippable {
        override fun getGrips(): List<PointF> {
            return points
        }
    }

    private val gson: Gson = Gson()

    // Example of deserializing a list of shapes
    val shapeType = object : TypeToken<List<Shape>>() {}.type
    val shapeClass = Shape::class.java

    class ShapeTypeAdapter : JsonSerializer<Shape>, JsonDeserializer<Shape> {
        override fun serialize(
            src: Shape?,
            typeOfSrc: Type?,
            context: JsonSerializationContext?
        ): JsonElement? {
            val jsonObject = context?.serialize(src)?.asJsonObject
 //           jsonObject?.addProperty("type", src::class.java.simpleName)
            return jsonObject
        }

        override fun deserialize(
            json: JsonElement?,
            typeOfT: Type?,
            context: JsonDeserializationContext?
        ): Shape? {
            val jsonObject = json?.asJsonObject
            val type = jsonObject?.get("type")?.asString
            val actualType = when (type) {
                "Line" -> Line::class.java
                "Rectangle" -> Rectangle::class.java
                "Circle" -> Circle::class.java
                "Arc" -> Arc::class.java
                "Text" -> Text::class.java
                "Freehand" -> Freehand::class.java
                else -> throw IllegalArgumentException("Unknown type: $type")
            }
            return context?.deserialize(jsonObject, actualType)
        }
    }

    private var scaleFactor = 1f
    private var lastScaleFactor = 1f
    private var focusX = 0f
    private var focusY = 0f
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var isScaling = false
    private var originalDrawingMode: DrawingMode = DrawingMode.NONE
    private var currentDrawingMode: DrawingMode = DrawingMode.NONE
    private var isMultiTouch = false
    private var isDrawing: Boolean = false
    private val snapAngle: Double = 30.0 // Snap angle in degrees
    private val SNAP_THRESHOLD_ANGLE = Math.toRadians(10.0) // 10 degrees
    private var initialPointer1X = 0f
    private var initialPointer1Y = 0f
    private var initialPointer2X = 0f
    private var initialPointer2Y = 0f
    private var lastPointer1X = 0f
    private var lastPointer1Y = 0f
    private var lastPointer2X = 0f
    private var lastPointer2Y = 0f
    private var isHandleMode = false
    private var isEraseMode = false
    private var isSnapping = false
    private var snapPointX = 0f
    private var snapPointY = 0f
    private val snapIndicatorRadius = 10f
    private val snapIndicatorPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val gridSpacing: Float = 20f // Example grid spacing value, adjust as needed

    fun enterEraseMode() {
        isEraseMode = true
    }

    val snapThresholdInches = 0.125f // 1/8th of an inch
    val displayMetrics = resources.displayMetrics
    val dpi = displayMetrics.densityDpi
    val threshold = snapThresholdInches * dpi


    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount
        val action = event.actionMasked

        val x = (event.x - translationX) / scaleFactor
        val y = (event.y - translationY) / scaleFactor
        val snappedPoint = snapToGrid(x, y)


        if (pointerCount > 1) {
            handleMultiTouchEvent(event, action)
        } else {
            handleSingleTouchEvent(event, action, snappedPoint)
        }

        return true
    }

    private fun handleMultiTouchEvent(event: MotionEvent, action: Int) {
        val pointer1Index = event.findPointerIndex(0)
        val pointer2Index = event.findPointerIndex(1)

        val pointer1X = event.getX(pointer1Index)
        val pointer1Y = event.getY(pointer1Index)
        val pointer2X = event.getX(pointer2Index)
        val pointer2Y = event.getY(pointer2Index)

        when (action) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    initialPointer1X = pointer1X
                    initialPointer1Y = pointer1Y
                    initialPointer2X = pointer2X
                    initialPointer2Y = pointer2Y
                    lastPointer1X = pointer1X
                    lastPointer1Y = pointer1Y
                    lastPointer2X = pointer2X
                    lastPointer2Y = pointer2Y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2) {
                    val deltaX1 = pointer1X - lastPointer1X
                    val deltaY1 = pointer1Y - lastPointer1Y
                    val deltaX2 = pointer2X - lastPointer2X
                    val deltaY2 = pointer2Y - lastPointer2Y

                    val averageDeltaX = (deltaX1 + deltaX2) / 2
                    val averageDeltaY = (deltaY1 + deltaY2) / 2

                    val speedFactor = 2.0f  // Increase this factor to make scrolling faster
                    translationX += averageDeltaX * speedFactor / scaleFactor
                    translationY += averageDeltaY * speedFactor / scaleFactor

                    lastPointer1X = pointer1X
                    lastPointer1Y = pointer1Y
                    lastPointer2X = pointer2X
                    lastPointer2Y = pointer2Y

                    invalidate()
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    lastPointer1X = pointer1X
                    lastPointer1Y = pointer1Y
                    lastPointer2X = pointer2X
                    lastPointer2Y = pointer2Y
                }
            }
        }
    }

    private fun handleSingleTouchEvent(event: MotionEvent, action: Int, snappedPoint: PointF) {
        Log.d(TAG, "$action")
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                selectShape(snappedPoint.x, snappedPoint.y)
                Log.d(TAG, "$currentMode")
                if (currentDrawingMode != DrawingMode.NONE) {
                    isDrawing = true
                    startX = snappedPoint.x
                    startY = snappedPoint.y
                    currentX = snappedPoint.x
                    currentY = snappedPoint.y
                    path.moveTo(startX, startY)
                    when (currentDrawingMode) {
                        DrawingMode.ERASE -> eraseElement(startX, startY)
                        DrawingMode.ERASER_EFFECT -> eraserEffectPath.moveTo(startX, startY)
                        DrawingMode.TEXT -> {
                            val text = "Sample Text" // Replace with your text input logic
                            val textSize = 16f // Replace with your text size logic
                            shapes.add(Text(text, startX, startY, textSize, textSize)) // Assuming width and height are equal to textSize
                        }
                        DrawingMode.FREEHAND -> {
                            freehandPath.moveTo(startX, startY)
                            freehandLines.clear()
                        }
                        else -> { /* no-op */ }
                    }
                } else {
                    if (selectedHandle != null) {
                        handleActionDown(event, snappedPoint.x, snappedPoint.y)
                        invalidate()
                    }
                }

                // Reset long press flag and schedule long press check
                isLongPressTriggered = false
                handler.postDelayed({
                    if (!isLongPressTriggered && !isDrawing) {
                        isLongPressTriggered = true
                        handleLongPress(event)
                    }
                }, longPressTimeout.toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentX = snappedPoint.x
                    currentY = snappedPoint.y

                    handleEdgeProximity(event.x, event.y)

                    when (currentDrawingMode) {
                        DrawingMode.FREEHAND -> {
                            path.lineTo(snappedPoint.x, snappedPoint.y)
                            freehandPath.lineTo(snappedPoint.x, snappedPoint.y)
                            freehandLines.add(Line(startX, startY, snappedPoint.x, snappedPoint.y))
                            startX = snappedPoint.x
                            startY = snappedPoint.y
                        }
                        DrawingMode.ERASER_EFFECT -> {
                            eraserEffectPath.lineTo(snappedPoint.x, snappedPoint.y)
                            eraseElementsAlongPath(eraserEffectPath)
                        }
                        else -> {
                            lastX = snappedPoint.x
                            lastY = snappedPoint.y
                        }
                    }
                    invalidate()
                }

                if (selectedHandle != null) {
                    handleActionMove(event, snappedPoint.x, snappedPoint.y)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDrawing) {
                    val snappedEndPoint = findNearestEndpoint(currentX, currentY) ?: PointF(currentX, currentY)
                    currentX = snappedEndPoint.x
                    currentY = snappedEndPoint.y

                    when (currentDrawingMode) {
                        DrawingMode.AUTO -> {
                            val snappedLine = snapToHorizontalOrVertical(startX, startY, currentX, currentY)
                            shapes.add(Line(startX, startY, snappedLine.x, snappedLine.y))
                        }
                        DrawingMode.RECTANGLE -> {
                            val left = min(startX, currentX)
                            val top = min(startY, currentY)
                            val right = max(startX, currentX)
                            val bottom = max(startY, currentY)
                            rectangles.add(Rectangle(startX, startY, currentX, currentY, left, top, right, bottom))
                        }

                        DrawingMode.CIRCLE -> {
                            val radius = distance(startX, startY, currentX, currentY)
                            shapes.add(Circle(startX, startY, radius))
                        }
                        DrawingMode.ARC -> {
                            val (radiusX, radiusY) = calculateRadii(startX, startY, currentX, currentY)
                            val centerX = (startX + currentX) / 2
                            val centerY = (startY + currentY) / 2
                            shapes.add(Arc(centerX, centerY, radiusX, radiusY, 0f, 180f))
                        }
                        DrawingMode.FREEHAND -> {
                            path.lineTo(snappedPoint.x, snappedPoint.y)
                            freehandPath.lineTo(snappedPoint.x, snappedPoint.y)
                            freehandLines.add(Line(startX, startY, snappedPoint.x, snappedPoint.y))
                        }

                        else -> {}
                    }

                    isDrawing = false
                    invalidate()
                }

                if (selectedHandle != null) {
                    handleActionUp(event, snappedPoint.x, snappedPoint.y)
                    invalidate()
                }
            }
        }
    }





    private fun resetDrawingState() {
        isDrawing = false
        currentDrawingMode = DrawingMode.NONE
        selectedHandle = null
        selectedText = null
        startX = 0f
        startY = 0f
        lastX = 0f
        lastY = 0f
        invalidate()
    }

  //  private fun addText(x: Float, y: Float, text: String) {
  //      textElements.add(TextElement(x, y, text))
  //      invalidate()
  //  }

    private fun calculateAngle(centerX: Float, centerY: Float, pointX: Float, pointY: Float): Float {
        val angle = Math.toDegrees(Math.atan2((pointY - centerY).toDouble(), (pointX - centerX).toDouble())).toFloat()
        return if (angle < 0) angle + 360 else angle
    }

    private fun handleEdgeProximity(x: Float, y: Float) {
        val edgeThreshold = 50  // The distance from the edge to trigger scrolling
        val speedFactor = 2.0f  // Increase this factor to make scrolling faster

        if (x < edgeThreshold) {
            translationX += (edgeThreshold - x) * speedFactor / scaleFactor
        } else if (x > width - edgeThreshold) {
            translationX -= (x - (width - edgeThreshold)) * speedFactor / scaleFactor
        }

        if (y < edgeThreshold) {
            translationY += (edgeThreshold - y) * speedFactor / scaleFactor
        } else if (y > height - edgeThreshold) {
            translationY -= (y - (height - edgeThreshold)) * speedFactor / scaleFactor
        }
    }

    private fun checkForHandleSelection(x: Float, y: Float) {

        for (line in lines) {
            if (isCloseToHandle(line.startX, line.startY, x, y)) {
                selectedLine = line
                selectedHandle = Handle.START
                break
            } else if (isCloseToHandle(line.endX, line.endY, x, y)) {
                selectedLine = line
                selectedHandle = Handle.END
                break
            } else {
                val (midX, midY) = line.getMidPoint()
                if (isCloseToHandle(midX, midY, x, y)) {
                    selectedLine = line
                    selectedHandle = Handle.MIDDLE
                    break
                }
            }
        }

        for (rectangle in rectangles) {
            if (isCloseToHandle(rectangle.startX, rectangle.startY, x, y)) {
                selectedRectangle = rectangle
                selectedHandle = Handle.START
                break
            } else if (isCloseToHandle(rectangle.endX, rectangle.endY, x, y)) {
                selectedRectangle = rectangle
                selectedHandle = Handle.END
                break
            } else {
                val midX = (rectangle.startX + rectangle.endX) / 2
                val midY = (rectangle.startY + rectangle.endY) / 2
                if (isCloseToHandle(midX, midY, x, y)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.MIDDLE
                    break
                }
            }
        }

        for (circle in circles) {
            val handleX = circle.centerX + circle.radius
            if (isCloseToHandle(handleX, circle.centerY, x, y)) {
                selectedCircle = circle
                selectedHandle = Handle.END
                break
            } else if (isCloseToHandle(circle.centerX, circle.centerY, x, y)) {
                selectedCircle = circle
                selectedHandle = Handle.START
                break
            }
        }

        selectedText = selectText(x, y)
        resetSelections()

    }

    private fun handleMoveSelection(x: Float, y: Float) {
        selectedLine?.let {
            when (selectedHandle) {
                Handle.START -> {
                    it.startX = x
                    it.startY = y
                }
                Handle.END -> {
                    it.endX = x
                    it.endY = y
                }
                Handle.MIDDLE -> {
                    val dx = x - (it.startX + it.endX) / 2
                    val dy = y - (it.startY + it.endY) / 2
                    it.startX += dx
                    it.startY += dy
                    it.endX += dx
                    it.endY += dy
                }
                else -> { /* no-op */ }
            }
        }

        selectedRectangle?.let {
            when (selectedHandle) {
                Handle.START -> {
                    it.startX = x
                    it.startY = y
                }
                Handle.END -> {
                    it.endX = x
                    it.endY = y
                }
                Handle.MIDDLE -> {
                    val dx = x - (it.startX + it.endX) / 2
                    val dy = y - (it.startY + it.endY) / 2
                    it.startX += dx
                    it.startY += dy
                    it.endX += dx
                    it.endY += dy
                }
                else -> { /* no-op */ }
            }
        }

        selectedCircle?.let {
            when (selectedHandle) {
                Handle.START -> {
                    it.centerX = x
                    it.centerY = y
                }
                Handle.END -> {
                    val dx = x - it.centerX
                    it.radius = Math.abs(dx)
                }
                else -> { /* no-op */ }
            }
        }
        selectedText?.let {
            Handle.LEFT
            it.x = x
            it.y = y
            invalidate()
        }
    }

    fun resetSelections() {
        selectedLine = null
        selectedRectangle = null
        selectedCircle = null
        selectedArc = null
        selectedHandle = null
        selectedText = null
        isLongPressTriggered = false
        currentDrawingMode = DrawingMode.NONE
    }





    fun handleLongPress(e: MotionEvent) {

        val x = (e.x - translationX) / scaleFactor
        val y = (e.y - translationY) / scaleFactor

        var handleSelected = false

        // Check if a handle of a line, rectangle, or circle is selected
        for (line in lines) {
            if (isPointNearHandle(x, y, line.startX, line.startY)) {
                selectedLine = line
                selectedHandle = Handle.START
                handleSelected = true
                break
            } else if (isPointNearHandle(x, y, line.endX, line.endY)) {
                selectedLine = line
                selectedHandle = Handle.END
                handleSelected = true
                break
            } else if (isPointNearHandle(x, y, (line.startX + line.endX) / 2, (line.startY + line.endY) / 2)) {
                selectedLine = line
                selectedHandle = Handle.MIDDLE
                handleSelected = true
                break
            } else if (isPointNearLine(x, y, line)) {
                selectedLine = line
                selectedHandle = Handle.MIDDLE
                handleSelected = true
                break
            }
        }

        if (!handleSelected) {
            for (rectangle in rectangles) {
                if (isPointNearHandle(x, y, rectangle.startX, rectangle.startY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.TOP_LEFT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, rectangle.endX, rectangle.startY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.TOP_RIGHT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, rectangle.startX, rectangle.endY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.BOTTOM_LEFT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, rectangle.endX, rectangle.endY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.BOTTOM_RIGHT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, (rectangle.startX + rectangle.endX) / 2, (rectangle.startY + rectangle.endY) / 2)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.MIDDLE
                    handleSelected = true
                    break
                } else if (isPointNearRectangle(x, y, rectangle)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.MIDDLE
                    handleSelected = true
                    break
                }
            }
        }

        if (!handleSelected) {
            for (circle in circles) {
                if (isPointNearHandle(x, y, circle.centerX, circle.centerY - circle.radius)) {
                    selectedCircle = circle
                    selectedHandle = Handle.TOP
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX + circle.radius, circle.centerY)) {
                    selectedCircle = circle
                    selectedHandle = Handle.RIGHT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX, circle.centerY + circle.radius)) {
                    selectedCircle = circle
                    selectedHandle = Handle.BOTTOM
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX - circle.radius, circle.centerY)) {
                    selectedCircle = circle
                    selectedHandle = Handle.LEFT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX, circle.centerY)) {
                    selectedCircle = circle
                    selectedHandle = Handle.MIDDLE
                    handleSelected = true
                    break
                } else if (isPointNearCircle(x, y, circle)) {
                    selectedCircle = circle
                    selectedHandle = Handle.MIDDLE
                    handleSelected = true
                    break
                }
            }
        }

        if (!handleSelected) {
            for (text in texts) {
                val textElement = isPointNearText(x, y)
                if (textElement != null) {
                    selectedText = textElement
                    selectedHandle = Handle.LEFT
                    handleSelected = true
                    break
                }
            }
        }

        if (handleSelected) {
            isHandleMode = true
            invalidate()
        } else {
            // Reset selections if no handle is selected
             resetSelections()
        }
    }



    private fun isPointNearLine(x: Float, y: Float, line: Line): Boolean {
        val threshold = 10f
        val dx = line.endX - line.startX
        val dy = line.endY - line.startY
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return isPointNearHandle(x, y, line.startX, line.startY)
        val t = ((x - line.startX) * dx + (y - line.startY) * dy) / lengthSquared
        if (t < 0f) return false
        if (t > 1f) return false
        val nearX = line.startX + t * dx
        val nearY = line.startY + t * dy
        return isPointNearHandle(x, y, nearX, nearY)
    }

    private fun isPointNearCircle(x: Float, y: Float, circle: Circle): Boolean {
        val dx = x - circle.centerX
        val dy = y - circle.centerY
        val distanceToCenter = sqrt(dx * dx + dy * dy)

        // Check if point is near the center handle
        if (distanceToCenter <= threshold) return true

        // Check if point is near the top handle
        val topHandleDistance = sqrt((x - circle.centerX) * (x - circle.centerX) + (y - (circle.centerY - circle.radius)) * (y - (circle.centerY - circle.radius)))
        if (topHandleDistance <= threshold) return true

        // Check if point is near the right handle
        val rightHandleDistance = sqrt((x - (circle.centerX + circle.radius)) * (x - (circle.centerX + circle.radius)) + (y - circle.centerY) * (y - circle.centerY))
        if (rightHandleDistance <= threshold) return true

        // Check if point is near the bottom handle
        val bottomHandleDistance = sqrt((x - circle.centerX) * (x - circle.centerX) + (y - (circle.centerY + circle.radius)) * (y - (circle.centerY + circle.radius)))
        if (bottomHandleDistance <= threshold) return true

        // Check if point is near the left handle
        val leftHandleDistance = sqrt((x - (circle.centerX - circle.radius)) * (x - (circle.centerX - circle.radius)) + (y - circle.centerY) * (y - circle.centerY))
        if (leftHandleDistance <= threshold) return true

        // Check if point is near the circle's edge
        return abs(distanceToCenter - circle.radius) <= threshold
    }


    private fun isPointNearText(x: Float, y: Float): Text? {
        val touchPadding = 20 // Adjust this value as needed for easier selection
        return texts.find { text ->
            val textBounds = RectF(
                text.x - threshold,
                text.y - threshold,
                text.x + text.width + threshold,
                text.y + text.height + threshold
            )
            textBounds.contains(x, y)
        }
    }

    private fun isPointNearHandle(x: Float, y: Float, handleX: Float, handleY: Float): Boolean {
 //       val threshold = 20f // Adjust the threshold value as needed
        val dx = x - handleX
        val dy = y - handleY
        return dx * dx + dy * dy <= threshold * threshold
    }



    private fun findLineAtPoint(x: Float, y: Float): Line? {
        for (line in lines) {
            if (isCloseToHandle(line.startX, line.startY, x, y) || isCloseToHandle(line.endX, line.endY, x, y)) {
                return line
            }
        }
        return null
    }

    private fun findRectangleAtPoint(x: Float, y: Float): Rectangle? {
        for (rectangle in rectangles) {
            if (isCloseToHandle(rectangle.startX, rectangle.startY, x, y) || isCloseToHandle(rectangle.endX, rectangle.endY, x, y)) {
                return rectangle
            }
        }
        return null
    }

    private fun findCircleAtPoint(x: Float, y: Float): Circle? {
        for (circle in circles) {
            if (isCloseToHandle(circle.centerX, circle.centerY, x, y)) {
                return circle
            }
        }
        return null
    }

    private fun findTextAtPoint(x: Float, y: Float): Text? {
        for (text in texts) {
            if (isCloseToHandle(text.x, text.y, x, y)) {
                return text
            }
        }
        return null
    }

    private fun isCloseToHandle(handleX: Float, handleY: Float, x: Float, y: Float): Boolean {
        val handleRadius = 20f
        return (Math.abs(handleX - x) <= handleRadius && Math.abs(handleY - y) <= handleRadius)

    }



    private fun handleActionDown(event: MotionEvent, x: Float, y: Float) {

        if (isHandleMode) {
            resetSelections()
        }

        isDrawing = true

        val (snappedX, snappedY) = snapToGrid(x, y)
        startX = snappedX
        startY = snappedY
        lastX = snappedX
        lastY = snappedY

        selectedShape = findShapeAt(x, y)

        when (currentDrawingMode) {
            DrawingMode.AUTO -> {
                lines.add(Line(startX, startY, startX, startY, false, Color.BLACK))
            }
            DrawingMode.RECTANGLE -> {
                val left = min(startX, startY)
                val top = min(startY, currentY)
                val right = max(startX, currentX)
                val bottom = max(startY, currentY)

                rectangles.add(Rectangle(startX, startY, currentX, currentY))
            }
            DrawingMode.FREEHAND -> {
                path.moveTo(x, y)
                freehandPath.moveTo(x, y)
            }
            DrawingMode.ERASER_EFFECT -> {
                eraserEffectPath.moveTo(x, y)
            }
            DrawingMode.TEXT -> {
                // Handle text drawing
                val textElement = isPointNearText(x, y)
                if (textElement != null) {
                    selectedText = textElement
                }
            }
            DrawingMode.CIRCLE -> {
                val top = PointF(snappedX, snappedY - 0f)
                val right = PointF(snappedX + 0f, snappedY)
                val bottom = PointF(snappedX, snappedY + 0f)
                val left = PointF(snappedX - 0f, snappedY)

                val circle = Circle(snappedX, snappedY, 0f)
                circles.add(circle)
                selectedCircle = circle
            }
            else -> {

            }
        }
    }

    private fun handleActionMove(event: MotionEvent, x: Float, y: Float) {
        val x = (event.x - translationX) / scaleFactor
        val y = (event.y - translationY) / scaleFactor
        val (snappedX, snappedY) = snapToGrid(x, y)

        if (selectedShape != null && !isShapeLocked(selectedShape!!)) {
            moveShape(selectedShape!!, x, y)
            invalidate()
        }

        if (!isMultiTouch && currentDrawingMode != DrawingMode.NONE && selectedHandle == null && selectedText == null) {
            if (isDrawing) {
                currentX = snappedX
                currentY = snappedY

                val edgeThreshold = 50f
                if (event.x < edgeThreshold) {
                    translationX += 10 / scaleFactor
                } else if (event.x > width - edgeThreshold) {
                    translationX -= 10 / scaleFactor
                }
                if (event.y < edgeThreshold) {
                    translationY += 10 / scaleFactor
                } else if (event.y > height - edgeThreshold) {
                    translationY -= 10 / scaleFactor
                }

                when (currentDrawingMode) {
                    DrawingMode.FREEHAND -> {
                        path.lineTo(x, y)
                        freehandPath.lineTo(x, y)
                        freehandLines.add(Line(startX, startY, x, y))
                        startX = snappedX
                        startY = snappedY
                    }
                    DrawingMode.ERASER_EFFECT -> {
                        eraserEffectPath.lineTo(x, y)
                        eraseElementsAlongPath(eraserEffectPath)
                        eraserTrail.add(PointF(x, y))  // Add point to the trail
                        invalidate()
                    }
                    DrawingMode.AUTO -> {
                        val line = lines.last()
                        val (newEndX, newEndY) = snapToHorizontalOrVertical(line.startX, line.startY, snappedX, snappedY)
                        line.endX = newEndX
                        line.endY = newEndY
                        lastX = newEndX
                        lastY = newEndY
                    }
                    DrawingMode.RECTANGLE -> {
                        val rectangle = rectangles.last()
                        rectangle.endX = snappedX
                        rectangle.endY = snappedY
                        rectangle.updateBounds()
                        lastX = snappedX
                        lastY = snappedY
                    }
                    DrawingMode.CIRCLE -> {
                        val circle = circles.last()
                        circle.radius = distance(startX, startY, snappedX, snappedY)
                        lastX = snappedX
                        lastY = snappedY
                    }
                    else -> {
                        lastX = snappedX
                        lastY = snappedY
                    }
                }
                invalidate()
            }
        }

        if (selectedHandle != null) {
            selectedLine?.let {
                if (!it.locked) { // Check if the line is locked
                    when (selectedHandle) {
                        Handle.START -> {
                            val (newX, newY) = snapToHorizontalOrVertical(
                                snappedX,
                                snappedY,
                                it.endX,
                                it.endY
                            )
                            it.startX = newX
                            it.startY = newY
                        }

                        Handle.END -> {
                            val (newX, newY) = snapToHorizontalOrVertical(
                                it.startX,
                                it.startY,
                                snappedX,
                                snappedY
                            )
                            it.endX = newX
                            it.endY = newY
                        }

                        Handle.MIDDLE -> {
                            val dx = snappedX - (it.startX + it.endX) / 2
                            val dy = snappedY - (it.startY + it.endY) / 2
                            it.startX += dx
                            it.startY += dy
                            it.endX += dx
                            it.endY += dy
                        }

                        else -> {}
                    }
                    invalidate()
                }
            }

            selectedRectangle?.let {
                if (!it.locked) { // Check if the rectangle is locked
                    when (selectedHandle) {
                        Handle.TOP_LEFT -> {
                            it.startX = snappedX
                            it.startY = snappedY
                        }

                        Handle.TOP_RIGHT -> {
                            it.endX = snappedX
                            it.startY = snappedY
                        }

                        Handle.BOTTOM_LEFT -> {
                            it.startX = snappedX
                            it.endY = snappedY
                        }

                        Handle.BOTTOM_RIGHT -> {
                            it.endX = snappedX
                            it.endY = snappedY
                        }

                        Handle.MIDDLE -> {
                            val dx = snappedX - (it.startX + it.endX) / 2
                            val dy = snappedY - (it.startY + it.endY) / 2
                            it.startX += dx
                            it.startY += dy
                            it.endX += dx
                            it.endY += dy
                        }

                        else -> {}
                    }
                    invalidate()
                }
            }
            selectedCircle?.let {
                if (!it.locked) { // Check if the circle is locked
                    when (selectedHandle) {
                        Handle.TOP -> {
                            it.radius = Math.abs(it.centerY - snappedY)
                        }

                        Handle.RIGHT -> {
                            it.radius = Math.abs(it.centerX - snappedX)
                        }

                        Handle.BOTTOM -> {
                            it.radius = Math.abs(it.centerY - snappedY)
                        }

                        Handle.LEFT -> {
                            it.radius = Math.abs(it.centerX - snappedX)
                        }

                        Handle.MIDDLE -> {
                            it.centerX = snappedX
                            it.centerY = snappedY
                        }

                        else -> {}
                    }
                    invalidate()
                }
            }
        }
        selectedText?.let {
            it.x = snappedX
            it.y = snappedY
            invalidate()
        }
    }

    private fun handleActionUp(event: MotionEvent, x: Float, y: Float) {

        val x = (event.x - translationX) / scaleFactor
        val y = (event.y - translationY) / scaleFactor


        val (snappedX, snappedY) = snapToGrid(x, y)
        startX = snappedX
        startY = snappedY
        lastX = snappedX
        lastY = snappedY

        if (isDrawing) {
            isDrawing = false

            when (currentDrawingMode) {
                DrawingMode.AUTO -> {
                    val line = lines.last()
                    line.endX = snappedX
                    line.endY = snappedY
                }

                DrawingMode.RECTANGLE -> {
                    val rectangle = rectangles.last()
                    rectangle.endX = snappedX
                    rectangle.endY = snappedY
                    rectangle.updateBounds()
                }

                DrawingMode.FREEHAND -> {
                    freehandPath.lineTo(x, y)
                    path.lineTo(x, y)
                }

                DrawingMode.ERASER_EFFECT -> {
                    // Finalize erasing effect if necessary
                }

                DrawingMode.CIRCLE -> {
                    val circle = circles.last()
                    val radius = distance(circle.centerX, circle.centerY, snappedX, snappedY)
                    circle.radius = radius
                }

                else -> {}
            }
               invalidate()
                return
      //      }
        }

        if (selectedHandle != null) {
            selectedHandle = null
        }

     //   resetSelections()
    }

    private fun findHandleAt(x: Float, y: Float): Handle? {
        // Implement logic to find a handle at the given coordinates

        return null
    }

    init {
        // Set the canvas size based on the device type
        if (isTablet(context)) {
            canvasWidth = 1100
            canvasHeight = 850
        } else {
            canvasWidth = 850
            canvasHeight = 1100
        }
    }



    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(translationX, translationY)
        canvas.scale(scaleFactor, scaleFactor)

        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Draw the canvas border
        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), borderPaint)

        // Draw grid lines and other background elements here
        drawGraphLines(canvas)

        // Draw the shapes and elastic effect
        val lockedPaint = Paint(paint).apply {
            color = Color.DKGRAY // Color for locked shapes
        }

        val selectedPaint = Paint(paint).apply {
            color = Color.GREEN // Color for selected shape
        }
  //      Log.d(TAG, "before shapes: ")
        shapes.forEach { shape ->
            val paintToUse = when {
                shape.locked -> lockedPaint
                shape == selectedShape -> selectedPaint
                else -> Paint().apply {
                    color = shape.color
                    style = Paint.Style.STROKE // Ensure shapes are not filled
                }
            }
 //           Log.d(TAG, "$shape")
            when (shape) {
                is Line -> {
                    canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paintToUse)
                }
                is Rectangle -> {
                    canvas.drawRect(shape.left, shape.top, shape.right, shape.bottom, paintToUse)

                }
                is Circle -> {
                    paintToUse.style = Paint.Style.STROKE // Ensure circle is not filled
                    canvas.drawCircle(shape.centerX, shape.centerY, shape.radius, paintToUse)
                }
                is Arc -> {
                    val rectF = RectF(
                        shape.centerX - shape.radiusX,
                        shape.centerY - shape.radiusY,
                        shape.centerX + shape.radiusX,
                        shape.centerY + shape.radiusY
                    )
                    canvas.drawArc(rectF, shape.startAngle, shape.sweepAngle, false, paintToUse)
                }
                is Text -> {
                    val textPaint = Paint().apply {
                        color = shape.color
                        textSize = 40f
                    }
                    canvas.drawText(shape.text, shape.x, shape.y, textPaint)
                }
                is Freehand -> {
                    val path = Path()
                    if (shape.points.isNotEmpty()) {
                        path.moveTo(shape.points[0].x, shape.points[0].y)
                        for (i in 1 until shape.points.size) {
                            path.lineTo(shape.points[i].x, shape.points[i].y)
                        }
                        canvas.drawPath(path, paintToUse)
                    }
                }
            }
        }

        eraserTrail.forEach {
            canvas.drawCircle(it.x, it.y, 10f, eraserTrailPaint)
        }

        // Draw grips for selected shape
        if (selectedShape is Grippable) {
            val gripPaint = Paint().apply {
                color = Color.BLUE
                style = Paint.Style.FILL
            }
            (selectedShape as Grippable).getGrips().forEach { grip ->
                canvas.drawCircle(grip.x, grip.y, 10f, gripPaint)
            }
        }

        // Elastic effect for drawing
        if (isDrawing) {
            val paintElastic = Paint(paint).apply {
                color = Color.GRAY
                strokeWidth = 3f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            when (currentDrawingMode) {
                DrawingMode.AUTO -> {
                    val (snappedX, snappedY) = snapToHorizontalOrVertical(
                        startX,
                        startY,
                        currentX,
                        currentY
                    )
                    canvas.drawLine(startX, startY, snappedX, snappedY, paintElastic)
                }

                DrawingMode.RECTANGLE -> canvas.drawRect(
                    startX,
                    startY,
                    currentX,
                    currentY,
                    paintElastic
                )

                DrawingMode.CIRCLE -> {
                    val radius = distance(startX, startY, currentX, currentY)
                    canvas.drawCircle(startX, startY, radius, paintElastic)
                }

                DrawingMode.ARC -> {
                    val radius = distance(startX, startY, currentX, currentY)
                    val sweepAngle = calculateSweepAngle(startX, startY, currentX, currentY)
                    canvas.drawArc(
                        startX - radius,
                        startY - radius,
                        startX + radius,
                        startY + radius,
                        0f,
                        sweepAngle,
                        false,
                        paintElastic
                    )
                }

                DrawingMode.TEXT -> {
                    canvas.drawText(textToDraw, startX, startY, paintElastic)
                }
                else -> {}
            }
        }
        canvas.restore()
    }

    private var textToDraw: String = "Sample Text"

    private fun isPointNear(px: Float, py: Float, x: Float, y: Float, threshold: Float): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= threshold * threshold
    }

    private val eraserTrailPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val eraserTrailRadius = 10f  // Adjust the radius as needed

    private fun findArcCenterPoints(startX: Float, startY: Float, endX: Float, endY: Float, radius: Float): List<PointF> {
        val midX = (startX + endX) / 2
        val midY = (startY + endY) / 2
        val dx = endX - startX
        val dy = endY - startY
        val dist = distance(startX, startY, endX, endY) / 2

        if (dist > radius) {
            // No valid arc if the radius is less than half the distance between points
            return emptyList()
        }

        val h = Math.sqrt((radius * radius - dist * dist).toDouble()).toFloat()
        val offsetX = h * (dy / dist)
        val offsetY = h * (-dx / dist)

        val center1 = PointF(midX + offsetX, midY + offsetY)
        val center2 = PointF(midX - offsetX, midY - offsetY)

        return listOf(center1, center2)
    }

    private fun snapToHorizontalOrVertical(startX: Float, startY: Float, endX: Float, endY: Float): PointF {
        val deltaX = endX - startX
        val deltaY = endY - startY
        val threshold = 10 // You can adjust this threshold as needed

        return when {
            Math.abs(deltaX) < threshold -> PointF(startX, endY) // Vertical snap
            Math.abs(deltaY) < threshold -> PointF(endX, startY) // Horizontal snap
            else -> PointF(endX, endY) // No snap
        }
    }

    val gridSize = 18f // Example grid size in points (0.25 inches * 72 points per inch)

    private fun drawGraphLines(canvas: Canvas) {
        if (gridSize <= 0) {
            throw IllegalArgumentException("Grid size must be positive, was: $gridSize")
        }

        // Draw vertical lines
        var x = 0f
        while (x <= canvasWidth) {
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = when {
                    x % (gridSize * 8) == 0f -> Color.RED
                    x % (gridSize * 4) == 0f -> Color.BLUE
                    else -> Color.LTGRAY
                }
            }
            canvas.drawLine(x, 0f, x, canvasHeight.toFloat(), paint)
            x += gridSize
        }

        // Draw horizontal lines
        var y = 0f
        while (y <= canvasHeight) {
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = when {
                    y % (gridSize * 8) == 0f -> Color.RED
                    y % (gridSize * 4) == 0f -> Color.BLUE
                    else -> Color.LTGRAY
                }
            }
            canvas.drawLine(0f, y, canvasWidth.toFloat(), y, paint)
            y += gridSize
        }
    }


    private fun inchesToPixels(inches: Float, densityDpi: Float): Int {
        return (inches * densityDpi).toInt()
    }

    private fun addTextDirectly(x: Float, y: Float) {
        // Simulate a popup keyboard by requesting focus and showing the soft keyboard
        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            requestFocus()

        }
        editText.setOnEditorActionListener { v, actionId, event ->
            val text = editText.text.toString()
            if (text.isNotEmpty()) {

                texts.add(Text(text, x, y, width.toFloat(), height.toFloat()))
                invalidate()
                //           Toast.makeText(context, "Text added: $text", Toast.LENGTH_SHORT).show()
                hideKeyboard(editText)
            }
            true
        }
        // Add the EditText to the layout temporarily to show the keyboard
        val params = ViewGroup.LayoutParams(0, 0)
        (parent as ViewGroup).addView(editText, params)
        editText.requestFocus()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        showKeyboard(editText)
        setDrawingMode(DrawingMode.TEXT)
        DrawingMode.TEXT
    }

    private fun showKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.postDelayed({
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 0) // Delay to ensure proper focus handling in landscape mode
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        (parent as ViewGroup).removeView(view) // Remove the EditText from the layout
        this.requestFocus() // Request focus on the view to ensure the keyboard is hidden
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Adjust layout parameters or re-request focus on EditText if needed
            if (currentMode == DrawingMode.TEXT) {
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun eraseElement(x: Float, y: Float): Boolean {

        var elementErased = false


        // Iterate through lines
        val lineIterator = lines.iterator()
        while (lineIterator.hasNext()) {
            val line = lineIterator.next()
            if (isPointNearLine(x, y, line)) {
                lineIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through texts
        val textIterator = texts.iterator()
        while (textIterator.hasNext()) {
            val text = textIterator.next()
            if (isPointNearText(x, y) != null) {
                textIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through circles
        val circleIterator = circles.iterator()
        while (circleIterator.hasNext()) {
            val circle = circleIterator.next()
            if (isPointNearCircle(x, y, circle)) {
                circleIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through arcs
        val arcIterator = arcs.iterator()
        while (arcIterator.hasNext()) {
            val arc = arcIterator.next()
            if (isPointNearArc(x, y, arc)) {
                arcIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through rectangles
        val rectangleIterator = rectangles.iterator()
        while (rectangleIterator.hasNext()) {
            val rectangle = rectangleIterator.next()
            if (isPointNearRectangle(x, y, rectangle)) {
                rectangleIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through paths
        val pathIterator = paths.iterator()
        while (pathIterator.hasNext()) {
            val path = pathIterator.next()
            if (isPointNearPath(x, y, path)) {
                pathIterator.remove()
                elementErased = true
                break
            }
        }

        if (elementErased) {
            invalidate()
        }
        return elementErased
    }


    private fun distanceFromPointToLineSegment(x: Float, y: Float, start: PointF, end: PointF): Float {
        val lineLengthSquared = (end.x - start.x).pow(2) + (end.y - start.y).pow(2)
        if (lineLengthSquared == 0f) return distanceBetweenPoints(x, y, start.x, start.y)

        val t = ((x - start.x) * (end.x - start.x) + (y - start.y) * (end.y - start.y)) / lineLengthSquared
        val clampedT = t.coerceIn(0f, 1f)

        val projection = PointF(start.x + clampedT * (end.x - start.x), start.y + clampedT * (end.y - start.y))
        return distanceBetweenPoints(x, y, projection.x, projection.y)
    }

    private fun distanceBetweenPoints(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }

    private fun distanceFromPointToLine(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val A = px - x1
        val B = py - y1
        val C = x2 - x1
        val D = y2 - y1

        val dot = A * C + B * D
        val lenSq = C * C + D * D
        val param = if (lenSq != 0f) dot / lenSq else -1f

        val xx: Float
        val yy: Float

        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * C
            yy = y1 + param * D
        }

        val dx = px - xx
        val dy = py - yy
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }




    private fun isPointNearArc(x: Float, y: Float, arc: Arc): Boolean {
        val dx = x - arc.centerX
        val dy = y - arc.centerY

        // Calculate the angle of the point relative to the arc's center
        val pointAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val normalizedPointAngle = (pointAngle + 360) % 360

        // Calculate the radii for the given angle
        val angleInRadians = Math.toRadians(normalizedPointAngle.toDouble())
        val radiusX = arc.radiusX
        val radiusY = arc.radiusY
        val radiusAtPoint = (radiusX * radiusY) / sqrt((radiusY * cos(angleInRadians)).pow(2) + (radiusX * sin(angleInRadians)).pow(2))

        // Calculate the distance from the arc's center to the point
        val distance = sqrt(dx * dx + dy * dy)
   //     val threshold = 20 // Adjust this threshold value as needed

        // Check if the point is within the arc's sweep angle
        val startAngle = arc.startAngle
        val endAngle = (startAngle + arc.sweepAngle) % 360
        val isWithinAngles = if (startAngle < endAngle) {
            normalizedPointAngle in startAngle..endAngle
        } else {
            normalizedPointAngle >= startAngle || normalizedPointAngle <= endAngle
        }

        // Check if the point is near the calculated radius for the given angle
        val isWithinRadius = kotlin.math.abs(distance - radiusAtPoint) <= threshold

        return isWithinRadius && isWithinAngles
    }

    private fun isPointNearRectangle(x: Float, y: Float, rectangle: Rectangle): Boolean {
        val rectLeft = rectangle.startX.coerceAtMost(rectangle.endX)
        val rectRight = rectangle.startX.coerceAtLeast(rectangle.endX)
        val rectTop = rectangle.startY.coerceAtMost(rectangle.endY)
        val rectBottom = rectangle.startY.coerceAtLeast(rectangle.endY)

        val centerX = (rectLeft + rectRight) / 2
        val centerY = (rectTop + rectBottom) / 2

        // Threshold for considering a point near the rectangle
  //      val threshold = 20f

        // Check if the point is near any side of the rectangle
        val nearLeft = abs(x - rectLeft) <= threshold && y in rectTop..rectBottom
        val nearRight = abs(x - rectRight) <= threshold && y in rectTop..rectBottom
        val nearTop = abs(y - rectTop) <= threshold && x in rectLeft..rectRight
        val nearBottom = abs(y - rectBottom) <= threshold && x in rectLeft..rectRight

        // Check if the point is near the center of the rectangle
        val nearCenter = distanceBetweenPoints(x, y, centerX, centerY) <= threshold

        return nearLeft || nearRight || nearTop || nearBottom || nearCenter
    }

    private fun distanceToLine(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        val t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared
        val nearestX = if (t < 0) x1 else if (t > 1) x2 else x1 + t * dx
        val nearestY = if (t < 0) y1 else if (t > 1) y2 else y1 + t * dy
        return distance(px, py, nearestX, nearestY)
    }

    private fun isPointNearPath(x: Float, y: Float, path: Path): Boolean {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        // Adjust the hit detection logic as needed
        return bounds.contains(x, y)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
    }

    private fun getNearestSnapPoint(x: Float, y: Float): Pair<Float, Float> {
        var nearestX = x
        var nearestY = y
        var minDistance = Float.MAX_VALUE

        for (line in lines) {
            val startDistance = distance(x, y, line.startX, line.startY)
            if (startDistance < minDistance) {
                minDistance = startDistance
                nearestX = line.startX
                nearestY = line.startY
            }

            val endDistance = distance(x, y, line.endX, line.endY)
            if (endDistance < minDistance) {
                minDistance = endDistance
                nearestX = line.endX
                nearestY = line.endY
            }
        }

        return if (minDistance < snapRadius) Pair(nearestX, nearestY) else Pair(x, y)
    }

    private fun calculateSweepAngle(startX: Float, startY: Float, x: Float, y: Float): Float {
        val dx = x - startX
        val dy = y - startY
        return (Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())) % 360).toFloat()
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            originalDrawingMode = currentDrawingMode
            setDrawingMode(DrawingMode.NONE)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            setDrawingMode(DrawingMode.NONE)
       //     originalDrawingMode = currentDrawingMode
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = 0.1f.coerceAtLeast(scaleFactor.coerceAtMost(10.0f))
      //      setDrawingMode(DrawingMode.NONE)
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

 //       override fun onScroll(
  //          e1: MotionEvent?,
  //          e2: MotionEvent,
  //          distanceX: Float,
  //          distanceY: Float
  //      ): Boolean {
            //        focusX -= distanceX
            //        focusY -= distanceY
  //          translationX -= distanceX
  //          translationY -= distanceY
 //           invalidate()
  //          return true
   //     }
 //       override fun onDown(e: MotionEvent): Boolean {
 //       resetSelections()
 //       return true
 //       }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetSelections()
            zoomToFitPage()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
           isLongPressTriggered = true
     //       areGripsDisplayed = true
            isDrawing = false
            resetSelections()
            handleLongPress(e)

          }

    //    override fun onSingleTapUp(e: MotionEvent): Boolean {
    //       resetSelections()
    //       invalidate()
     //      return true
      //      }
    }

    fun clearCanvas() {
        path.reset()
        lines.clear()
        texts.clear()
        circles.clear()
        arcs.clear()
        rectangles.clear()
        invalidate()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.lines = lines
        savedState.texts = texts
        savedState.circles = circles
        savedState.arcs = arcs
        savedState.rectangles = rectangles

        return savedState


    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            lines = state.lines
            texts = state.texts
            circles = state.circles
            arcs = state.arcs
            rectangles = state.rectangles
        } else {
            super.onRestoreInstanceState(state)
        }
    }


    private fun calculateZoomToFitScaleFactor(): Float {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val canvasWidth = sheetWidth.toFloat()
        val canvasHeight = sheetHeight.toFloat()

        val scaleX = viewWidth / canvasWidth
        val scaleY = viewHeight / canvasHeight

        return minOf(scaleX, scaleY)
    }

    fun saveDrawingContent(filename: String) {
        val gson = Gson()
        val content = mapOf(
            "lines" to lines,
            "texts" to texts,
            "circles" to circles,
            "arcs" to arcs,
            "rectangles" to rectangles
        )
        val json = gson.toJson(content)
        val file = File(context.filesDir, filename)
        file.writeText(json)
    }

    fun loadDrawingContent(filename: String) {
        val file = File(context.filesDir, filename)
        if (file.exists()) {
            val json = file.readText()
            val gson = Gson()
            val content = gson.fromJson<Map<String, List<Any>>>(json, Map::class.java)
            lines = content["lines"] as MutableList<Line>
            texts = content["texts"] as MutableList<Text>
            circles = content["circles"] as MutableList<Circle>
            arcs = content["arcs"] as MutableList<Arc>
            rectangles = content["rectangles"] as MutableList<Rectangle>
            invalidate()
        }
    }

    fun setCurrentPageNumber(pageNumber: Int) {
        currentPageNumber = pageNumber
        invalidate() // Redraw the view
    }

    private fun drawPageNumber(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        setCurrentPageNumber(currentPageNumber)
        val pageNumberText = "$currentPageNumber"

        // Calculate the position for the text based on the canvas size
        val xPos = (width / 2).toFloat()
        val yPos =
            (height / 2 + canvasHeightInPixels / 2 - 50).toFloat() // Adjust this value to position the text

        //       Draw a border around the 8.5x11 canvas
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        val left = (width / 2 - canvasWidthInPixels / 2).toFloat()
        val top = (height / 2 - canvasHeightInPixels / 2).toFloat()
        val right = (width / 2 + canvasWidthInPixels / 2).toFloat()
        val bottom = (height / 2 + canvasHeightInPixels / 2).toFloat()
        canvas.drawRect(left, top, right, bottom, borderPaint)

        // Draw the page number text
        //    canvas.drawText(pageNumberText, xPos, yPos, paint)
    }



    fun setDrawingMode(mode: DrawingMode) {
        currentMode = mode
        currentDrawingMode = mode
        //      setDrawingModeWithTimeout(mode, 0) // Set timeout to 5 seconds (or any desired duration)
    }

    private fun startModeTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = Runnable {
            currentMode = DrawingMode.NONE
            invalidate()
        }
        handler.postDelayed(timerRunnable!!, 5000) // 5 seconds timeout
    }

    fun setScaleAndTranslation(scaleFactor: Float, translationX: Float, translationY: Float) {
        this.scaleFactor = scaleFactor
        this.translationX = translationX
        this.translationY = translationY
        matrix.setScale(scaleFactor, scaleFactor)
        matrix.postTranslate(translationX, translationY)
        invalidate()
    }

    //  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    //      super.onSizeChanged(w, h, oldw, oldh)
    //      zoomToFitPage()
    //  }

    fun zoomToFitPage() {
        // Calculate the scale factor to fit the page width
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val pageWidth = (canvasWidth + 5f)
        val pageHeight = (canvasHeight + 5f)

        val scaleX = viewWidth / pageWidth
        val scaleY = viewHeight / pageHeight
        scaleFactor = minOf(scaleX, scaleY)

        // Center the page within the view
        translationX = (viewWidth - pageWidth * scaleFactor) / 2
        translationY = (viewHeight - pageHeight * scaleFactor) / 2

        invalidate()
    }

    companion object {
        private const val SNAP_THRESHOLD = 10f // Adjust this value as needed
        private const val TAG = "DrawingView"
    }

    private fun findNearestEndpoint(x: Float, y: Float): PointF? {
        var nearestPoint: PointF? = null
        var minDistance = Float.MAX_VALUE

        lines.forEach { line ->
            val points = listOf(PointF(line.startX, line.startY), PointF(line.endX, line.endY))
            points.forEach { point ->
                val distance = distance(x, y, point.x, point.y)
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance
                    nearestPoint = point
                }
            }
        }

        rectangles.forEach { rect ->
            val points = listOf(PointF(rect.startX, rect.startY), PointF(rect.endX, rect.endY))
            points.forEach { point ->
                val distance = distance(x, y, point.x, point.y)
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance
                    nearestPoint = point
                }
            }
        }

        circles.forEach { circle ->
            val points = listOf(PointF(circle.centerX, circle.centerY))
            points.forEach { point ->
                val distance = distance(x, y, point.x, point.y)
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance
                    nearestPoint = point
                }
            }
        }

        return nearestPoint
    }

    private fun eraseElementsAlongPath(path: Path) {

        val eraserWidth = eraserEffectPaint.strokeWidth / 2

 //       val pathBounds = RectF()
 //       path.computeBounds(pathBounds, true)
 //       eraserEffectPaint

        // Remove lines that intersect with the eraser path
        val lineIterator = lines.iterator()
        while (lineIterator.hasNext()) {
            val line = lineIterator.next()
            if (isLineIntersectingPath(line, path, eraserWidth)) {
                lineIterator.remove()
            }
        }

        // Remove rectangles that intersect with the eraser path
        val rectIterator = rectangles.iterator()
        while (rectIterator.hasNext()) {
            val rect = rectIterator.next()
            if (isRectangleIntersectingPath(rect, path, eraserWidth)) {
                rectIterator.remove()
            }
        }

        // Remove circles that intersect with the eraser path
        val circleIterator = circles.iterator()
        while (circleIterator.hasNext()) {
            val circle = circleIterator.next()
            if (isCircleIntersectingPath(circle, path, eraserWidth)) {
                circleIterator.remove()
            }
        }

        // Remove arcs that intersect with the eraser path
        val arcIterator = arcs.iterator()
        while (arcIterator.hasNext()) {
            val arc = arcIterator.next()
            if (isArcIntersectingPath(arc, path, eraserWidth)) {
                arcIterator.remove()
            }
        }

        // Remove texts that intersect with the eraser path
        val textIterator = texts.iterator()
        while (textIterator.hasNext()) {
            val text = textIterator.next()
            if (isTextIntersectingPath(text, path, eraserWidth)) {
                textIterator.remove()
            }
        }

        // Remove freehand lines that intersect with the eraser path
        val freehandLineIterator = freehandLines.iterator()
        while (freehandLineIterator.hasNext()) {
            val freehandLine = freehandLineIterator.next()
            if (isLineIntersectingPath(freehandLine, path, eraserWidth)) {
                freehandLineIterator.remove()
            }
        }

        // Trigger a redraw of the canvas
        invalidate()
    }

    private fun isLineIntersectingPath(line: Line, path: Path, eraserWidth: Float): Boolean {
        val pathSegments = getPathSegments(path)
        for (segment in pathSegments) {
            if (doLineSegmentsIntersect(
                    PointF(line.startX, line.startY),
                    PointF(line.endX, line.endY),
                    segment.first,
                    segment.second,
                    eraserWidth
                )) {
                return true
            }
        }
        return false
    }



    private fun isRectangleIntersectingPath(rect: Rectangle, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        val rectBounds = RectF(rect.left, rect.top, rect.right, rect.bottom)
        return RectF.intersects(pathBounds, rectBounds)
    }

    private fun isCircleIntersectingPath(circle: Circle, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        val circleBounds = RectF(
            circle.centerX - circle.radius,
            circle.centerY - circle.radius,
            circle.centerX + circle.radius,
            circle.centerY + circle.radius
        )
        return pathBounds.intersect(circleBounds)
    }

    private fun isArcIntersectingPath(arc: Arc, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)

        // Use radiusX and radiusY for the bounding rectangle of the arc
        val arcBounds = RectF(
            arc.centerX - arc.radiusX,
            arc.centerY - arc.radiusY,
            arc.centerX + arc.radiusX,
            arc.centerY + arc.radiusY
        )
        return pathBounds.intersect(arcBounds)
    }


    private fun isTextIntersectingPath(text: Text, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        val textBounds = RectF(text.x, text.y - text.height, text.x + text.width, text.y)
        return pathBounds.intersect(textBounds)
    }

    private fun getPathSegments(path: Path): List<Pair<PointF, PointF>> {
        val segments = mutableListOf<Pair<PointF, PointF>>()
        val pathMeasure = PathMeasure(path, false)
        val coords = FloatArray(2)
        var startX = 0f
        var startY = 0f
        var distance = 0f
        var segmentStarted = false

        while (distance < pathMeasure.length) {
            pathMeasure.getPosTan(distance, coords, null)
            val x = coords[0]
            val y = coords[1]

            if (segmentStarted) {
                segments.add(PointF(startX, startY) to PointF(x, y))
            } else {
                segmentStarted = true
            }

            startX = x
            startY = y
            distance += 20f // Increment this value for more precision
        }

        return segments
    }

    private fun doLineSegmentsIntersect(
        p1: PointF, p2: PointF,
        q1: PointF, q2: PointF,
        eraserWidth: Float
    ): Boolean {
        // Implementation of line segment intersection algorithm with eraser width
        val expandedP1 = expandPoint(p1, eraserWidth)
        val expandedP2 = expandPoint(p2, eraserWidth)
        val expandedQ1 = expandPoint(q1, eraserWidth)
        val expandedQ2 = expandPoint(q2, eraserWidth)

        val o1 = orientation(expandedP1, expandedP2, expandedQ1)
        val o2 = orientation(expandedP1, expandedP2, expandedQ2)
        val o3 = orientation(expandedQ1, expandedQ2, expandedP1)
        val o4 = orientation(expandedQ1, expandedQ2, expandedP2)

        if (o1 != o2 && o3 != o4) {
            return true
        }

        if (o1 == 0 && onSegment(expandedP1, expandedQ1, expandedP2)) return true
        if (o2 == 0 && onSegment(expandedP1, expandedQ2, expandedP2)) return true
        if (o3 == 0 && onSegment(expandedQ1, expandedP1, expandedQ2)) return true
        if (o4 == 0 && onSegment(expandedQ1, expandedP2, expandedQ2)) return true

        return false
    }
    private fun orientation(p: PointF, q: PointF, r: PointF): Int {
        val value = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
        return when {
            value == 0f -> 0
            value > 0 -> 1
            else -> 2
        }
    }

    private fun expandPoint(point: PointF, eraserWidth: Float): PointF {
        // Adjust the point coordinates based on eraser width
        return PointF(point.x - eraserWidth, point.y - eraserWidth)
    }

    private fun onSegment(p: PointF, q: PointF, r: PointF): Boolean {
        return q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) && q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        val handleSize = 10f
        val handlePaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, handleSize, handlePaint)
    }
    private fun selectText(x: Float, y: Float): Text? {
        for (text in texts) {
            val textBounds = getTextBounds(text)
            if (textBounds.contains(x, y)) {
                return text
            }
        }
        return null
    }
    // Helper function to get the bounding rectangle of the text
    private fun getTextBounds(text: Text): RectF {
        val paint = Paint()
    //    paint.textSize = text.textSize
        val textWidth = paint.measureText(text.text)
        val textHeight = paint.descent() - paint.ascent()
        return RectF(text.x, text.y - textHeight, text.x + textWidth, text.y)
    }

    fun isTablet(context: Context): Boolean {
        val configuration = context.resources.configuration
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val isLarge = screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE
        val isXLarge = screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE
        return isLarge || isXLarge
    }



    private fun showAlertDialog(message: String) {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(message)
            .setPositiveButton("OK") { dialog, id ->
                // User clicked OK button
                dialog.dismiss() // Close the dialog
            }
        val alert = builder.create()
        alert.show()
    }
    fun lockShape(shape: Any) {
        when (shape) {
            is Line -> shape.locked = true
            is Rectangle -> shape.locked = true
            is Circle -> shape.locked = true
        }
    }

    fun unlockShape(shape: Any) {
        when (shape) {
            is Line -> shape.locked = false
            is Rectangle -> shape.locked = false
            is Circle -> shape.locked = false
        }
    }
    fun toggleLockSelectedShape() {
        selectedShape?.let {
            when (it) {
                is Line -> {
                    it.locked = !it.locked
                    it.color = if (it.locked) Color.GREEN else Color.BLACK
                }
                is Rectangle -> {
                    it.locked = !it.locked
                    it.color = if (it.locked) Color.GREEN else Color.BLACK
                }
                is Circle -> {
                    it.locked = !it.locked
                    it.color = if (it.locked) Color.GREEN else Color.BLACK
                }

                else -> {}
            }
        }
        invalidate()
    }
    private fun isShapeLocked(shape: Any): Boolean {
        return when (shape) {
            is Line -> shape.locked
            is Rectangle -> shape.locked
            is Circle -> shape.locked
            else -> false
        }
    }
    private fun findShapeAt(x: Float, y: Float): Shape? {
        // Implement logic to find and return the shape at the given coordinates
        for (line in lines) {
            if (isPointNearLine(x, y, line)) {
                return line
            }
        }
        for (rectangle in rectangles) {
            if (isPointNearRectangle(x, y, rectangle)) {
                return rectangle
            }
        }
        for (circle in circles) {
            if (isPointNearCircle(x, y, circle)) {
                return circle
            }
        }
        return null
    }
    private fun moveShape(shape: Any, x: Float, y: Float) {
        // Implement logic to move the shape
        when (shape) {
            is Line -> {
                val dx = x - (shape.startX + shape.endX) / 2
                val dy = y - (shape.startY + shape.endY) / 2
                shape.startX += dx
                shape.startY += dy
                shape.endX += dx
                shape.endY += dy
            }
            is Rectangle -> {
                val dx = x - (shape.startX + shape.endX) / 2
                val dy = y - (shape.startY + shape.endY) / 2
                shape.startX += dx
                shape.startY += dy
                shape.endX += dx
                shape.endY += dy
            }
            is Circle -> {
                shape.centerX = x
                shape.centerY = y
            }
        }
    }
    fun getShapes(): List<Shape> {
        val allShapes = mutableListOf<Shape>()
        allShapes.addAll(lines)
        allShapes.addAll(rectangles)
        allShapes.addAll(circles)
        // Add other shapes if any
        return allShapes
    }
    private fun selectShape(x: Float, y: Float) {
        selectedShape = null

        // Check if any shape is selected
        for (line in lines) {
            if (line.contains(x, y)) {
                selectedShape = line
                break
            }
        }

        for (rectangle in rectangles) {
            if (rectangle.contains(x, y)) {
                selectedShape = rectangle
                break
            }
        }

        for (circle in circles) {
            if (circle.contains(x, y)) {
                selectedShape = circle
                break
            }
        }

        // Add checks for other shapes if any

        invalidate()
    }
    private fun saveDrawingState(): String {
        return gson.toJson(shapes)
    }

    private fun loadDrawingState(json: String) {
        val shapeListType = object : TypeToken<List<Shape>>() {}.type
        val loadedShapes: List<Shape> = gson.fromJson(json, shapeListType)
        shapes.clear()
        shapes.addAll(loadedShapes)
        invalidate()
    }
    private fun snapToGrid(x: Float, y: Float): PointF {
        val snappedX = (x / gridSpacing).roundToInt() * gridSpacing
        val snappedY = (y / gridSpacing).roundToInt() * gridSpacing
        return PointF(snappedX, snappedY)
    }
    private fun calculateRadii(startX: Float, startY: Float, currentX: Float, currentY: Float): Pair<Float, Float> {
        val radiusX = abs(currentX - startX) / 2
        val radiusY = abs(currentY - startY) / 2
        return Pair(radiusX, radiusY)
    }
}