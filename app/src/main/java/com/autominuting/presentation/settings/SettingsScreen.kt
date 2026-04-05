package com.autominuting.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autominuting.data.auth.AuthMode
import com.autominuting.data.auth.AuthState
import com.autominuting.data.auth.DriveAuthState
import com.autominuting.data.preferences.UserPreferencesRepository
import com.autominuting.data.stt.WhisperModelManager
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesEngineType
import com.autominuting.domain.model.SttEngineType
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Folder

/**
 * 설정 섹션 헤더 + 콘텐츠를 그룹화하는 재사용 가능한 composable.
 *
 * @param title 섹션 헤더 텍스트
 * @param content 섹션 내부 콘텐츠
 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(12.dp))
    Column { content() }
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
}

/**
 * 설정 화면.
 * 회의록 형식 선택, 자동화 모드 토글, Gemini 인증 모드(API 키/OAuth) 선택,
 * Google 로그인/로그아웃 UI를 제공한다.
 *
 * @param viewModel 설정 상태를 관리하는 ViewModel
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToTemplates: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val defaultTemplateId by viewModel.defaultTemplateId.collectAsStateWithLifecycle()
    val defaultCustomPrompt by viewModel.defaultCustomPrompt.collectAsStateWithLifecycle()
    val sttEngineType by viewModel.sttEngineType.collectAsStateWithLifecycle()
    val minutesEngineType by viewModel.minutesEngineType.collectAsStateWithLifecycle()
    val hasGroqApiKey by viewModel.hasGroqApiKey.collectAsStateWithLifecycle()
    val hasDeepgramApiKey by viewModel.hasDeepgramApiKey.collectAsStateWithLifecycle()
    val hasClovaInvokeUrl by viewModel.hasClovaInvokeUrl.collectAsStateWithLifecycle()
    val hasClovaSecretKey by viewModel.hasClovaSecretKey.collectAsStateWithLifecycle()
    val hasClovaSummaryClientId by viewModel.hasClovaSummaryClientId.collectAsStateWithLifecycle()
    val hasClovaSummaryClientSecret by viewModel.hasClovaSummaryClientSecret.collectAsStateWithLifecycle()
    val whisperModelState by viewModel.whisperModelState.collectAsStateWithLifecycle()
    val automationMode by viewModel.automationMode.collectAsStateWithLifecycle()
    val authMode by viewModel.authMode.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val driveAuthState by viewModel.driveAuthState.collectAsStateWithLifecycle()
    val driveTranscriptFolderId by viewModel.driveTranscriptFolderId.collectAsStateWithLifecycle()
    val driveMinutesFolderId by viewModel.driveMinutesFolderId.collectAsStateWithLifecycle()
    val driveAutoUploadEnabled by viewModel.driveAutoUploadEnabled.collectAsStateWithLifecycle()
    val driveFolderPickerState by viewModel.driveFolderPickerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Drive 동의 결과를 처리하는 Activity Result 런처
    // Pitfall 방지: 컴포저블 최상위(unconditional)에서 등록해야 한다
    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        // Drive 동의 결과 처리
        val data = activityResult.data
        if (data != null) {
            scope.launch {
                try {
                    val authResult = Identity.getAuthorizationClient(context)
                        .getAuthorizationResultFromIntent(data)
                    val token = authResult.accessToken
                    if (token != null) {
                        viewModel.onDriveAuthorizationResult(token)
                    } else {
                        viewModel.onDriveAuthorizationFailed("access token이 null입니다")
                    }
                } catch (e: ApiException) {
                    viewModel.onDriveAuthorizationFailed(e.message)
                }
            }
        } else {
            viewModel.onDriveAuthorizationFailed("Drive 인증이 취소되었습니다")
        }
    }

    // NeedsConsent 상태 진입 시 PendingIntent 자동 실행
    LaunchedEffect(driveAuthState) {
        val state = driveAuthState
        if (state is DriveAuthState.NeedsConsent) {
            driveAuthLauncher.launch(
                IntentSenderRequest.Builder(state.pendingIntent.intentSender).build()
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // === 섹션 1: 파이프라인 ===
            SettingsSection(title = "파이프라인") {
                // 자동화 모드
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "완전 자동 모드",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "오디오 감지부터 회의록 생성까지 자동 진행",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = automationMode == AutomationMode.FULL_AUTO,
                        onCheckedChange = { isAuto ->
                            viewModel.setAutomationMode(
                                if (isAuto) AutomationMode.FULL_AUTO else AutomationMode.HYBRID
                            )
                        }
                    )
                }

                if (automationMode == AutomationMode.HYBRID) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "하이브리드 모드: 전사 완료 후 확인을 거쳐 회의록 생성",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // STT 엔진 드롭다운
                Text(
                    text = "STT 엔진",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "음성을 텍스트로 변환할 엔진을 선택합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                var sttDropdownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = sttDropdownExpanded,
                    onExpandedChange = { sttDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (sttEngineType) {
                            SttEngineType.GEMINI -> "Gemini STT (클라우드)"
                            SttEngineType.WHISPER -> "Whisper (온디바이스)"
                            SttEngineType.GROQ -> "Groq Whisper (클라우드)"
                            SttEngineType.DEEPGRAM -> "Deepgram Nova-3 (클라우드)"
                            SttEngineType.NAVER_CLOVA -> "Naver CLOVA Speech (클라우드)"
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sttDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = sttDropdownExpanded,
                        onDismissRequest = { sttDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gemini STT (클라우드)") },
                            onClick = {
                                viewModel.setSttEngineType(SttEngineType.GEMINI)
                                sttDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Whisper (온디바이스)") },
                            onClick = {
                                viewModel.setSttEngineType(SttEngineType.WHISPER)
                                sttDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Groq Whisper (클라우드)") },
                            onClick = {
                                viewModel.setSttEngineType(SttEngineType.GROQ)
                                sttDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Deepgram Nova-3 (클라우드)") },
                            onClick = {
                                viewModel.setSttEngineType(SttEngineType.DEEPGRAM)
                                sttDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Naver CLOVA Speech (클라우드)") },
                            onClick = {
                                viewModel.setSttEngineType(SttEngineType.NAVER_CLOVA)
                                sttDropdownExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 회의록 엔진 선택
                Text(
                    text = "회의록 생성 엔진",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "회의록 텍스트를 생성할 엔진을 선택합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                var minutesDropdownExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = minutesDropdownExpanded,
                    onExpandedChange = { minutesDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = when (minutesEngineType) {
                            MinutesEngineType.GEMINI -> "Gemini (클라우드)"
                            MinutesEngineType.DEEPGRAM -> "Deepgram Intelligence (클라우드)"
                            MinutesEngineType.NAVER_CLOVA -> "Naver CLOVA Summary (클라우드)"
                        },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = minutesDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = minutesDropdownExpanded,
                        onDismissRequest = { minutesDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Gemini (클라우드)") },
                            onClick = {
                                viewModel.setMinutesEngineType(MinutesEngineType.GEMINI)
                                minutesDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Deepgram Intelligence (클라우드)") },
                            onClick = {
                                viewModel.setMinutesEngineType(MinutesEngineType.DEEPGRAM)
                                minutesDropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Naver CLOVA Summary (클라우드)") },
                            onClick = {
                                viewModel.setMinutesEngineType(MinutesEngineType.NAVER_CLOVA)
                                minutesDropdownExpanded = false
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 프롬프트 템플릿 관리
                OutlinedButton(
                    onClick = onNavigateToTemplates,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("프롬프트 템플릿 관리")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 기본 프롬프트 템플릿
                Text(
                    text = "기본 프롬프트 템플릿",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "기본 템플릿을 설정하면 선택 없이 자동으로 해당 템플릿으로 생성됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 기본 템플릿 드롭다운
                var templateDropdownExpanded by remember { mutableStateOf(false) }
                val selectedTemplateName = when (defaultTemplateId) {
                    0L -> "매번 선택"
                    UserPreferencesRepository.CUSTOM_PROMPT_MODE_ID -> "직접 입력"
                    else -> templates.find { it.id == defaultTemplateId }?.name ?: "매번 선택"
                }

                ExposedDropdownMenuBox(
                    expanded = templateDropdownExpanded,
                    onExpandedChange = { templateDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedTemplateName,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateDropdownExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = templateDropdownExpanded,
                        onDismissRequest = { templateDropdownExpanded = false }
                    ) {
                        // "매번 선택" 옵션 (id = 0)
                        DropdownMenuItem(
                            text = { Text("매번 선택") },
                            onClick = {
                                viewModel.setDefaultTemplateId(0L)
                                templateDropdownExpanded = false
                            }
                        )
                        // "직접 입력" 옵션
                        DropdownMenuItem(
                            text = { Text("직접 입력") },
                            onClick = {
                                viewModel.setDefaultTemplateId(UserPreferencesRepository.CUSTOM_PROMPT_MODE_ID)
                                templateDropdownExpanded = false
                            }
                        )
                        // 템플릿 목록
                        templates.forEach { template ->
                            DropdownMenuItem(
                                text = { Text(template.name) },
                                onClick = {
                                    viewModel.setDefaultTemplateId(template.id)
                                    templateDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // 직접 입력 모드일 때 프롬프트 입력 TextField 표시
                if (defaultTemplateId == UserPreferencesRepository.CUSTOM_PROMPT_MODE_ID) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "기본 프롬프트",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "회의록 생성 시 사용할 프롬프트를 직접 입력하세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defaultCustomPrompt,
                        onValueChange = { viewModel.setDefaultCustomPrompt(it) },
                        placeholder = { Text("예: 회의 내용을 요약하고 액션 아이템을 정리해주세요") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (defaultCustomPrompt.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "프롬프트가 비어있으면 기본 구조화된 회의록 형식이 사용됩니다",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === 섹션 2: Google 계정 ===
            SettingsSection(title = "Google 계정") {
                Text(
                    text = "Google 계정 로그인 및 Gemini 인증을 설정합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Google 계정 로그인/로그아웃
                GoogleAccountSection(
                    authState = authState,
                    onSignIn = { viewModel.signInWithGoogle(context) },
                    onSignOut = { viewModel.signOut() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Gemini 인증 모드 RadioButton
                Text(
                    text = "Gemini API 호출 시 사용할 인증 방식을 선택합니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = authMode == AuthMode.API_KEY,
                        onClick = { viewModel.setAuthMode(AuthMode.API_KEY) }
                    )
                    Text(
                        text = "API 키",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(end = 24.dp)
                    )

                    RadioButton(
                        selected = authMode == AuthMode.OAUTH,
                        onClick = { viewModel.setAuthMode(AuthMode.OAUTH) }
                    )
                    Text(
                        text = "Google 계정 (OAuth)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 인증 모드별 UI
                if (authMode == AuthMode.OAUTH) {
                    // OAuth Client ID 입력
                    OAuthClientIdSection(viewModel = viewModel)
                    Spacer(modifier = Modifier.height(12.dp))
                    // Gemini API OAuth 권한 부여 버튼 -- 로그인 후 수동 재요청용
                    if (authState is AuthState.SignedIn) {
                        OutlinedButton(
                            onClick = { viewModel.authorizeGeminiAccess(context as Activity) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Gemini API 권한 부여 (재시도)")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Google 계정 로그인 후 Gemini API 접근 권한이 필요합니다. 회의록 생성이 안 될 경우 이 버튼을 눌러주세요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // API 키
                    ApiKeySection(viewModel = viewModel)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // === 섹션 3: Google Drive (로그인 시에만 표시) ===
            if (authState is AuthState.SignedIn) {
                SettingsSection(title = "Google Drive") {
                    Text(
                        text = "Google Drive 자동/수동 업로드를 설정합니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Drive 연결/해제
                    GoogleDriveSection(
                        driveAuthState = driveAuthState,
                        onConnectDrive = {
                            viewModel.authorizeDrive(context as Activity)
                        },
                        onRevokeDrive = { viewModel.revokeDriveAuth() }
                    )

                    // Drive 폴더 피커 + 자동 업로드 토글 (Drive 연결 시)
                    if (driveAuthState is DriveAuthState.Authorized) {
                        Spacer(modifier = Modifier.height(16.dp))
                        DriveFolderSection(
                            driveAuthState = driveAuthState,
                            transcriptFolderId = driveTranscriptFolderId,
                            minutesFolderId = driveMinutesFolderId,
                            autoUploadEnabled = driveAutoUploadEnabled,
                            folderPickerState = driveFolderPickerState,
                            onAutoUploadEnabledChange = viewModel::setDriveAutoUploadEnabled,
                            onBrowseTranscriptFolder = { viewModel.loadDriveFolders(null) },
                            onBrowseMinutesFolder = { viewModel.loadDriveFolders(null) },
                            onFolderSelected = { folder, isTranscript ->
                                if (isTranscript) viewModel.setDriveTranscriptFolderId(folder.id)
                                else viewModel.setDriveMinutesFolderId(folder.id)
                                viewModel.dismissDriveFolderPicker()
                            },
                            onNavigateIntoFolder = { viewModel.navigateIntoFolder(it) },
                            onNavigateUp = { viewModel.navigateUpFolder() },
                            onCreateFolder = { name, isTranscript ->
                                viewModel.createDriveFolder(
                                    folderName = name,
                                    onCreated = { /* 생성만, 자동 선택 안 함 -- 목록에서 직접 선택 */ },
                                    onError = { /* 피커 내 에러 표시는 상태로 처리됨 */ }
                                )
                            },
                            onDismissPicker = { viewModel.dismissDriveFolderPicker() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // === 섹션 4: API 키 (엔진 선택에 따라 조건부 표시) ===
            @Suppress("KotlinConstantConditions")
            val showApiKeySection = sttEngineType == SttEngineType.GROQ ||
                sttEngineType == SttEngineType.DEEPGRAM || minutesEngineType == MinutesEngineType.DEEPGRAM ||
                sttEngineType == SttEngineType.NAVER_CLOVA || minutesEngineType == MinutesEngineType.NAVER_CLOVA

            if (showApiKeySection) {
                SettingsSection(title = "API 키") {
                    // Groq API 키 (STT 엔진이 GROQ일 때만)
                    if (sttEngineType == SttEngineType.GROQ) {
                        ApiKeyInputField(
                            label = "Groq API 키",
                            hasKey = hasGroqApiKey,
                            onSave = { viewModel.saveGroqApiKey(it) },
                            onClear = { viewModel.clearGroqApiKey() }
                        )
                        if (!hasGroqApiKey) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Groq API 키가 필요합니다. console.groq.com에서 발급받으세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Deepgram API 키 (STT 엔진이 DEEPGRAM이거나 회의록 엔진이 DEEPGRAM일 때)
                    if (sttEngineType == SttEngineType.DEEPGRAM || minutesEngineType == MinutesEngineType.DEEPGRAM) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ApiKeyInputField(
                            label = "Deepgram API 키",
                            hasKey = hasDeepgramApiKey,
                            onSave = { viewModel.saveDeepgramApiKey(it) },
                            onClear = { viewModel.clearDeepgramApiKey() }
                        )
                        if (!hasDeepgramApiKey) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Deepgram API 키가 필요합니다. console.deepgram.com에서 발급받으세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Naver CLOVA Speech 설정 (STT 엔진이 NAVER_CLOVA일 때)
                    if (sttEngineType == SttEngineType.NAVER_CLOVA) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Naver CLOVA Speech (STT)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiKeyInputField(
                            label = "CLOVA Speech Invoke URL",
                            hasKey = hasClovaInvokeUrl,
                            onSave = { viewModel.saveClovaInvokeUrl(it) },
                            onClear = { viewModel.clearClovaInvokeUrl() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiKeyInputField(
                            label = "CLOVA Speech Secret Key",
                            hasKey = hasClovaSecretKey,
                            onSave = { viewModel.saveClovaSecretKey(it) },
                            onClear = { viewModel.clearClovaSecretKey() }
                        )
                        if (!hasClovaInvokeUrl || !hasClovaSecretKey) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CLOVA Speech Invoke URL과 Secret Key가 모두 필요합니다. NAVER Cloud Console에서 발급받으세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Naver CLOVA Summary 설정 (회의록 엔진이 NAVER_CLOVA일 때)
                    if (minutesEngineType == MinutesEngineType.NAVER_CLOVA) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Naver CLOVA Summary (회의록)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiKeyInputField(
                            label = "CLOVA Summary Client ID",
                            hasKey = hasClovaSummaryClientId,
                            onSave = { viewModel.saveClovaSummaryClientId(it) },
                            onClear = { viewModel.clearClovaSummaryClientId() }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ApiKeyInputField(
                            label = "CLOVA Summary Client Secret",
                            hasKey = hasClovaSummaryClientSecret,
                            onSave = { viewModel.saveClovaSummaryClientSecret(it) },
                            onClear = { viewModel.clearClovaSummaryClientSecret() }
                        )
                        if (!hasClovaSummaryClientId || !hasClovaSummaryClientSecret) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "CLOVA Summary Client ID와 Client Secret이 모두 필요합니다. NAVER Cloud Console에서 발급받으세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // === 섹션 5: 모델 관리 ===
            SettingsSection(title = "모델 관리") {
                // Whisper 모델 다운로드/삭제
                when (val state = whisperModelState) {
                    is WhisperModelManager.ModelState.NotDownloaded -> {
                        Text(
                            text = "Whisper 모델 미설치 (~500MB 다운로드 필요)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(onClick = { viewModel.downloadWhisperModel() }) {
                            Text("모델 다운로드")
                        }
                    }
                    is WhisperModelManager.ModelState.Downloading -> {
                        Text(
                            text = "모델 다운로드 중... ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    is WhisperModelManager.ModelState.Ready -> {
                        Text(
                            text = "Whisper 모델 설치 완료",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedButton(onClick = { viewModel.deleteWhisperModel() }) {
                            Text("모델 삭제")
                        }
                    }
                    is WhisperModelManager.ModelState.Error -> {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(onClick = { viewModel.downloadWhisperModel() }) {
                            Text("다시 시도")
                        }
                    }
                }
            }

        }
    }
}

/**
 * Google 계정 로그인/로그아웃 섹션.
 *
 * @param authState 현재 인증 상태
 * @param onSignIn 로그인 버튼 클릭 콜백
 * @param onSignOut 로그아웃 버튼 클릭 콜백
 */
@Composable
private fun GoogleAccountSection(
    authState: AuthState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    when (authState) {
        is AuthState.NotSignedIn -> {
            FilledTonalButton(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Google 계정",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Google 계정으로 로그인")
            }
        }

        is AuthState.Loading -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "로그인 중...",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        is AuthState.SignedIn -> {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google 계정",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = authState.displayName,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = authState.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onSignOut) {
                    Text("로그아웃")
                }
            }
        }

        is AuthState.Error -> {
            Column {
                Text(
                    text = authState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google 계정",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Google 계정으로 로그인")
                }
            }
        }
    }
}

/**
 * Google Drive 연결/해제 섹션.
 *
 * 미연결 상태: "Google Drive 연결" 버튼 표시
 * NeedsConsent: 동의 화면 런처 자동 실행 (LaunchedEffect에서 처리)
 * Loading: 진행 중 인디케이터
 * Authorized: 연결된 계정 이메일 + "연결 해제" 버튼
 * Error: 오류 메시지 + 재시도 버튼
 *
 * @param driveAuthState Drive 인증 상태
 * @param onConnectDrive Drive 연결 버튼 클릭 콜백
 * @param onRevokeDrive 연결 해제 버튼 클릭 콜백
 */
@Composable
private fun GoogleDriveSection(
    driveAuthState: DriveAuthState,
    onConnectDrive: () -> Unit,
    onRevokeDrive: () -> Unit
) {
    Column {
        Text(
            text = "Google Drive",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (driveAuthState) {
            is DriveAuthState.NotAuthorized -> {
                FilledTonalButton(
                    onClick = onConnectDrive,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Google Drive 연결")
                }
            }

            is DriveAuthState.NeedsConsent -> {
                // LaunchedEffect에서 PendingIntent 자동 실행 — 진행 중 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Drive 동의 화면 열기 중...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is DriveAuthState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Drive 연결 중...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            is DriveAuthState.Authorized -> {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Drive 연결된 계정",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Drive 연결됨",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (driveAuthState.email.isNotBlank()) {
                                Text(
                                    text = driveAuthState.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onRevokeDrive) {
                        Text("연결 해제")
                    }
                }
            }

            is DriveAuthState.Error -> {
                Column {
                    Text(
                        text = driveAuthState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = onConnectDrive,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Drive 다시 연결")
                    }
                }
            }
        }
    }
}

/**
 * Google Drive 업로드 폴더 선택 섹션.
 *
 * Drive 인증 완료(DriveAuthState.Authorized) 상태일 때만 표시된다.
 * 폴더 브라우징 버튼 클릭 시 Drive 폴더 목록을 Dialog로 표시하며,
 * 폴더 선택 또는 새 폴더 생성이 가능하다.
 */
@Composable
private fun DriveFolderSection(
    driveAuthState: DriveAuthState,
    transcriptFolderId: String,
    minutesFolderId: String,
    autoUploadEnabled: Boolean,
    folderPickerState: DriveFolderPickerState,
    onAutoUploadEnabledChange: (Boolean) -> Unit,
    onBrowseTranscriptFolder: () -> Unit,
    onBrowseMinutesFolder: () -> Unit,
    onFolderSelected: (com.autominuting.data.drive.DriveFolder, Boolean) -> Unit,
    onNavigateIntoFolder: (com.autominuting.data.drive.DriveFolder) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateFolder: (name: String, isTranscript: Boolean) -> Unit,
    onDismissPicker: () -> Unit
) {
    // Drive 인증 완료 상태일 때만 표시
    if (driveAuthState !is DriveAuthState.Authorized) return

    // 현재 피커가 어떤 폴더 유형을 위한 것인지 추적
    var pickerTargetIsTranscript by remember { mutableStateOf(true) }
    // 새 폴더 생성 다이얼로그 상태
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    // 피커 다이얼로그 (Loaded 상태일 때만 표시)
    if (folderPickerState is DriveFolderPickerState.Loaded) {
        val currentFolder = folderPickerState.currentFolder
        val navStack = folderPickerState.navStack

        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissPicker,
            title = {
                Column {
                    Text(if (pickerTargetIsTranscript) "전사 파일 폴더 선택" else "회의록 폴더 선택")
                    // 현재 경로 표시 (breadcrumb)
                    val breadcrumb = buildString {
                        append("내 드라이브")
                        navStack.forEach { append(" > ${it.name}") }
                        if (currentFolder != null) append(" > ${currentFolder.name}")
                    }
                    Text(
                        text = breadcrumb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            text = {
                Column {
                    // 뒤로가기 버튼 (root가 아닐 때)
                    if (currentFolder != null) {
                        TextButton(onClick = onNavigateUp) {
                            Text("← ${navStack.lastOrNull()?.name ?: "내 드라이브"}")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    // 현재 폴더를 선택하는 버튼
                    OutlinedButton(
                        onClick = {
                            onFolderSelected(
                                currentFolder ?: com.autominuting.data.drive.DriveFolder("root", "내 드라이브"),
                                pickerTargetIsTranscript
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✓ ${currentFolder?.name ?: "내 드라이브 (root)"} 선택")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (folderPickerState.folders.isEmpty()) {
                        Text(
                            text = "하위 폴더 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        folderPickerState.folders.forEach { folder ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 폴더 이름 클릭 → 선택
                                TextButton(
                                    onClick = { onFolderSelected(folder, pickerTargetIsTranscript) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                // 폴더 아이콘 클릭 → 하위 탐색
                                IconButton(onClick = { onNavigateIntoFolder(folder) }) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "하위 폴더 보기"
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreateFolderDialog = true }) {
                    Text("+ 새 폴더")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPicker) { Text("취소") }
            }
        )
    }

    // 로딩 다이얼로그
    if (folderPickerState is DriveFolderPickerState.Loading) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissPicker,
            title = { Text("폴더 목록 로딩 중...") },
            text = { CircularProgressIndicator() },
            confirmButton = {}
        )
    }

    // 에러 다이얼로그
    if (folderPickerState is DriveFolderPickerState.Error) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissPicker,
            title = { Text("오류") },
            text = { Text(folderPickerState.message) },
            confirmButton = {
                TextButton(onClick = onDismissPicker) { Text("확인") }
            }
        )
    }

    // 새 폴더 이름 입력 다이얼로그
    if (showCreateFolderDialog) {
        val currentFolderName = (folderPickerState as? DriveFolderPickerState.Loaded)
            ?.currentFolder?.name ?: "내 드라이브"
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                newFolderName = ""
            },
            title = { Text("새 폴더 만들기") },
            text = {
                Column {
                    Text(
                        text = "위치: $currentFolderName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("폴더 이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            onCreateFolder(newFolderName, pickerTargetIsTranscript)
                            showCreateFolderDialog = false
                            newFolderName = ""
                        }
                    },
                    enabled = newFolderName.isNotBlank()
                ) { Text("만들기") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateFolderDialog = false
                    newFolderName = ""
                }) { Text("취소") }
            }
        )
    }

    SettingsSection(title = "Google Drive 업로드 폴더") {
        // 자동 업로드 on/off 토글
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "파이프라인 완료 시 자동 업로드",
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = autoUploadEnabled,
                onCheckedChange = onAutoUploadEnabledChange
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 전사 파일 폴더
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "전사 파일 폴더",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (transcriptFolderId.isBlank()) "미설정 (내 드라이브 root)" else transcriptFolderId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = {
                pickerTargetIsTranscript = true
                onBrowseTranscriptFolder()
            }) {
                Text("선택")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 회의록 폴더
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "회의록 폴더",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (minutesFolderId.isBlank()) "미설정 (내 드라이브 root)" else minutesFolderId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = {
                pickerTargetIsTranscript = false
                onBrowseMinutesFolder()
            }) {
                Text("선택")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "폴더 미선택 시 내 드라이브 최상위(root)에 업로드됩니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Google OAuth Web Client ID 입력 섹션.
 * Google Cloud Console에서 발급받은 Web Client ID를 암호화 저장한다.
 *
 * @param viewModel 설정 ViewModel
 */
@Composable
private fun OAuthClientIdSection(viewModel: SettingsViewModel) {
    val hasOAuthClientId by viewModel.hasOAuthClientId.collectAsStateWithLifecycle()
    val oauthClientIdSaved by viewModel.oauthClientIdSaved.collectAsStateWithLifecycle()
    var clientIdInput by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Text(
        text = "Google OAuth Web Client ID",
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        text = "Google Cloud Console에서 발급받은 웹 클라이언트 ID를 입력하세요",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    TextButton(
        onClick = {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://console.cloud.google.com/apis/credentials")
            )
            context.startActivity(intent)
        },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        Text("Client ID 발급받기 →", style = MaterialTheme.typography.bodySmall)
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = clientIdInput,
        onValueChange = {
            clientIdInput = it
            viewModel.resetOAuthClientIdSaved()
        },
        label = { Text("Web Client ID") },
        placeholder = { Text("xxxx.apps.googleusercontent.com") },
        visualTransformation = if (isVisible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible)
                        Icons.Default.VisibilityOff
                    else
                        Icons.Default.Visibility,
                    contentDescription = if (isVisible) "숨기기" else "보기"
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                viewModel.saveOAuthClientId(clientIdInput.trim())
            },
            enabled = clientIdInput.isNotBlank()
        ) {
            Text("저장")
        }

        if (hasOAuthClientId) {
            OutlinedButton(onClick = {
                viewModel.clearOAuthClientId()
                clientIdInput = ""
            }) {
                Text("삭제")
            }
        }
    }

    if (oauthClientIdSaved) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Client ID가 저장되었습니다",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (hasOAuthClientId && !oauthClientIdSaved) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "사용자 Client ID 사용 중",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/**
 * API 키 입력 섹션.
 *
 * @param viewModel 설정 ViewModel
 */
@Composable
private fun ApiKeySection(viewModel: SettingsViewModel) {
    val apiKeyValidationState by viewModel.apiKeyValidationState.collectAsStateWithLifecycle()
    val hasApiKey by viewModel.hasApiKey.collectAsStateWithLifecycle()
    var apiKeyInput by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }

    Text(
        text = "Gemini API 키를 입력하면 내장 키 대신 사용합니다",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(8.dp))

    // API 키 입력 필드
    OutlinedTextField(
        value = apiKeyInput,
        onValueChange = {
            apiKeyInput = it
            viewModel.resetApiKeyValidationState()
        },
        label = { Text("Gemini API 키") },
        visualTransformation = if (isKeyVisible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                Icon(
                    imageVector = if (isKeyVisible)
                        Icons.Default.VisibilityOff
                    else
                        Icons.Default.Visibility,
                    contentDescription = if (isKeyVisible) "숨기기" else "보기"
                )
            }
        },
        singleLine = true,
        enabled = apiKeyValidationState !is ApiKeyValidationState.Validating,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    // 저장 버튼 + 상태 표시
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { viewModel.validateAndSaveApiKey(apiKeyInput.trim()) },
            enabled = apiKeyInput.isNotBlank() && apiKeyValidationState !is ApiKeyValidationState.Validating
        ) {
            if (apiKeyValidationState is ApiKeyValidationState.Validating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("검증 후 저장")
        }

        if (hasApiKey) {
            OutlinedButton(onClick = {
                viewModel.clearApiKey()
                apiKeyInput = ""
            }) {
                Text("키 삭제")
            }
        }
    }

    // 검증 결과 메시지
    when (val state = apiKeyValidationState) {
        is ApiKeyValidationState.Success -> {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "API 키가 저장되었습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        is ApiKeyValidationState.Error -> {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        else -> {}
    }

    if (hasApiKey) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "사용자 API 키 사용 중",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/**
 * API 키 입력 + 저장/삭제 재사용 composable.
 * Gemini API 키 입력 패턴을 간소화한 버전 (검증 없이 바로 저장).
 *
 * @param label 입력 필드 라벨
 * @param hasKey 저장된 키 존재 여부
 * @param onSave 저장 콜백
 * @param onClear 삭제 콜백
 */
@Composable
private fun ApiKeyInputField(
    label: String,
    hasKey: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text(label) },
        visualTransformation = if (isVisible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible)
                        Icons.Default.VisibilityOff
                    else
                        Icons.Default.Visibility,
                    contentDescription = if (isVisible) "숨기기" else "보기"
                )
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = {
                onSave(input.trim())
                input = ""
            },
            enabled = input.isNotBlank()
        ) {
            Text("저장")
        }

        if (hasKey) {
            OutlinedButton(onClick = {
                onClear()
                input = ""
            }) {
                Text("삭제")
            }
        }
    }

    if (hasKey) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "저장됨",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
