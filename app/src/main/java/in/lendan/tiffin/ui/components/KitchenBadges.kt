package `in`.lendan.tiffin.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import `in`.lendan.tiffin.R
import java.util.Locale

private val VegGreen = Color(0xFF2E7D32)
private val NonVegRed = Color(0xFFB3261E)

/** Indian food-label style mark plus a text label, so meaning never depends on colour alone. */
@Composable
fun DietIndicator(isVeg: Boolean, modifier: Modifier = Modifier) {
    val color = if (isVeg) VegGreen else NonVegRed
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(BorderStroke(1.5.dp, color)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(7.dp)) {
                if (isVeg) {
                    drawCircle(color = color)
                } else {
                    val path = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(if (isVeg) R.string.veg else R.string.non_veg),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

@Composable
fun Rating(rating: Double, modifier: Modifier = Modifier) {
    val formatted = String.format(Locale.getDefault(), "%.1f", rating)
    val description = stringResource(R.string.rating_content_description, formatted)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(text = formatted, style = MaterialTheme.typography.labelLarge)
    }
}
