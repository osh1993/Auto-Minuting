package com.autominuting.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.autominuting.domain.model.AutomationMode
import com.autominuting.domain.model.MinutesFormat

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
    val selectedFormat by viewModel.minutesFormat.collectAsStateWithLifecycle()
    val automationMode by viewModel.automationMode.collectAsStateWithLifecycle()
    val authMode by viewModel.authMode.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            // 프롬프트 템플릿 관리
            OutlinedButton(
                onClick = onNavigateToTemplates,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("프롬프트 템플릿 관리")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 회의록 형식 섹션
            Text(
                text = "회의록 형식",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "기본 회의록 생성 형식을 선택합니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 형식 드롭다운
            var expanded by remember { mutableStateOf(false) }
            val formatLabels = mapOf(
                MinutesFormat.STRUCTURED to "구조화된 회의록",
                MinutesFormat.SUMMARY to "요약",
                MinutesFormat.ACTION_ITEMS to "액션 아이템"
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = formatLabels[selectedFormat] ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor(type = androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    MinutesFormat.entries.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(formatLabels[format] ?: format.name) },
                            onClick = {
                                viewModel.setMinutesFormat(format)
                                expanded = false
                            }
                        )
                    }
                }
            }

            // 자동화 모드 섹션
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "자동화 모드",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

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

            // --- Gemini 인증 모드 섹션 ---
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Gemini 인증",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Gemini API 호출 시 사용할 인증 방식을 선택합니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 인증 모드 RadioButton
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
                    text = "Google 계정",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 인증 모드별 UI
            if (authMode == AuthMode.OAUTH) {
                // --- OAuth Client ID 입력 섹션 ---
                OAuthClientIdSection(viewModel = viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // --- Google 계정 (OAuth) 섹션 ---
                GoogleAccountSection(
                    authState = authState,
                    onSignIn = { viewModel.signInWithGoogle(context) },
                    onSignOut = { viewModel.signOut() }
                )
            } else {
                // --- API 키 섹션 ---
                ApiKeySection(viewModel = viewModel)
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
                    contentDescription = null,
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
                        contentDescription = null,
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
                        contentDescription = null,
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

    Text(
        text = "Google OAuth Web Client ID",
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        text = "Google Cloud Console에서 발급받은 웹 클라이언트 ID를 입력하세요",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

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
