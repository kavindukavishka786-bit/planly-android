package com.planly.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun PlanlyMark(modifier: Modifier = Modifier) {
    val p = LocalPlanly.current
    Canvas(modifier.size(30.dp)) {
        val w = size.width; val h = size.height
        listOf(p.peach, p.butter, p.green, p.sky).forEachIndexed { index, color ->
            rotate((index - 1.5f) * 32f, Offset(w * .5f, h * .82f)) {
                drawOval(color, Offset(w * .36f, h * .05f), Size(w * .28f, h * .75f))
            }
        }
    }
}

/** Lightweight vector scenery: no bitmap downloads, blur passes, or continuous animation. */
@Composable fun CozyLandscape(modifier: Modifier = Modifier) {
    val p = LocalPlanly.current
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(p.sky, p.background, p.butter)))
        drawCircle(p.butter, w * .13f, Offset(w * .78f, h * .47f))
        val back = Path().apply {
            moveTo(0f, h * .76f); cubicTo(w * .25f, h * .36f, w * .36f, h * .92f, w * .67f, h * .67f)
            cubicTo(w * .83f, h * .48f, w * .93f, h * .62f, w, h * .53f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(back, p.lavender)
        val middle = Path().apply {
            moveTo(0f, h * .63f); cubicTo(w * .18f, h * .87f, w * .3f, h * .76f, w * .47f, h * .83f)
            cubicTo(w * .73f, h, w * .78f, h * .53f, w, h * .72f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(middle, p.mint)
        val front = Path().apply {
            moveTo(0f, h * .93f); cubicTo(w * .28f, h * .72f, w * .51f, h * 1.1f, w, h * .85f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(front, p.green.copy(alpha = .16f))
        // A small leaf sprig keeps the composition close to the supplied illustration.
        drawLine(p.green.copy(alpha = .65f), Offset(w * .93f, h), Offset(w * .93f, h * .77f), strokeWidth = 2.dp.toPx())
        rotate(-30f, Offset(w * .93f, h * .88f)) { drawOval(p.green.copy(alpha = .5f), Offset(w * .865f, h * .8f), Size(w * .06f, h * .08f)) }
        rotate(30f, Offset(w * .93f, h * .84f)) { drawOval(p.green.copy(alpha = .7f), Offset(w * .93f, h * .75f), Size(w * .055f, h * .09f)) }
    }
}

@Composable fun WelcomeGarden(greeting: String, name: String, onAdd: () -> Unit) {
    val p = LocalPlanly.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))) {
        CozyLandscape(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text(greeting, color = p.secondaryText, fontSize = 13.sp)
            Text(name.ifBlank { "A little space for you." }, color = p.text, fontWeight = FontWeight.Bold,
                fontSize = 25.sp, lineHeight = 31.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Text("Small steps today.\nMore room to breathe.", color = p.secondaryText, fontSize = 13.sp,
                lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(48.dp))
            Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text("Add a small step", fontSize = 12.sp); Spacer(Modifier.width(12.dp))
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp))
            }
        }
    }
}

@Composable fun QuickAccessCard(title: String, subtitle: String, icon: ImageVector, color: Color,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalPlanly.current
    Surface(onClick = onClick, modifier = modifier, color = color, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(42.dp).background(p.surface.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = p.green, modifier = Modifier.size(23.dp))
            }
            Text(title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
            Text(subtitle, color = p.secondaryText, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 5.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = p.green,
                modifier = Modifier.align(Alignment.End).padding(top = 10.dp).size(19.dp))
        }
    }
}
