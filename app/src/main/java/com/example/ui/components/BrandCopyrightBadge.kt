package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.SpaceObsidian

@Composable
fun BrandCopyrightBadge(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        Surface(
            color = SpaceObsidian.copy(alpha = 0.82f),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                .testTag("brand_copyright_badge_compact"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_mo7mels),
                    contentDescription = stringResource(id = R.string.brand_logo_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = stringResource(id = R.string.copyright_notice_short),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Surface(
            color = SpaceObsidian.copy(alpha = 0.9f),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp))
                .testTag("brand_copyright_badge"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_mo7mels),
                    contentDescription = stringResource(id = R.string.brand_logo_cd),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.copyright_notice_full),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
        }
    }
}
