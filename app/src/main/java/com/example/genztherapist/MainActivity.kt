package com.example.genztherapist

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler // NEW: For the timer
import android.os.Looper  // NEW: For the timer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.ViewGroup
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import kotlin.random.Random // NEW: To pick random nudges

// --- DATA & API ---
data class ChatRequest(val user_message: String)
data class ChatResponse(val reply: String)

interface ApiService {
    @POST("chat/")
    fun chat(@Body request: ChatRequest): Call<ChatResponse>
}

class MainActivity : ComponentActivity() {

    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private var myVideoView: VideoView? = null

    //  NEW: INACTIVITY TIMER VARIABLES ---
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable { triggerNudge() }
    private val nudgePhrases = listOf(
        "You there bestie?",
        "Why so quiet? Tell me what's wrong.",
        "Hello? You zoning out?",
        "Is there any problem? You can tell me.",
        "Are you there?",
        "what's going on",
        "Relax and speak to me i'm here with you"
    )

    // TRACKING STATE
    private var currentVideoId = -1

    // API CONNECTION (Use your verified IP here)
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.202.228.3:8000/") // <--- MAKE SURE THIS IP IS CORRECT
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val api = retrofit.create(ApiService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //  Setup Text-to-Speech
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.US
                tts.setPitch(1.0f)
            }
        }

        // 2. Setup Speech Recognizer
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        setContent {
            ModernAvatarScreen()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ModernAvatarScreen() {
        var textInput by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // Full Screen Video
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        myVideoView = this
                        playVideo(R.raw.avatar_listening)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // LIVE Badge
            Row(
                modifier = Modifier
                    .padding(top = 48.dp, start = 24.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Red.copy(alpha = 0.85f), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).background(Color.White, CircleShape))
                Text(
                    text = " LIVE SESSION",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            //  Dark Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
            )

            //  Controls
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        stopInactivityTimer() // NEW: If typing, stop timer
                    },
                    placeholder = { Text("Chat with Dr. AI...", color = Color.LightGray) },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(28.dp)),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.2f),
                        disabledContainerColor = Color.White.copy(alpha = 0.2f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                FloatingActionButton(
                    onClick = {
                        stopInactivityTimer() // NEW: Stop timer on click
                        if (textInput.isNotEmpty()) {
                            sendToBackend(textInput)
                            textInput = ""
                        } else {
                            startListening()
                        }
                    },
                    containerColor = Color(0xFF6200EE),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (textInput.isNotEmpty()) Icons.Default.Send else Icons.Default.PlayArrow,
                        contentDescription = "Action",
                        tint = Color.White
                    )
                }
            }
        }
    }

    // SMART VIDEO LOGIC
    private fun playVideo(resourceId: Int) {
        if (currentVideoId == resourceId && myVideoView?.isPlaying == true) {
            return
        }
        currentVideoId = resourceId
        val path = "android.resource://$packageName/$resourceId"

        myVideoView?.setVideoURI(Uri.parse(path))
        myVideoView?.setOnPreparedListener { mp ->
            mp.setVolume(0f, 0f)
            mp.isLooping = true
            myVideoView?.start()
        }
    }

    private fun sendToBackend(text: String) {
        stopInactivityTimer() // Ensure timer is off while waiting for API
        val request = ChatRequest(user_message = text)

        api.chat(request).enqueue(object : Callback<ChatResponse> {
            override fun onResponse(call: Call<ChatResponse>, response: Response<ChatResponse>) {
                if (response.isSuccessful) {
                    val aiReply = response.body()?.reply ?: "I didn't quite catch that."
                    speakResponse(aiReply)
                }
            }
            override fun onFailure(call: Call<ChatResponse>, t: Throwable) {
                // If API fails, maybe nudge user to try again?
            }
        })
    }

    private fun speakResponse(text: String) {
        stopInactivityTimer() // Safety check

        runOnUiThread { playVideo(R.raw.avatar_speaking) } // CHANGE to avatar_talking if needed

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "done")
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "done")

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                currentVideoId = -1
                runOnUiThread {
                    playVideo(R.raw.avatar_listening)
                    startInactivityTimer() // NEW: Start counting ONLY after he finishes talking
                }
            }
            override fun onStart(utteranceId: String?) {}
            override fun onError(utteranceId: String?) {
                currentVideoId = -1
                runOnUiThread { playVideo(R.raw.avatar_listening) }
            }
        })
    }

    private fun startListening() {
        stopInactivityTimer() // User is acting, so stop timer

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spokenText = matches?.get(0) ?: ""
                    sendToBackend(spokenText)
                }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { stopInactivityTimer() }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    // If user cancels or error, maybe restart timer?
                    startInactivityTimer()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer.startListening(intent)
        }
    }

    private fun startInactivityTimer() {

        inactivityHandler.removeCallbacks(inactivityRunnable)
        // Start a new timer for 10 seconds
        inactivityHandler.postDelayed(inactivityRunnable, 10000)
    }

    private fun stopInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    private fun triggerNudge() {

        val randomNudge = nudgePhrases.random()

        speakResponse(randomNudge)
    }
}