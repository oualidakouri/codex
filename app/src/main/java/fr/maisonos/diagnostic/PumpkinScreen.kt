package fr.maisonos.diagnostic

import android.Manifest
import android.graphics.PixelFormat
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import fr.maisonos.core.*
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

@Composable internal fun PumpkinSettingsPanel(s: ProbeState, vm: ProbeViewModel) {
    val settings = s.config.home.pumpkin
    var automatic by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { if (automatic) { vm.interact(); vm.updateHome { it.copy(pumpkin = it.pumpkin.copy(enabled = true)) } } else vm.startPumpkin() }
        else vm.record("Citrouille",Evidence.NOT_TESTED,"Permission caméra refusée. Démonstration disponible sans caméra.")
    }
    Panel("Laboratoire · Citrouille 3D") {
        Text("Une grande citrouille en relief, sur fond transparent au-dessus de Maison OS. Ses yeux suivent le visage frontal le plus grand. Analyse Android locale, sans photo, enregistrement ni envoi réseau.",fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = settings.enabled, onCheckedChange = { enabled ->
                if (enabled) { automatic = true; permission.launch(Manifest.permission.CAMERA) }
                else { vm.dismissPumpkin(); vm.updateHome { it.copy(pumpkin = it.pumpkin.copy(enabled = false)) } }
            },modifier = Modifier.testTag("pumpkin-auto"))
            Text("Afficher automatiquement après inactivité")
        }
        Text("Délai : ${settings.idleSeconds} s · durée d’affichage : ${settings.durationSeconds} s")
        Choice(listOf("Après 5 s","Après 30 s","Après 1 min","Après 5 min"), listOf(5,30,60,300).indexOf(settings.idleSeconds)) { i -> vm.interact(); vm.updateHome { it.copy(pumpkin = it.pumpkin.copy(idleSeconds = listOf(5,30,60,300)[i])) } }
        Choice(listOf("Pendant 15 s","Pendant 1 min","Pendant 2 min","Pendant 5 min"), listOf(15,60,120,300).indexOf(settings.durationSeconds)) { i -> vm.updateHome { it.copy(pumpkin = it.pumpkin.copy(durationSeconds = listOf(15,60,120,300)[i])) } }
        Button(enabled = !s.simulation && s.resources?.protect != true, onClick = { automatic = false; permission.launch(Manifest.permission.CAMERA) },modifier = Modifier.testTag("pumpkin-test-camera")) { Text("Tester le suivi avec la caméra") }
        OutlinedButton(enabled = s.resources?.protect != true, onClick = { vm.startPumpkin(true) },modifier = Modifier.testTag("pumpkin-demo")) { Text("Démonstration sans caméra · simulée") }
        Text("Expérimental : visage de face et éclairage suffisant ; pas de reconnaissance d’identité ni de détection du corps. Le regard revient au centre sans visage. Le mode automatique remplace temporairement la veille sombre. Arrêt au changement d’application, pendant l’adhan ou en protection thermique/batterie. Suivi humain réel non vérifié sur cette tablette.",fontSize = 12.sp,color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable internal fun PumpkinOverlay(s: ProbeState, vm: ProbeViewModel) {
    BackHandler { vm.dismissPumpkin() }
    val context = LocalContext.current
    val renderer = remember { PumpkinRenderer() }
    val view = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8,8,8,8,16,0)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            setZOrderOnTop(true)
            // Bound fragment cost; Android scales this transparent surface to the screen.
            holder.setFixedSize(640,400)
            setRenderer(renderer); renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }
    }
    var renderError by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(s)
    LaunchedEffect(view) {
        var tick = 0
        while (true) {
            val value = current
            val gaze = if (value.pumpkinSimulated) FaceGaze(sin(tick/25f)*0.8f,sin(tick/37f)*0.45f,true) else value.gaze
            if (renderer.error != null && !renderError) {
                renderError = true
                vm.record("Citrouille",Evidence.FAILED,"Le pilote OpenGL a refusé le rendu 3D. Fermer et tester sur la tablette.")
            }
            renderer.targetX = gaze.x; renderer.targetY = gaze.y
            view.requestRender(); tick++; delay(50) // At most 20 fps, only while visible.
        }
    }
    DisposableEffect(view) { view.onResume(); onDispose { view.onPause() } }
    Box(Modifier.fillMaxSize().testTag("pumpkin-overlay").clickable { vm.dismissPumpkin() }) {
        AndroidView(factory = { view },modifier = Modifier.fillMaxSize().onSizeChanged { size ->
            if (size.width > 0 && size.height > 0) {
                val scale = minOf(1f,640f/maxOf(size.width,size.height))
                view.holder.setFixedSize((size.width*scale).toInt().coerceAtLeast(1),(size.height*scale).toInt().coerceAtLeast(1))
            }
        })
        Surface(Modifier.align(Alignment.TopStart).padding(12.dp),color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)) {
            Column(Modifier.padding(12.dp)) {
                Text(if (renderError) "Rendu 3D indisponible" else if (s.pumpkinSimulated) "SIMULATION · regard animé sans caméra" else when {
                    s.pumpkinCameraError -> "Caméra indisponible · regard au centre"
                    !s.pumpkinCameraReady -> "Ouverture de la caméra…"
                    s.gaze.detected -> "Visage détecté · suivi local"
                    else -> "Aucun visage frontal détecté"
                },fontSize = 13.sp)
                Text(if (s.pumpkinSimulated) "Aucune image enregistrée" else "${s.pumpkinFaceFrames} images analysées · aucune conservée",fontSize = 11.sp)
                TextButton(onClick = vm::dismissPumpkin,modifier = Modifier.testTag("pumpkin-close")) { Text("Fermer la citrouille") }
            }
        }
    }
}

/** Small static meshes, lit in OpenGL ES 2; only the pupil direction changes per frame. */
internal class PumpkinRenderer : GLSurfaceView.Renderer {
    @Volatile var targetX = 0f
    @Volatile var targetY = 0f
    @Volatile var error: String? = null
        private set
    @Volatile var frames = 0
        private set
    private var x = 0f; private var y = 0f
    private var program = 0
    private val projection = FloatArray(16)
    private val matrix = FloatArray(16)
    private val view = FloatArray(16).apply { android.opengl.Matrix.setIdentityM(this,0); android.opengl.Matrix.translateM(this,0,0f,-0.10f,-4f) }
    private data class Mesh(val data: java.nio.FloatBuffer,val count: Int,val kind: Int)
    private val meshes = listOf(sphere(0),stalk(),sphere(1,-0.36f),sphere(1,0.36f),mouth())
    private fun mesh(values: List<Float>, kind: Int) = Mesh(ByteBuffer.allocateDirect(values.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { values.forEach { put(it) };position(0) },values.size/6,kind)
    private fun sphere(kind: Int, centerX: Float = 0f): Mesh {
        val values = ArrayList<Float>()
        val latitudes = 20;val longitudes = if (kind == 0) 64 else 32
        fun point(i: Int,j: Int) {
            val phi = Math.PI*i/latitudes;val theta = 2*Math.PI*j/longitudes
            val sx = kotlin.math.sin(phi)*kotlin.math.cos(theta)
            val sy = kotlin.math.cos(phi); val sz = kotlin.math.sin(phi)*kotlin.math.sin(theta)
            if (kind == 1) {
                values.addAll(listOf(centerX+(sx*0.235).toFloat(),0.20f+(sy*0.235).toFloat(),0.76f+(sz*0.235).toFloat(),sx.toFloat(),sy.toFloat(),sz.toFloat()))
            } else {
                val radius = 0.98+0.055*kotlin.math.cos(10*theta)*kotlin.math.sin(phi)
                val qx = sx*radius;val qy = sy*radius;val qz = sz*radius
                val denominator = (qx*qx+qz*qz).coerceAtLeast(0.01)
                val dr = -0.55*kotlin.math.sin(10*theta)*kotlin.math.sin(phi)
                values.addAll(listOf(qx.toFloat(),(qy*0.86).toFloat(),(qz*0.85).toFloat(),
                    (sx+dr*qz/denominator).toFloat(),(sy/0.86).toFloat(),((sz-dr*qx/denominator)/0.85).toFloat()))
            }
        }
        for (i in 0 until latitudes) for (j in 0 until longitudes) {
            point(i,j);point(i+1,j);point(i,j+1)
            point(i,j+1);point(i+1,j);point(i+1,j+1)
        }
        return mesh(values,kind)
    }
    private fun stalk(): Mesh {
        val values = ArrayList<Float>()
        fun point(level: Int,j: Int) {
            val theta = 2*Math.PI*j/16;val radius = 0.085f*(1f-0.3f*level)
            values.addAll(listOf(0.02f+0.15f*level+(radius*kotlin.math.cos(theta)).toFloat(),0.82f+0.35f*level,(radius*kotlin.math.sin(theta)).toFloat(),kotlin.math.cos(theta).toFloat(),-0.2f,kotlin.math.sin(theta).toFloat()))
        }
        for (j in 0 until 16) { point(0,j);point(1,j);point(0,j+1);point(0,j+1);point(1,j);point(1,j+1) }
        return mesh(values,2)
    }
    private fun mouth(): Mesh {
        val values = ArrayList<Float>()
        fun point(i: Int,lower: Boolean) {
            val px = -0.55+1.10*i/32
            val py = -0.40+0.40*px*px - if (lower) 0.13 else if (kotlin.math.abs(px)<0.07) 0.08 else 0.0
            var low=0.0;var high=1.0
            repeat(16) {
                val z=(low+high)/2;val theta=kotlin.math.atan2(z/0.85,px)
                val radius=0.98+0.055*kotlin.math.cos(10*theta)*kotlin.math.sqrt((px*px+(z/0.85)*(z/0.85))/(px*px+(py/0.86)*(py/0.86)+(z/0.85)*(z/0.85)))
                if (kotlin.math.sqrt(px*px+(py/0.86)*(py/0.86)+(z/0.85)*(z/0.85))<radius) low=z else high=z
            }
            values.addAll(listOf(px.toFloat(),py.toFloat(),high.toFloat()+0.012f,0f,0f,1f))
        }
        for (i in 0 until 32) { point(i,false);point(i,true);point(i+1,false);point(i+1,false);point(i,true);point(i+1,true) }
        return mesh(values,3)
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            fun shader(type: Int, code: String): Int {
                val id = glCreateShader(type);glShaderSource(id,code);glCompileShader(id)
                val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);check(ok[0]!=0);return id
            }
            val vertex=shader(GL_VERTEX_SHADER,"""
                uniform mat4 matrix;attribute vec3 point;attribute vec3 normal;varying vec3 n;
                void main(){n=normal;gl_Position=matrix*vec4(point,1.0);}
            """.trimIndent())
            val fragment=shader(GL_FRAGMENT_SHADER,"""
                precision mediump float;uniform float kind;uniform vec2 gaze;varying vec3 n;
                void main(){
                    vec3 normal=normalize(n),light=normalize(vec3(-1.2,2.0,3.0));
                    vec3 color=vec3(1.0,0.29,0.02);
                    if(kind>0.5 && kind<1.5){
                        float iris=dot(normal,normalize(vec3(gaze.x*0.52,gaze.y*0.42,1.0)));
                        color=vec3(0.98,0.95,0.78);
                        if(iris>0.86) color=mix(vec3(0.05,0.19,0.08),vec3(0.28,0.58,0.17),smoothstep(0.86,0.91,iris));
                        if(iris>0.967) color=vec3(0.012,0.018,0.008);
                    }
                    if(kind>1.5)color=vec3(0.18,0.30,0.055);
                    if(kind>2.5)color=vec3(0.08,0.025,0.006);
                    float shine=pow(max(dot(reflect(-light,normal),vec3(0.0,0.0,1.0)),0.0),28.0);
                    gl_FragColor=vec4(color*(0.23+0.77*max(dot(normal,light),0.0))+shine*0.22,1.0);
                }
            """.trimIndent())
            program=glCreateProgram();glAttachShader(program,vertex);glAttachShader(program,fragment);glLinkProgram(program)
            val ok=IntArray(1);glGetProgramiv(program,GL_LINK_STATUS,ok,0);check(ok[0]!=0)
            glDeleteShader(vertex);glDeleteShader(fragment)
        } catch (_: Exception) { error="Rendu OpenGL indisponible";program=0 }
        glClearColor(0f,0f,0f,0f);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL)
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        glViewport(0,0,w,h)
        val aspect=w.toFloat()/h;val scale=1.22f*maxOf(1f,1.1f/aspect)
        android.opengl.Matrix.orthoM(projection,0,-scale*aspect,scale*aspect,-scale,scale,1f,10f)
        android.opengl.Matrix.multiplyMM(matrix,0,projection,0,view,0)
    }
    override fun onDrawFrame(gl: GL10?) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        if(program==0)return
        x+=(targetX-x)*0.18f;y+=(targetY-y)*0.18f
        glUseProgram(program);glUniformMatrix4fv(glGetUniformLocation(program,"matrix"),1,false,matrix,0)
        glUniform2f(glGetUniformLocation(program,"gaze"),x,y)
        val point=glGetAttribLocation(program,"point");val normal=glGetAttribLocation(program,"normal")
        glEnableVertexAttribArray(point);glEnableVertexAttribArray(normal)
        for(mesh in meshes){
            glUniform1f(glGetUniformLocation(program,"kind"),mesh.kind.toFloat())
            mesh.data.position(0);glVertexAttribPointer(point,3,GL_FLOAT,false,24,mesh.data)
            mesh.data.position(3);glVertexAttribPointer(normal,3,GL_FLOAT,false,24,mesh.data)
            glDrawArrays(GL_TRIANGLES,0,mesh.count)
        }
        glDisableVertexAttribArray(point);glDisableVertexAttribArray(normal)
        if(glGetError()==GL_NO_ERROR)frames++ else error="Erreur de rendu OpenGL"
    }
}
