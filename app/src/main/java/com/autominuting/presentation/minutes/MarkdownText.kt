package com.autominuting.presentation.minutes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Markdown 텍스트를 구조화된 타이포그래피로 렌더링하는 Composable.
 *
 * 지원 범위:
 * - 헤딩: ## (titleLarge), ### (titleMedium)
 * - 볼드: **text**
 * - 불릿 목록: - 시작 라인
 * - 숫자 목록: 1. 시작 라인
 * - 테이블: | 구분자 기반 (헤더 행은 볼드)
 * - 구분선: --- 단독 라인
 *
 * 스크롤과 텍스트 선택은 호출부에서 처리한다.
 *
 * @param text Markdown 형식의 원본 문자열
 * @param modifier 레이아웃 수정자
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val lines = remember(text) { text.lines() }
    val parsedBlocks = remember(lines) { parseMarkdownLines(lines) }

    Column(modifier = modifier) {
        parsedBlocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading2 -> Heading2Block(block.content)
                is MarkdownBlock.Heading3 -> Heading3Block(block.content)
                is MarkdownBlock.BulletItem -> BulletItemBlock(block.content)
                is MarkdownBlock.NumberedItem -> NumberedItemBlock(block.number, block.content)
                is MarkdownBlock.Table -> TableBlock(block.headerCells, block.rows)
                is MarkdownBlock.Divider -> DividerBlock()
                is MarkdownBlock.Paragraph -> ParagraphBlock(block.content)
            }
        }
    }
}

// --- 파싱된 블록 타입 ---

/** Markdown 라인을 파싱한 블록 타입 */
private sealed class MarkdownBlock {
    /** ## 헤딩 */
    data class Heading2(val content: String) : MarkdownBlock()

    /** ### 헤딩 */
    data class Heading3(val content: String) : MarkdownBlock()

    /** 불릿 목록 아이템 (- 시작) */
    data class BulletItem(val content: String) : MarkdownBlock()

    /** 숫자 목록 아이템 (1. 시작) */
    data class NumberedItem(val number: String, val content: String) : MarkdownBlock()

    /** 테이블 (헤더 + 데이터 행) */
    data class Table(val headerCells: List<String>, val rows: List<List<String>>) : MarkdownBlock()

    /** 구분선 (---) */
    data object Divider : MarkdownBlock()

    /** 일반 텍스트 단락 */
    data class Paragraph(val content: String) : MarkdownBlock()
}

// --- 라인 파싱 ---

/** 테이블 구분선 패턴: |---|---| 또는 | --- | --- | 등 */
private val TABLE_SEPARATOR_REGEX = Regex("""^\|[\s\-:|]+\|$""")

/** 숫자 목록 패턴: 1. 2. 등 */
private val NUMBERED_LIST_REGEX = Regex("""^(\d+)\.\s+(.+)$""")

/**
 * Markdown 라인 목록을 블록 목록으로 파싱한다.
 * 테이블은 연속된 | 라인을 모아서 하나의 Table 블록으로 생성한다.
 */
private fun parseMarkdownLines(lines: List<String>): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        when {
            // 빈 라인은 건너뛴다
            trimmed.isEmpty() -> {
                i++
            }

            // ### 헤딩 (## 보다 먼저 검사)
            trimmed.startsWith("### ") -> {
                blocks.add(MarkdownBlock.Heading3(trimmed.removePrefix("### ").trim()))
                i++
            }

            // ## 헤딩
            trimmed.startsWith("## ") -> {
                blocks.add(MarkdownBlock.Heading2(trimmed.removePrefix("## ").trim()))
                i++
            }

            // 구분선: --- 단독 라인 (테이블 구분선이 아닌 경우)
            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks.add(MarkdownBlock.Divider)
                i++
            }

            // 테이블: | 시작 라인 → 연속된 | 라인을 모아서 처리
            trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                val tableLines = mutableListOf<String>()
                while (i < lines.size) {
                    val tl = lines[i].trim()
                    if (tl.startsWith("|") && tl.endsWith("|")) {
                        tableLines.add(tl)
                        i++
                    } else {
                        break
                    }
                }
                val table = parseTable(tableLines)
                if (table != null) {
                    blocks.add(table)
                }
            }

            // 불릿 목록: - 시작
            trimmed.startsWith("- ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.removePrefix("- ")))
                i++
            }

            // 숫자 목록: 1. 시작
            else -> {
                val numberedMatch = NUMBERED_LIST_REGEX.matchEntire(trimmed)
                if (numberedMatch != null) {
                    val number = numberedMatch.groupValues[1]
                    val content = numberedMatch.groupValues[2]
                    blocks.add(MarkdownBlock.NumberedItem(number, content))
                } else {
                    // 일반 텍스트
                    blocks.add(MarkdownBlock.Paragraph(trimmed))
                }
                i++
            }
        }
    }

    return blocks
}

/**
 * 테이블 라인 목록을 Table 블록으로 변환한다.
 * 첫 행은 헤더, 구분선 행(|---|)은 스킵, 나머지는 데이터 행이다.
 */
private fun parseTable(tableLines: List<String>): MarkdownBlock.Table? {
    if (tableLines.size < 2) return null

    val dataLines = tableLines.filter { !TABLE_SEPARATOR_REGEX.matches(it) }
    if (dataLines.isEmpty()) return null

    val headerCells = parseCells(dataLines.first())
    val rows = dataLines.drop(1).map { parseCells(it) }

    return MarkdownBlock.Table(headerCells, rows)
}

/** | 로 구분된 셀을 파싱한다. 앞뒤 빈 셀은 제거한다. */
private fun parseCells(line: String): List<String> {
    return line.split("|")
        .drop(1)                    // 첫 번째 빈 문자열 제거 (|앞)
        .dropLast(1)                // 마지막 빈 문자열 제거 (|뒤)
        .map { it.trim() }
}

// --- 볼드 인라인 파싱 ---

/** **bold** 패턴을 AnnotatedString으로 변환한다 */
private val BOLD_REGEX = Regex("""\*\*(.+?)\*\*""")

/**
 * 텍스트 내 **볼드** 패턴을 SpanStyle로 변환한 AnnotatedString을 생성한다.
 */
@Composable
private fun parseBoldText(text: String): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        BOLD_REGEX.findAll(text).forEach { match ->
            // 볼드 앞의 일반 텍스트
            append(text.substring(lastIndex, match.range.first))
            // 볼드 텍스트
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastIndex = match.range.last + 1
        }
        // 나머지 텍스트
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

// --- 블록 렌더링 Composable ---

/** ## 헤딩 렌더링 */
@Composable
private fun Heading2Block(content: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = parseBoldText(content),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
}

/** ### 헤딩 렌더링 */
@Composable
private fun Heading3Block(content: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = parseBoldText(content),
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(4.dp))
}

/** 불릿 목록 아이템 렌더링 */
@Composable
private fun BulletItemBlock(content: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            text = "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(16.dp)
        )
        Text(
            text = parseBoldText(content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 숫자 목록 아이템 렌더링 */
@Composable
private fun NumberedItemBlock(number: String, content: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = parseBoldText(content),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 테이블 렌더링: 헤더 행은 볼드, 데이터 행은 일반 */
@Composable
private fun TableBlock(headerCells: List<String>, rows: List<List<String>>) {
    val columnCount = headerCells.size

    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        // 헤더 행
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            headerCells.forEach { cell ->
                Text(
                    text = cell,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }

        HorizontalDivider()

        // 데이터 행
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // 셀 수가 헤더보다 적을 수 있으므로 헤더 기준으로 반복
                for (colIndex in 0 until columnCount) {
                    val cellText = row.getOrElse(colIndex) { "" }
                    Text(
                        text = parseBoldText(cellText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}

/** 구분선 렌더링 */
@Composable
private fun DividerBlock() {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.outlineVariant
    )
    Spacer(modifier = Modifier.height(8.dp))
}

/** 일반 텍스트 단락 렌더링 */
@Composable
private fun ParagraphBlock(content: String) {
    Text(
        text = parseBoldText(content),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}
