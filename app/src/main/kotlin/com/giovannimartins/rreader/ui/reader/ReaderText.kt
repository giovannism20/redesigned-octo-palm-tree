package com.giovannimartins.rreader.ui.reader

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.giovannimartins.rreader.R
import com.giovannimartins.rreader.data.settings.ReadingFont
import com.giovannimartins.rreader.ui.theme.readingTextStyle

/**
 * The chapter's paragraphs in the listener's [readingFont], one lazy item each so long chapters scroll smoothly; only
 * the paragraph being read carries a highlight. While [following], the highlighted line is kept in the upper part of
 * the screen; a drag by the listener reports [onUserScroll]. Tapping a paragraph reads from the sentence under the
 * finger, and TalkBack users get "Read from here" on each paragraph.
 */
@Composable
internal fun ReaderText(
    chapterIndex: Int,
    paragraphs: List<String>,
    highlight: ReaderHighlight?,
    following: Boolean,
    followRequests: Int,
    textScale: Float,
    readingFont: ReadingFont,
    onUserScroll: () -> Unit,
    onReadFrom: (paragraph: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A new chapter starts with a new list, at its top.
    val listState = key(chapterIndex) { rememberLazyListState() }
    val layouts = remember(chapterIndex) { HashMap<Int, TextLayoutResult>() }
    val currentOnUserScroll by rememberUpdatedState(onUserScroll)
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) currentOnUserScroll() }
    }
    LaunchedEffect(listState, highlight, following, followRequests) {
        if (following && highlight != null) listState.keepInView(highlight, layouts)
    }
    val style = readingTextStyle(readingFont, textScale)
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(PARAGRAPH_SPACING),
    ) {
        itemsIndexed(paragraphs) { index, text ->
            ReaderParagraph(
                text = text,
                highlight = highlight?.takeIf { it.paragraph == index },
                style = style,
                onTextLayout = { layouts[index] = it },
                onReadFrom = { offset -> onReadFrom(index, offset) },
            )
        }
    }
}

@Composable
private fun ReaderParagraph(
    text: String,
    highlight: ReaderHighlight?,
    style: TextStyle,
    onTextLayout: (TextLayoutResult) -> Unit,
    onReadFrom: (offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnReadFrom by rememberUpdatedState(onReadFrom)
    val readFromHere = stringResource(R.string.reader_read_from_here)
    val readingNow = stringResource(R.string.reader_reading_now)
    val background = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary
    val shown =
        remember(text, highlight, background, foreground) { highlighted(text, highlight, background, foreground) }
    Text(
        text = shown,
        style = style,
        onTextLayout = {
            layout = it
            onTextLayout(it)
        },
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MIN_TOUCH_HEIGHT)
                .pointerInput(text) {
                    detectTapGestures { position ->
                        layout?.let { currentOnReadFrom(it.getOffsetForPosition(position)) }
                    }
                }.semantics {
                    onClick(label = readFromHere) {
                        currentOnReadFrom(0)
                        true
                    }
                    if (highlight != null) stateDescription = readingNow
                },
    )
}

/** [text] with the characters being read on the primary colour, or plain text when none are. */
private fun highlighted(
    text: String,
    highlight: ReaderHighlight?,
    background: Color,
    foreground: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (highlight != null) {
            val start = highlight.start.coerceIn(0, text.length)
            addStyle(
                SpanStyle(background = background, color = foreground),
                start,
                highlight.end.coerceIn(start, text.length),
            )
        }
    }

/**
 * Scrolls so the highlighted line sits in the upper part of the screen, unless it is already comfortably in
 * view. A paragraph far away is jumped to first, then the line is placed once that paragraph is laid out.
 */
private suspend fun LazyListState.keepInView(
    highlight: ReaderHighlight,
    layouts: Map<Int, TextLayoutResult>,
) {
    if (layoutInfo.visibleItemsInfo.none { it.index == highlight.paragraph }) {
        scrollToItem(highlight.paragraph)
        withFrameNanos { }
    }
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == highlight.paragraph }
    val layout = layouts[highlight.paragraph]
    if (item != null && layout != null) {
        val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        val lineTop =
            item.offset +
                layout.getLineTop(layout.getLineForOffset(highlight.start.coerceAtMost(layout.layoutInput.text.length)))
        val lineEnd = (highlight.end - 1).coerceIn(highlight.start, layout.layoutInput.text.length)
        val lineBottom = item.offset + layout.getLineBottom(layout.getLineForOffset(lineEnd))
        val comfortable = lineTop >= viewport * COMFORT_TOP && lineBottom <= viewport * COMFORT_BOTTOM
        if (!comfortable) animateScrollBy(lineTop - viewport * TARGET_TOP)
    }
}

private val PARAGRAPH_SPACING = 16.dp
private val MIN_TOUCH_HEIGHT = 48.dp

/** The highlighted line stays between 10 % and 80 % of the screen's height, and is brought to 25 %. */
private const val COMFORT_TOP = 0.1f
private const val COMFORT_BOTTOM = 0.8f
private const val TARGET_TOP = 0.25f
