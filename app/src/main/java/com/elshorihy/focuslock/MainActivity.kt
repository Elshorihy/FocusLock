package com.elshorihy.focuslock

import android.content.Intent
import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

private data class LaunchableApp(val label: String, val packageName: String, val icon: Drawable)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FocusLockScreen() }
    }
}

@Composable
private fun FocusLockScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("focuslock", 0) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("blocking_enabled", false)) }
    var selected by remember { mutableStateOf(prefs.getStringSet("blocked_apps", emptySet())?.toSet() ?: emptySet()) }
    var minutes by remember { mutableIntStateOf(25) }
    var endAt by remember { mutableLongStateOf(prefs.getLong("session_end", 0L)) }
    var remaining by remember { mutableLongStateOf(((endAt-System.currentTimeMillis()).coerceAtLeast(0L))/1000L) }
    var serviceReady by remember { mutableStateOf(isServiceEnabled(context)) }
    LaunchedEffect(endAt) { while(endAt>0L && System.currentTimeMillis()<endAt) { remaining=((endAt-System.currentTimeMillis()).coerceAtLeast(0L))/1000L; delay(1000) }; if(endAt>0L) { prefs.edit().putLong("session_end",0L).putBoolean("blocking_enabled",false).apply(); endAt=0L } }
    val apps = remember {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(launchIntent, 0)
            .mapNotNull { info -> runCatching { LaunchableApp(info.loadLabel(context.packageManager).toString(), info.activityInfo.packageName, context.packageManager.getApplicationIcon(info.activityInfo.packageName)) }.getOrNull() }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }
    val bg = Color(0xFF0B1020)
    val panel = Color(0xFF151D32)
    MaterialTheme(colorScheme = darkColorScheme(background = bg, surface = panel, primary = Color(0xFF8B9CFF), onBackground = Color.White, onSurface = Color.White)) {
        Column(Modifier.fillMaxSize().background(bg).padding(20.dp)) {
            Text("FOCUSLOCK", color = Color(0xFF9BAAFF), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("خلي تركيزك\nفي إيدك.", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 39.sp)
            Spacer(Modifier.height(8.dp))
            Text("اختار التطبيقات اللي بتشتتك، وإحنا هنفكرك بهدفك.", color = Color.LightGray)
            Spacer(Modifier.height(20.dp))
            Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("إدارة الحجب", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("حدد التطبيقات بالأسفل، ثم فعّل الحجب. ستحتاج لتفعيل خدمة إمكانية الوصول يدويًا.", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val accessibilityOn = Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
                        if (!accessibilityOn) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        else {
                            enabled = !enabled
                            prefs.edit().putBoolean("blocking_enabled", enabled).putStringSet("blocked_apps", selected).apply()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "إيقاف الحجب" else "تفعيل الحجب") }
                    Text(if (enabled) "الحجب مفعّل" else "الحجب متوقف", color = Color(0xFFB9C5E8), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("التطبيقات", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${selected.size} محدد", color = Color(0xFF9BAAFF))
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        AndroidView(factory={ ImageView(it).apply { setImageDrawable(app.icon); scaleType=ImageView.ScaleType.FIT_CENTER } },modifier=Modifier.size(34.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(app.label, color = Color.White, modifier = Modifier.weight(1f))
                        Checkbox(checked = app.packageName in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + app.packageName else selected - app.packageName
                            prefs.edit().putStringSet("blocked_apps", selected).apply()
                        })
                    }
                }
            }
            Text("تم تطوير بواسطة حمزة شاهين", color=Color(0xFF78849A), fontSize=11.sp, modifier=Modifier.align(Alignment.CenterHorizontally))
            TextButton(onClick = {
                enabled = false
                prefs.edit().putBoolean("blocking_enabled", false).putLong("pause_until", 0L).apply()
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("استعادة التحكم / إعدادات الخدمة") }
        }
    }
}

private fun isServiceEnabled(context: android.content.Context): Boolean { val expected=ComponentName(context,FocusAccessibilityService::class.java).flattenToString(); val enabled=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false; return enabled.split(String.fromCharCode(58)).any { it.equals(expected,true) } }
