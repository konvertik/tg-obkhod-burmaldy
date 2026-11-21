package ru.mtproxymanager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Пасхалка: по нажатию кнопки со дна экрана вылетают подписи «тунг тунг сахуры и бренроты».
 * Только Compose-анимации: без сети, картинок и разрешений.
 */
@Composable
fun EasterEggOverlay(trigger: Int) {
    val particles = remember { mutableStateListOf<Particle>() }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val batch = buildParticles(trigger)
        particles.addAll(batch)
        delay(batch.maxOf { it.delayMs + it.durationMs }.toLong() + 150L)
        particles.removeAll(batch)
    }

    Box(Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            key(particle.id) {
                FlyingLabel(particle) { particles.remove(particle) }
            }
        }
    }
}

@Composable
private fun FlyingLabel(particle: Particle, onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(particle.id) {
        delay(particle.delayMs.toLong())
        progress.animateTo(1f, animationSpec = tween(particle.durationMs))
        onDone()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val p = progress.value
        val x = (particle.startX + (particle.endX - particle.startX) * p) * constraints.maxWidth
        val y = (particle.startY + (particle.endY - particle.startY) * p) * constraints.maxHeight
        Text(
            text = particle.text,
            color = particle.color,
            fontSize = particle.fontSize.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 10f)),
            modifier = Modifier.graphicsLayer {
                translationX = x - size.width / 2f
                translationY = y - size.height / 2f
                rotationZ = particle.rotation * p
                scaleX = 0.7f + 0.7f * p
                scaleY = 0.7f + 0.7f * p
                alpha = when {
                    p < 0.12f -> p / 0.12f
                    p > 0.72f -> (1f - p) / 0.28f
                    else -> 1f
                }
            },
        )
    }
}

private data class Particle(
    val id: Int,
    val text: String,
    val startX: Float,
    val endX: Float,
    val startY: Float,
    val endY: Float,
    val delayMs: Int,
    val durationMs: Int,
    val fontSize: Int,
    val rotation: Float,
    val color: Color,
)

private val LABELS = listOf(
    "ТУНГ ТУНГ САХУР",
    "БРЕНРОТЫ",
    "ТУНГ ТУНГ!",
    "БРР БРР ПАТАПИМ",
    "САХУР",
    "ШПИПИРИМИ",
    "ТРИПИ ТРОПИ",
    "БРЕЙНРОТ",
)

private val COLORS = listOf(
    Color(0xFFFFD93D),
    Color(0xFFFF6B9D),
    Color(0xFF4DD0E1),
    Color(0xFFB2FF59),
    Color(0xFFFF9100),
    Color(0xFFE1BEE7),
)

private fun buildParticles(seed: Int): List<Particle> {
    val random = Random(seed * 7919L + 13)
    return (0 until 14).map { index ->
        val fromLeft = random.nextBoolean()
        Particle(
            id = seed * 100 + index,
            text = LABELS.random(random),
            startX = if (fromLeft) -0.2f else 1.2f,
            endX = random.nextFloat() * 1.2f - 0.1f,
            startY = 0.75f + random.nextFloat() * 0.3f,
            endY = -0.1f + random.nextFloat() * 0.7f,
            delayMs = index * 70 + random.nextInt(130),
            durationMs = 1600 + random.nextInt(1200),
            fontSize = 20 + random.nextInt(22),
            rotation = (if (fromLeft) 1 else -1) * (20f + random.nextFloat() * 90f),
            color = COLORS.random(random),
        )
    }
}
