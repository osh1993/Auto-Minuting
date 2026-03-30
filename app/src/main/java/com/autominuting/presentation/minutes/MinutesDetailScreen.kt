package com.autominuting.presentation.minutes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import com.autominuting.util.NotebookLmHelper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 회의록 상세 읽기 화면.
 * 회의록 정보와 Markdown 내용을 렌더링으로 표시한다.
 * 텍스트 선택이 가능하다.
 *
 * @param onBack 뒤로가기 콜백
 * @param viewModel 회의록 상세 상태를 관리하는 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinutesDetailScreen(
    onBack: () -> Unit = {},
    viewModel: MinutesDetailViewModel = hiltViewModel()
) {
    val minutes by viewModel.minutes.collectAsState()
    val minutesContent by viewModel.minutesContent.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = minutes?.minutesTitle ?: "회의록 상세",
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    // NotebookLM 전용 버튼 + 공유 버튼: 회의록 내용이 있을 때만 표시
                    if (minutesContent.isNotBlank()) {
                        // NotebookLM 전용 버튼
                        IconButton(onClick = {
                            NotebookLmHelper.shareToNotebookLm(
                                context = context,
                                title = minutes?.minutesTitle ?: "회의록",
                                content = minutesContent
                            )
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = "NotebookLM으로 보내기"
                            )
                        }
                        // 일반 공유 버튼
                        IconButton(onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_SUBJECT, minutes?.minutesTitle ?: "회의록")
                                putExtra(Intent.EXTRA_TEXT, minutesContent)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "공유"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (minutes == null) {
            // 회의록 정보 로딩 중
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val currentMinutes = minutes!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 회의록 정보 영역
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // 회의록 제목
                    Text(
                        text = currentMinutes.minutesTitle ?: "회의록",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 생성 일시
                    Text(
                        text = currentMinutes.createdAt.atZone(ZoneId.systemDefault())
                            .format(DETAIL_DATE_TIME_FORMATTER),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 회의록 내용 영역
                if (minutesContent.isBlank()) {
                    // 회의록 내용 없음 안내
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "회의록 내용을 불러올 수 없습니다",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // 회의록 Markdown 내용을 구조화된 타이포그래피로 렌더링 (텍스트 선택 가능)
                    SelectionContainer {
                        MarkdownText(
                            text = minutesContent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 상세 화면 생성 시각 포맷터 (yyyy-MM-dd HH:mm) */
private val DETAIL_DATE_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
