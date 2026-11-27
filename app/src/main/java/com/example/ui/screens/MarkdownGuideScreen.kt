package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.util.markdown.toggleCheckboxAtLine

/** One topic in the guide. [example] is shown as typed and then rendered by the app's own preview. */
internal data class MarkdownLesson(
    @StringRes val titleRes: Int,
    @StringRes val helpRes: Int,
    val example: String
)

// The examples are Markdown syntax, so they are the same in every app language
internal val markdownLessons = listOf(
    MarkdownLesson(
        R.string.markdown_guide_headings_title,
        R.string.markdown_guide_headings_help,
        "# Data Structures\n## Linked lists\n### Key points"
    ),
    MarkdownLesson(
        R.string.markdown_guide_emphasis_title,
        R.string.markdown_guide_emphasis_help,
        "**Exam topic**\n*New term*\n~~Old deadline~~"
    ),
    MarkdownLesson(
        R.string.markdown_guide_lists_title,
        R.string.markdown_guide_lists_help,
        "- Arrays\n- Stacks\n  - Push and pop\n\n1. Read the slides\n2. Solve the lab"
    ),
    MarkdownLesson(
        R.string.markdown_guide_checklist_title,
        R.string.markdown_guide_checklist_help,
        "- [ ] Submit the lab\n- [x] Read chapter 4"
    ),
    MarkdownLesson(
        R.string.markdown_guide_quote_title,
        R.string.markdown_guide_quote_help,
        "> A stack is last in, first out."
    ),
    MarkdownLesson(
        R.string.markdown_guide_code_title,
        R.string.markdown_guide_code_help,
        "Use `push()` to add an item.\n\n```kotlin\nval stack = ArrayDeque<Int>()\nstack.addLast(5)\n```"
    ),
    MarkdownLesson(
        R.string.markdown_guide_links_title,
        R.string.markdown_guide_links_help,
        "[Course page](https://example.com)\n[[Project ideas]]"
    ),
    MarkdownLesson(
        R.string.markdown_guide_tables_title,
        R.string.markdown_guide_tables_help,
        "| Operation | Time |\n| --- | --- |\n| Push | O(1) |\n| Search | O(n) |"
    ),
    MarkdownLesson(
        R.string.markdown_guide_divider_title,
        R.string.markdown_guide_divider_help,
        "Lecture 1\n\n---\n\nLecture 2"
    )
)

/** A short "how to write Markdown notes" guide, opened from the top bar of the notes list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownGuideScreen(onClose: () -> Unit) {
    BackHandler { onClose() }

    Scaffold(
        modifier = Modifier.testTag("markdown_guide_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.markdown_guide_open),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("markdown_guide_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("markdown_guide_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.markdown_guide_intro),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            items(markdownLessons, key = { it.titleRes }) { lesson ->
                MarkdownLessonCard(lesson)
            }
            item {
                Text(
                    text = stringResource(R.string.markdown_guide_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MarkdownLessonCard(lesson: MarkdownLesson) {
    // Ticking a checklist item in the example updates both boxes, like a real note
    var source by rememberSaveable(lesson.titleRes) { mutableStateOf(lesson.example) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(lesson.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(lesson.helpRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GuideLabel(R.string.markdown_guide_you_type)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                // The symbols must stay in typing order, also when the app is in Urdu
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    SelectionContainer {
                        Text(
                            text = source,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            GuideLabel(R.string.markdown_guide_you_see)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                MarkdownSnippet(
                    markdown = source,
                    onToggleChecklist = { line -> source = toggleCheckboxAtLine(source, line) }
                )
            }
        }
    }
}

@Composable
private fun GuideLabel(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}
