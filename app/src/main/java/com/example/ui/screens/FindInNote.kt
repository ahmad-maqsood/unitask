package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** Pure search logic for "Find in note", kept apart from the screen so it can be tested. */
object NoteFind {

    /**
     * Start offsets of every match of [query] in [text], ignoring upper and lower case, in order and
     * without overlapping. A blank query finds nothing.
     */
    fun matches(text: String, query: String): List<Int> {
        if (query.isBlank() || text.isEmpty()) return emptyList()
        val found = ArrayList<Int>()
        var from = 0
        while (from <= text.length - query.length) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            found.add(at)
            from = at + query.length
        }
        return found
    }

    /** The next match number, going round to the first after the last. [total] is the number of matches. */
    fun step(current: Int, total: Int, forward: Boolean): Int {
        if (total <= 0) return 0
        return if (forward) (current + 1) % total else (current - 1 + total) % total
    }
}

/** The search text the preview should highlight. Empty when Find is closed. */
val LocalNoteFindQuery = compositionLocalOf { "" }

val FindMatchColor = Color(0x66FFD54F)
val FindCurrentMatchColor = Color(0xCCFF9800)

/** A copy of this text with a background behind every match of [query]. */
fun AnnotatedString.withFindHighlights(query: String): AnnotatedString {
    if (query.isBlank()) return this
    val hits = NoteFind.matches(text, query)
    if (hits.isEmpty()) return this
    return buildAnnotatedString {
        append(this@withFindHighlights)
        hits.forEach { addStyle(SpanStyle(background = FindMatchColor), it, it + query.length) }
    }
}

/** The bar under the top bar: a search box, "2 of 7", previous and next, and close. */
@Composable
fun NoteFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    currentMatch: Int,
    totalMatches: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("note_find_bar")
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find in note") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("note_find_input")
            )
            if (query.isNotBlank()) {
                Text(
                    text = if (totalMatches == 0) "No results" else "${currentMatch + 1} of $totalMatches",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("note_find_count")
                )
            }
            IconButton(
                onClick = onPrevious,
                enabled = totalMatches > 0,
                modifier = Modifier.testTag("note_find_previous")
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(
                onClick = onNext,
                enabled = totalMatches > 0,
                modifier = Modifier.testTag("note_find_next")
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onClose, modifier = Modifier.testTag("note_find_close")) {
                Icon(Icons.Default.Close, contentDescription = "Close find")
            }
        }
    }
}
