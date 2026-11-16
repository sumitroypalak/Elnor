@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The tag buttons of the home screen (see Tags.kt for what a tag is).

/**
 * One row of buttons, one for every tag that is in the notes: "#কাজ 3" (the tag and the number of
 * notes that have it). The row scrolls sideways when there are many. The tag with the most notes
 * is first. The tag with the key [selected] is shown as chosen; a tap on a button calls [onToggle]
 * with the key of that tag, and the home screen decides what that does (choose it, or take the
 * choice away when it is the chosen one).
 */
@Composable
internal fun TagChipsRow(
    tags: List<TagCount>,
    selected: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tags, key = { it.key }) { tag ->
            FilterChip(
                selected = tag.key == selected,
                onClick = { onToggle(tag.key) },
                label = { Text("#${tag.key}  ${tag.count}", maxLines = 1) }
            )
        }
    }
}
