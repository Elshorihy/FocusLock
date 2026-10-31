package com.elshorihy.focuslock

import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
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
    var appsRefreshKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FocusLockScreen() }
    }

    override fun onResume() {
        super.onResume()
        appsRefreshKey++
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
    val refreshKey = (context as? MainActivity)?.appsRefreshKey ?: 0
    val appDiagnostics = remember(refreshKey) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherCount = runCatching { pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL).size }.getOrElse { -1 }
        val installedCount = runCatching { pm.getInstalledApplications(PackageManager.MATCH_ALL).size }.getOrElse { -1 }
        val packageCount = runCatching { pm.getInstalledPackages(PackageManager.MATCH_ALL).size }.getOrElse { -1 }
        Triple(launcherCount, installedCount, packageCount)
    }
    val apps = remember(refreshKey) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = runCatching {
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL or PackageManager.MATCH_DISABLED_COMPONENTS)
        }.getOrDefault(emptyList())
        val fromLauncherQuery = resolved.mapNotNull { info ->
            val appInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (appInfo.packageName == context.packageName) return@mapNotNull null
            runCatching {
                LaunchableApp(
                    info.loadLabel(pm).toString(),
                    appInfo.packageName,
                    info.loadIcon(pm)
                )
            }.getOrNull()
        }
        // Fallback: enumerate visible installed packages, then retain only apps with a launcher entry.
        val fromInstalledPackages = if (fromLauncherQuery.isNotEmpty()) emptyList() else {
            runCatching {
                pm.getInstalledApplications(PackageManager.MATCH_ALL).mapNotNull { appInfo ->
                    if (appInfo.packageName == context.packageName) return@mapNotNull null
                    val launchIntentForApp = pm.getLaunchIntentForPackage(appInfo.packageName)
                        ?: return@mapNotNull null
                    runCatching {
                        LaunchableApp(
                            pm.getApplicationLabel(appInfo).toString(),
                            appInfo.packageName,
                            pm.getApplicationIcon(appInfo)
                        )
                    }.getOrNull()
                }
            }.getOrDefault(emptyList())
        }
        (fromLauncherQuery + fromInstalledPackages)
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
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
            Card(colors=CardDefaults.cardColors(containerColor=panel),shape=RoundedCornerShape(22.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("جلسة التركيز",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                    Text(if(endAt>0L) String.format("%02d:%02d",remaining/60,remaining%60) else String.format("%02d:00",minutes),color=Color.White,fontSize=38.sp,fontWeight=FontWeight.Bold)
                    Row { listOf(25,50,90).forEach { m -> FilterChip(selected=minutes==m && endAt==0L,onClick={minutes=m},enabled=endAt==0L,label={Text("$m دقيقة")}) } }
                    Button(onClick={ serviceReady=isServiceEnabled(context); if(!serviceReady) context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else if(endAt>0L) { endAt=0L; enabled=false; prefs.edit().putLong("session_end",0L).putBoolean("blocking_enabled",false).apply() } else if(selected.isNotEmpty()) { endAt=System.currentTimeMillis()+minutes*60000L; enabled=true; prefs.edit().putLong("session_end",endAt).putBoolean("blocking_enabled",true).putStringSet("blocked_apps",selected).apply() } },modifier=Modifier.fillMaxWidth()) { Text(if(endAt>0L) "إنهاء الجلسة" else if(!serviceReady) "تفعيل خدمة الحجب" else "ابدأ جلسة التركيز") }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (apps.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("تشخيص قائمة التطبيقات", color = Color(0xFFFFC779), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("تطبيقات التشغيل: ${appDiagnostics.first}", color = Color.White, fontSize = 13.sp)
                        Text("الحزم المثبتة: ${appDiagnostics.second}", color = Color.White, fontSize = 13.sp)
                        Text("كل الحزم: ${appDiagnostics.third}", color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("الأرقام دي هتحدد هل أندرويد مخفي التطبيقات عن التطبيق ولا المشكلة في فلتر القائمة.", color = Color.LightGray, fontSize = 12.sp)
                        OutlinedButton(onClick = {
                            val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            context.startActivity(details)
                        }, modifier = Modifier.fillMaxWidth()) { Text("فتح معلومات FocusLock") }
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
                        }, modifier = Modifier.fillMaxWidth()) { Text("فتح إعدادات التطبيقات") }
                        TextButton(onClick = { (context as? MainActivity)?.appsRefreshKey = (context as? MainActivity)?.appsRefreshKey?.plus(1) ?: 0 }, modifier = Modifier.fillMaxWidth()) { Text("إعادة فحص القائمة") }
                    }
                }
            }
            if (selected.isEmpty()) {
                Text("اختار تطبيقًا واحدًا على الأقل قبل بدء الجلسة.", color=Color(0xFFFFC779), fontSize=12.sp)
            }
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

private fun isServiceEnabled(context: android.content.Context): Boolean { val component=ComponentName(context,FocusAccessibilityService::class.java); val expected=component.flattenToString(); val short=component.flattenToShortString(); val enabled=Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false; return enabled.split(":").any { it.equals(expected,true) || it.equals(short,true) } }
