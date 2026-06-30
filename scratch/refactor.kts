import java.io.File

val file = File("d:/Xshield/Xshield/app/src/main/java/com/example/xshield/LiveCameraScreen.kt")
var content = file.readText()

val regex = Regex("(            val activeCamera = .*?)(            val unifiedCaptures = )", RegexOption.DOT_MATCHES_ALL)
val match = regex.find(content)

if (match != null) {
    val controlsBlock = match.groupValues[1]
    
    val overlayDef = """
    val cameraControlsOverlay: @androidx.compose.runtime.Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().run {
                if (isFullScreen) this.padding(horizontal = 16.dp, vertical = 32.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(12.dp) else this
            }
        ) {
${controlsBlock}        }
    }
"""

    // Insert overlay definition before Scaffold
    content = content.replace("    Scaffold(", overlayDef + "\n    Scaffold(")
    
    // Replace the original controls block
    content = content.replace(controlsBlock, "                cameraControlsOverlay()\n            }\n            if (!isFullScreen) {\n")
    
    // Inject overlay inside Box
    val boxRegex = Regex("(                // Add the Fullscreen toggle at the top end.*?                \\}\n            \\})", RegexOption.DOT_MATCHES_ALL)
    val boxMatch = boxRegex.find(content)
    if (boxMatch != null) {
        val oldBoxEnd = boxMatch.groupValues[1]
        val newBoxEnd = oldBoxEnd.replace("            }", """
                if (isFullScreen) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        cameraControlsOverlay()
                    }
                }
            }""")
        content = content.replace(oldBoxEnd, newBoxEnd)
        
        file.writeText(content)
        println("Successfully refactored controls.")
    } else {
        println("Could not find Box end.")
    }
} else {
    println("Could not find controls block.")
}
