package com.letstrack.app.ui.goals

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.rememberAsyncImagePainter
import com.letstrack.app.domain.goal.GoalProgress
import com.letstrack.app.ui.components.AppCard
import com.letstrack.app.ui.home.formatCurrency
import com.letstrack.app.ui.theme.Spacing
import com.letstrack.app.ui.theme.heroCardBorderColor
import com.letstrack.app.ui.theme.heroCardBrush
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The Home card stack from the original sketch. Cards fan out at a slight angle as they move
 * away from center (a "dial" rotating past you), rather than the plain scale/fade peek tried
 * first -- picked over a literal card-back-peeking-out treatment since it's a natural extension
 * of the same per-page offset math a HorizontalPager already gives for free, no separate
 * card-back art needed. Wraps around infinitely in both directions (pageCount pushed to
 * Int.MAX_VALUE, actual goal picked via modulo) since a stack of goals has no natural "end".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GoalCardStack(
    goals: List<GoalProgress>,
    onGoalClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (goals.isEmpty()) return

    // "Infinite" via a large-but-bounded virtual page count, not Int.MAX_VALUE -- that was
    // causing the pager's internal fling/snap math to choke (showed up as scrolling getting
    // stuck, and once as a full ANR). goals.size * 1000 is still thousands of laps in each
    // direction, far more than anyone will ever actually scroll.
    val virtualPageCount = remember(goals.size) { goals.size * 1000 }
    val startPage = remember(goals.size) {
        val half = virtualPageCount / 2
        half - (half % goals.size)
    }
    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { virtualPageCount })

    HorizontalPager(
        state = pagerState,
        // Tall enough for the taller fixed-height image below (see GoalStackCard) plus the rest
        // of the card's content without leaving a dead patch at the bottom.
        modifier = modifier.fillMaxWidth().height(290.dp),
        // Wider inset than before so neighboring cards actually peek in from both edges
        // instead of the current card nearly filling the row.
        contentPadding = PaddingValues(horizontal = 56.dp),
        pageSpacing = Spacing.lg
    ) { page ->
        val goal = goals[((page % goals.size) + goals.size) % goals.size]
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val depth = min(1f, abs(pageOffset))

        GoalStackCard(
            progress = goal,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Angled fan: neighbors tilt and slide slightly as they recede, like a
                    // dial turning past the hero card instead of just fading in place.
                    rotationZ = lerp(0f, 10f, depth) * (if (pageOffset < 0) -1f else 1f)
                    translationY = lerp(0f, 28.dp.toPx(), depth)
                    scaleX = lerp(0.86f, 1f, 1f - depth)
                    scaleY = lerp(0.86f, 1f, 1f - depth)
                    alpha = lerp(0.55f, 1f, 1f - depth)
                }
                .clickable { onGoalClick(goal.goal.id) }
        )
    }
}

@Composable
private fun GoalStackCard(progress: GoalProgress, modifier: Modifier = Modifier) {
    val goal = progress.goal
    val primary = MaterialTheme.colorScheme.primary
    val remaining = (goal.targetAmount - progress.savedAmount).coerceAtLeast(0.0)
    val pctLabel = (progress.percent * 100).roundToInt()

    AppCard(
        modifier = modifier,
        backgroundBrush = heroCardBrush(primary),
        borderColor = heroCardBorderColor(),
        contentPadding = PaddingValues(Spacing.md)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(Spacing.sm))

            if (goal.photoUri != null) {
                // Fixed height (not weight(1f) filling most of the card, per the reordered
                // layout) but tall enough that ContentScale.Crop isn't slicing off most of the
                // photo -- 72dp was cropping way too aggressively.
                val model = if (goal.photoUri.startsWith("file://")) File(goal.photoUri.removePrefix("file://")) else goal.photoUri
                Image(
                    painter = rememberAsyncImagePainter(model),
                    contentDescription = goal.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(Spacing.sm))
            }

            Text(
                "Price: ${formatCurrency(goal.targetAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = if (progress.isFullyFunded) {
                    "Saved: ${formatCurrency(progress.savedAmount)} · 🎉 goal reached"
                } else {
                    "Saved: ${formatCurrency(progress.savedAmount)} · ${formatCurrency(remaining)} to go"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (goal.link != null) {
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Icon(Icons.Filled.Link, contentDescription = null, tint = primary, modifier = Modifier.height(14.dp))
                    Text(goal.link, style = MaterialTheme.typography.bodySmall, color = primary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                LinearProgressIndicator(
                    progress = { progress.percent.coerceAtMost(1f) },
                    // Taller than before (10dp vs 6dp) per feedback.
                    modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)),
                    color = primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    "$pctLabel%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            }
        }
    }
}
