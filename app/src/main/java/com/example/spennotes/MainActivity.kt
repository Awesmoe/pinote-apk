package com.example.spennotes

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var drawingView: DrawingView
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val LOCAL_TIMEOUT_MS = 2000
    }

    private fun buildBaseUrl(ipKey: String, defaultIp: String): String {
        val ip = prefs.getString(ipKey, defaultIp)
        val port = prefs.getInt(SettingsActivity.KEY_SERVER_PORT, SettingsActivity.DEFAULT_SERVER_PORT)
        return "http://$ip:$port"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

        drawingView = DrawingView(this)

        val displayMetrics = resources.displayMetrics
        val halfHeight = displayMetrics.heightPixels / 2
        val drawingContainer = findViewById<FrameLayout>(R.id.drawing_container)

        drawingContainer.layoutParams.height = halfHeight
        drawingContainer.requestLayout()

        drawingContainer.addView(drawingView)

        findViewById<Button>(R.id.send_button).setOnClickListener { sendNoteToRpi() }
        findViewById<Button>(R.id.clear_button).setOnClickListener { drawingView.clear() }
        findViewById<Button>(R.id.clear_screen_button).setOnClickListener { clearPiScreen() }
        findViewById<Button>(R.id.newline_button).setOnClickListener { sendLineBreak() }
        findViewById<ImageButton>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        drawingView.updateSettings(
            spenOnly = prefs.getBoolean(SettingsActivity.KEY_SPEN_ONLY, false)
        )
    }

    private fun piRequest(
        path: String,
        body: String? = null,
        successMsg: String,
        failMsg: String,
        onSuccess: (() -> Unit)? = null
    ) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val responseCode = postToPi(path, body)
                withContext(Dispatchers.Main) {
                    if (responseCode == 200) {
                        Toast.makeText(this@MainActivity, successMsg, Toast.LENGTH_SHORT).show()
                        onSuccess?.invoke()
                    } else {
                        Toast.makeText(this@MainActivity, failMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun postToPi(path: String, body: String? = null): Int {
        fun attempt(base: String, timeoutMs: Int): Int {
            val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = 5000
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            try {
                if (body != null) {
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(body)
                        writer.flush()
                    }
                }
                return connection.responseCode
            } finally {
                connection.disconnect()
            }
        }

        val localBase = buildBaseUrl(SettingsActivity.KEY_LOCAL_IP, SettingsActivity.DEFAULT_LOCAL_IP)
        val tailscaleBase = buildBaseUrl(SettingsActivity.KEY_TAILSCALE_IP, SettingsActivity.DEFAULT_TAILSCALE_IP)

        return try {
            attempt(localBase, LOCAL_TIMEOUT_MS)
        } catch (_: Exception) {
            attempt(tailscaleBase, 5000)
        }
    }

    private fun sendLineBreak() {
        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("linebreak", true)
            put("strokes", JSONArray())
            put("width", 0)
            put("height", 0)
        }
        piRequest("/receive_note", json.toString(), "New line!", "Failed")
    }

    private fun clearPiScreen() {
        piRequest("/clear_notes", successMsg = "Pi screen cleared!", failMsg = "Failed to clear Pi screen")
    }

    private fun sendNoteToRpi() {
        val strokes = drawingView.getStrokes()
        if (strokes.isEmpty()) return

        // Single-pass bounding box
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        for (stroke in strokes) {
            for (point in stroke) {
                if (point.x < minX) minX = point.x
                if (point.y < minY) minY = point.y
                if (point.x > maxX) maxX = point.x
                if (point.y > maxY) maxY = point.y
            }
        }

        val strokesArray = JSONArray()
        strokes.forEach { stroke ->
            val pointsArray = JSONArray()
            stroke.forEach { point ->
                pointsArray.put(JSONObject().apply {
                    put("x", point.x - minX)
                    put("y", point.y - minY)
                })
            }
            strokesArray.put(JSONObject().apply { put("points", pointsArray) })
        }

        val json = JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("strokes", strokesArray)
            put("width", maxX - minX)
            put("height", maxY - minY)
        }

        piRequest("/receive_note", json.toString(), "Note sent!", "Failed to send note") {
            drawingView.clear()
        }
    }
}

class DrawingView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()
    private val strokes = mutableListOf<MutableList<PointF>>()
    private var currentStroke = mutableListOf<PointF>()
    private var spenOnly = false

    fun updateSettings(spenOnly: Boolean) {
        this.spenOnly = spenOnly
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (spenOnly && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return false
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                currentStroke = mutableListOf()
                currentStroke.add(PointF(x, y))
                path.moveTo(x, y)
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke.add(PointF(x, y))
                path.lineTo(x, y)
            }
            MotionEvent.ACTION_UP -> {
                strokes.add(currentStroke)
                performClick()
            }
        }

        invalidate()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }

    fun clear() {
        path.reset()
        strokes.clear()
        currentStroke.clear()
        invalidate()
    }

    fun getStrokes(): List<List<PointF>> = strokes.map { it.toList() }
}
