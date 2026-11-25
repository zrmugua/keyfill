package com.mugua.keyfill

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
internal fun HyperOsAboutBackground(
    dark: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier,
    bgModifier: Modifier = Modifier,
    alpha: () -> Float = { 1f },
    content: @Composable BoxScope.() -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val painter = remember { Os3BgEffectPainter() }
    val preset = remember(dark) { if (dark) OS3_DARK else OS3_LIGHT }
    val colorStage = remember { Animatable(1f) }

    LaunchedEffect(playing, preset) {
        if (!playing) return@LaunchedEffect
        var targetStage = floor(colorStage.value) + 1f
        while (isActive) {
            delay((preset.colorInterpPeriod * 500f).toLong())
            colorStage.animateTo(
                targetValue = targetStage,
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 35f),
            )
            targetStage += 1f
        }
    }

    Box(modifier = modifier) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .then(bgModifier)
                .then(
                    BgEffectElement(
                        painter = painter,
                        preset = preset,
                        dark = dark,
                        surface = surface,
                        playing = playing,
                        colorStage = { colorStage.value },
                        alpha = alpha,
                    ),
                ),
        )
        content()
    }
}

private class BgEffectPreset(
    val points: FloatArray,
    val colors1: FloatArray,
    val colors2: FloatArray,
    val colors3: FloatArray,
    val colorInterpPeriod: Float,
    val lightOffset: Float,
    val saturateOffset: Float,
    val pointOffset: Float,
)

private val OS3_POINTS = floatArrayOf(
    0.8f, 0.2f, 1.0f,
    0.8f, 0.9f, 1.0f,
    0.2f, 0.9f, 1.0f,
    0.2f, 0.2f, 1.0f,
)

private val OS3_LIGHT = BgEffectPreset(
    points = OS3_POINTS,
    colors1 = floatArrayOf(1.0f, 0.9f, 0.94f, 1.0f, 1.0f, 0.84f, 0.89f, 1.0f, 0.97f, 0.73f, 0.82f, 1.0f, 0.64f, 0.65f, 0.98f, 1.0f),
    colors2 = floatArrayOf(0.58f, 0.74f, 1.0f, 1.0f, 1.0f, 0.9f, 0.93f, 1.0f, 0.74f, 0.76f, 1.0f, 1.0f, 0.97f, 0.77f, 0.84f, 1.0f),
    colors3 = floatArrayOf(0.98f, 0.86f, 0.9f, 1.0f, 0.6f, 0.73f, 0.98f, 1.0f, 0.92f, 0.93f, 1.0f, 1.0f, 0.56f, 0.69f, 1.0f, 1.0f),
    colorInterpPeriod = 5.0f,
    lightOffset = 0.1f,
    saturateOffset = 0.2f,
    pointOffset = 0.2f,
)

private val OS3_DARK = BgEffectPreset(
    points = OS3_POINTS,
    colors1 = floatArrayOf(0.2f, 0.06f, 0.88f, 0.4f, 0.3f, 0.14f, 0.55f, 0.5f, 0.0f, 0.64f, 0.96f, 0.5f, 0.11f, 0.16f, 0.83f, 0.4f),
    colors2 = floatArrayOf(0.07f, 0.15f, 0.79f, 0.5f, 0.62f, 0.21f, 0.67f, 0.5f, 0.06f, 0.25f, 0.84f, 0.5f, 0.0f, 0.2f, 0.78f, 0.5f),
    colors3 = floatArrayOf(0.58f, 0.3f, 0.74f, 0.4f, 0.27f, 0.18f, 0.6f, 0.5f, 0.66f, 0.26f, 0.62f, 0.5f, 0.12f, 0.16f, 0.7f, 0.6f),
    colorInterpPeriod = 8.0f,
    lightOffset = 0.0f,
    saturateOffset = 0.17f,
    pointOffset = 0.4f,
)

private class Os3BgEffectPainter {
    val shader = RuntimeShader(OS3_BG_SHADER).apply {
        setFloatUniform("uTranslateY", 0f)
        setFloatUniform("uNoiseScale", 1.5f)
        setFloatUniform("uPointRadiusMulti", 1f)
        setFloatUniform("uAlphaMulti", 1f)
    }
    val brush = ShaderBrush(shader)

    private val resolution = FloatArray(2)
    private val bound = FloatArray(4)
    private val colorsBuffer = FloatArray(16)
    private val pointsAnimBuffer = FloatArray(8)
    private var lastDark: Boolean? = null

    fun update(width: Float, height: Float, dark: Boolean, preset: BgEffectPreset, stage: Float, time: Float) {
        if (resolution[0] != width || resolution[1] != height) {
            resolution[0] = width
            resolution[1] = height
            shader.setFloatUniform("uResolution", resolution)
            val drawHeight = height * 0.78f
            val ratio = drawHeight / height
            if (width <= height) {
                bound[0] = 0f; bound[1] = 1f - ratio; bound[2] = 1f; bound[3] = ratio
            } else {
                val aspect = width / height
                bound[0] = 0f; bound[1] = (1f - ratio / 2f) - aspect / 2f; bound[2] = 1f; bound[3] = aspect
            }
            shader.setFloatUniform("uBound", bound)
        }
        if (lastDark != dark) {
            lastDark = dark
            shader.setFloatUniform("uPoints", preset.points)
            shader.setFloatUniform("uLightOffset", preset.lightOffset)
            shader.setFloatUniform("uSaturateOffset", preset.saturateOffset)
        }
        val base = stage.toInt()
        val frac = stage - base
        val start = cycleColors(preset, base)
        val end = cycleColors(preset, base + 1)
        for (i in 0 until 16) {
            colorsBuffer[i] = start[i] + (end[i] - start[i]) * frac
        }
        shader.setFloatUniform("uColors", colorsBuffer)
        shader.setFloatUniform("uAnimTime", time)

        val offset = preset.pointOffset
        for (i in 0 until 4) {
            val sx = preset.points[i * 3]
            val sy = preset.points[i * 3 + 1]
            val ax = sx + sin(time + sy) * offset
            val ay = sy + cos(time + ax) * offset
            pointsAnimBuffer[i * 2] = ax
            pointsAnimBuffer[i * 2 + 1] = ay
        }
        shader.setFloatUniform("uPointsAnim", pointsAnimBuffer)
    }

    private fun cycleColors(p: BgEffectPreset, idx: Int): FloatArray = when (idx.mod(4)) {
        1 -> p.colors1
        3 -> p.colors3
        else -> p.colors2
    }
}

private data class BgEffectElement(
    val painter: Os3BgEffectPainter,
    val preset: BgEffectPreset,
    val dark: Boolean,
    val surface: Color,
    val playing: Boolean,
    val colorStage: () -> Float,
    val alpha: () -> Float,
) : ModifierNodeElement<BgEffectNode>() {
    override fun create() = BgEffectNode(painter, preset, dark, surface, playing, colorStage, alpha)
    override fun update(node: BgEffectNode) = node.update(painter, preset, dark, surface, playing, colorStage, alpha)
}

private class BgEffectNode(
    private var painter: Os3BgEffectPainter,
    private var preset: BgEffectPreset,
    private var dark: Boolean,
    private var surface: Color,
    private var playing: Boolean,
    private var colorStage: () -> Float,
    private var alpha: () -> Float,
) : Modifier.Node(), DrawModifierNode {
    private var job: Job? = null
    private var animTime = 1.2f
    private var startOffset = 1.2f

    override fun onAttach() {
        if (playing) startLoop()
    }

    override fun onDetach() {
        job?.cancel()
        job = null
    }

    fun update(
        painter: Os3BgEffectPainter,
        preset: BgEffectPreset,
        dark: Boolean,
        surface: Color,
        playing: Boolean,
        colorStage: () -> Float,
        alpha: () -> Float,
    ) {
        this.painter = painter
        this.preset = preset
        this.dark = dark
        this.surface = surface
        this.colorStage = colorStage
        this.alpha = alpha
        if (this.playing != playing) {
            this.playing = playing
            if (playing) startLoop() else { job?.cancel(); job = null }
        }
        invalidateDraw()
    }

    private fun startLoop() {
        job?.cancel()
        startOffset = animTime
        job = coroutineScope.launch {
            val minDelta = 1_000_000_000L / 60L
            val origin = withFrameNanos { it }
            var last = origin
            while (isActive) {
                val now = withFrameNanos { it }
                if (now - last < minDelta) continue
                last = now
                animTime = startOffset + (now - origin) / 1_000_000_000f
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawRect(surface)
        val a = alpha()
        if (a <= 0f) {
            job?.cancel()
            job = null
        } else if (playing && job == null) {
            startLoop()
        }
        if (a > 0f && size.width > 0f && size.height > 0f) {
            painter.update(size.width, size.height, dark, preset, colorStage(), animTime)
            drawRect(painter.brush, alpha = a)
        }
        drawContent()
    }
}

private const val OS3_BG_SHADER = """
    uniform vec2 uResolution;
    uniform float uAnimTime;
    uniform vec4 uBound;
    uniform float uTranslateY;
    uniform vec3 uPoints[4];
    uniform vec2 uPointsAnim[4];
    uniform vec4 uColors[4];
    uniform float uAlphaMulti;
    uniform float uNoiseScale;
    uniform float uPointRadiusMulti;
    uniform float uSaturateOffset;
    uniform float uLightOffset;

    vec3 rgb2hsv(vec3 c) {
        vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
        vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
        vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
        float d = q.x - min(q.w, q.y);
        float e = 1.0e-10;
        return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
    }

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    float hash(vec2 p) {
        vec3 p3 = fract(vec3(p.xyx) * 0.13);
        p3 += dot(p3, p3.yzx + 3.333);
        return fract((p3.x + p3.y) * p3.z);
    }

    float perlin(vec2 x) {
        vec2 i = floor(x); vec2 f = fract(x);
        float a = hash(i); float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0)); float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float gradientNoise(in vec2 uv) {
        return fract(52.9829189 * fract(dot(uv, vec2(0.06711056, 0.00583715))));
    }

    vec4 main(vec2 fragCoord) {
        vec2 vUv = fragCoord / uResolution;
        vUv.y = 1.0 - vUv.y;
        vec2 uv = vUv;
        uv -= vec2(0.0, uTranslateY);
        uv.xy -= uBound.xy;
        uv.xy /= uBound.zw;

        vec4 color = vec4(0.0);
        float noiseValue = perlin(vUv * uNoiseScale + vec2(-uAnimTime, -uAnimTime));

        for (int i = 0; i < 4; i++) {
            vec4 pointColor = uColors[i];
            pointColor.rgb *= pointColor.a;
            vec2 point = uPointsAnim[i];
            float rad = uPoints[i].z * uPointRadiusMulti;
            float d = distance(uv, point);
            float pct = smoothstep(rad, 0.0, d);
            color.rgb = mix(color.rgb, pointColor.rgb, pct);
            color.a = mix(color.a, pointColor.a, pct);
        }

        float oppositeNoise = smoothstep(0.0, 1.0, noiseValue);
        color.rgb /= max(color.a, 1.0e-5);
        vec3 hsv = rgb2hsv(color.rgb);
        hsv.y = mix(hsv.y, 0.0, oppositeNoise * uSaturateOffset);
        color.rgb = hsv2rgb(hsv);
        color.rgb += oppositeNoise * uLightOffset;

        color.a = clamp(color.a, 0.0, 1.0) * uAlphaMulti * smoothstep(0.06, 0.36, vUv.y);
        color += (10.0 / 255.0) * gradientNoise(fragCoord.xy) - (5.0 / 255.0);
        return vec4(color.rgb * color.a, color.a);
    }
"""
