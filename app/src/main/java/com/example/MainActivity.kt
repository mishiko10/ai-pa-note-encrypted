package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.VaultItem
import com.example.totp.TotpUtils
import com.example.ui.VaultViewModel
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

// Dark Theme Palette tailored for Security & Privacy
private val CryptoSlateDark = Color(0xFF0C0F12)
private val CryptoSurface = Color(0xFF141A20)
private val CryptoBorderAccent = Color(0xFF1F2A35)
private val CryptoMatrixGreen = Color(0xFF00FF9D)
private val CryptoGoldAccent = Color(0xFFFFB300)
private val CryptoTrashRed = Color(0xFFFF4D4D)
private val CryptoMutedGrey = Color(0xFF90A4AE)

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContent(activity = this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    activity: FragmentActivity,
    viewModel: VaultViewModel = viewModel()
) {
    val context = LocalContext.current
    val isRegistered by viewModel.isRegistered.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = CryptoSlateDark
    ) {
        when {
            !isRegistered -> {
                // First launch: registration of master password
                MasterPasswordRegistration(
                    onRegister = { password ->
                        viewModel.registerMasterPassword(password)
                        Toast.makeText(context, "სამაგისტრო პაროლი წარმატებით დაყენდა!", Toast.LENGTH_LONG).show()
                    }
                )
            }
            !isUnlocked -> {
                // Secondary launches: Unlock screen with Master Password or Biometrics
                UnlockScreen(
                    activity = activity,
                    biometricEnabled = biometricEnabled,
                    onPasswordSubmit = { password ->
                        val success = viewModel.unlockWithPassword(password)
                        if (success) {
                            Toast.makeText(context, "საცავი განბლოკილია!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "არასწორი პაროლი!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBiometricRequest = {
                        val executor = ContextCompat.getMainExecutor(activity)
                        val biometricPrompt = BiometricPrompt(
                            activity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    super.onAuthenticationSucceeded(result)
                                    viewModel.unlockWithBiometrics()
                                    Toast.makeText(activity, "ბიომეტრიული ავტორიზაცია წარმატებულია!", Toast.LENGTH_SHORT).show()
                                }

                                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                    super.onAuthenticationError(errorCode, errString)
                                    Toast.makeText(activity, "ბიომეტრიული შეცდომა: $errString", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )

                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("CryptoVault განბლოკვა")
                            .setSubtitle("დაადასტურეთ თქვენი ბიომეტრია")
                            .setNegativeButtonText("პაროლის შეყვანა")
                            .build()

                        try {
                            biometricPrompt.authenticate(promptInfo)
                        } catch (e: Exception) {
                            // If biometric hardware is missing (e.g. emulator), perform easy fallback sim
                            val simulated = viewModel.unlockWithBiometrics()
                            if (simulated) {
                                Toast.makeText(activity, "ავტორიზაცია (სიმულაცია): წარმატებულია", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, "თქვენ ჯერ არ გაგიაქტიურებიათ ბიომეტრიული შესვლა პარამეტრებიდან!", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
            else -> {
                // Vault successfully unlocked! Full application workspace
                VaultWorkspace(viewModel = viewModel)
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// Base App Screens
// --------------------------------------------------------------------------------------------------

@Composable
fun MasterPasswordRegistration(onRegister: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = "Security Shield",
                tint = CryptoMatrixGreen,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CryptoVault",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = CryptoMatrixGreen,
                fontFamily = FontFamily.Serif
            )
            Text(
                text = "ოფლაინ პაროლების და 2FA მენეჯერი",
                fontSize = 14.sp,
                color = CryptoMutedGrey,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                border = BorderStroke(1.dp, CryptoBorderAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "შექმენით სამაგისტრო პაროლი",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "პაროლი გამოიყენება საცავის დასაშიფრად სამხედრო AES-256 სტანდარტით. დამახსოვრება კრიტიკულია, რადგან დეცენტრალიზებული ოფლაინ პრინციპის გამო მისი აღდგენა შეუძლებელია!",
                        fontSize = 12.sp,
                        color = CryptoMutedGrey,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; errorMessage = "" },
                        label = { Text("სამაგისტრო პაროლი", color = CryptoMutedGrey) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Toggle visible password",
                                    tint = CryptoMutedGrey
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("master_password_setup_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it; errorMessage = "" },
                        label = { Text("გაიმეორეთ პაროლი", color = CryptoMutedGrey) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("master_password_confirm_input")
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = CryptoTrashRed,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (password.length < 5) {
                                errorMessage = "პაროლი უნდა შედგებოდეს მინიმუმ 5 სიმბოლოსგან!"
                            } else if (password != passwordConfirm) {
                                errorMessage = "პაროლები არ ემთხვევა!"
                            } else {
                                onRegister(password)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoMatrixGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("master_register_button")
                    ) {
                        Text("საცავის ინიციალიზაცია", fontWeight = FontWeight.Bold, color = CryptoSlateDark)
                    }
                }
            }
        }
    }
}

@Composable
fun UnlockScreen(
    activity: FragmentActivity,
    biometricEnabled: Boolean,
    onPasswordSubmit: (String) -> Unit,
    onBiometricRequest: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Locked Database",
                tint = CryptoGoldAccent,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "CryptoVault",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = CryptoMatrixGreen,
                fontFamily = FontFamily.Serif
            )
            Text(
                text = "დაშიფრული ოფლაინ კონტეინერი",
                fontSize = 13.sp,
                color = CryptoMutedGrey,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                border = BorderStroke(1.dp, CryptoBorderAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "შეიყვანეთ სამაგისტრო პაროლი საცავის განსაბლოკად",
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("ცენტრალური პაროლი", color = CryptoMutedGrey) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = "Toggle visibility",
                                    tint = CryptoMutedGrey
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("master_login_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            onPasswordSubmit(password)
                            password = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoMatrixGreen),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_submit_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Key, contentDescription = null, tint = CryptoSlateDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("განბლოკვა", fontWeight = FontWeight.Bold, color = CryptoSlateDark)
                    }

                    if (biometricEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = CryptoBorderAccent, modifier = Modifier.padding(horizontal = 24.dp))
                        Spacer(modifier = Modifier.height(16.dp))

                        IconButton(
                            onClick = onBiometricRequest,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CryptoBorderAccent)
                                .testTag("biometric_login_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = "Biometric Login",
                                tint = CryptoMatrixGreen,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Text(
                            text = "სწრაფი ბიომეტრიული შესვლა",
                            color = CryptoMutedGrey,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// Main Workspace Area (Logged In)
// --------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VaultWorkspace(viewModel: VaultViewModel) {
    var activeTab by remember { mutableStateOf("passwords") } // passwords, notes, totp, trash, settings
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    
    // Dialog state for adding items
    var isAddDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(CryptoSlateDark)) {
                // Header Logo and lock button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Shield Logo",
                            tint = CryptoMatrixGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CryptoVault",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = { viewModel.lockVault() },
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoSurface),
                        border = BorderStroke(1.dp, CryptoBorderAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("lock_trigger_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "LockVault",
                            tint = CryptoGoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("საცავის ჩაკეტვა", color = Color.White, fontSize = 12.sp)
                    }
                }

                // If not in Settings or Trash, show Search & Category Filter
                if (activeTab != "settings" && activeTab != "trash") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("ჩაწერეთ საძიებო სიტყვა...", color = CryptoMutedGrey) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search",
                                    tint = CryptoMutedGrey
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CryptoMatrixGreen,
                                unfocusedBorderColor = CryptoBorderAccent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                containerColor = CryptoSurface
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vault_search_field")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Category chips
                        val categories = listOf("All", "Personal", "Work", "Finance", "Social")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setSelectedCategory(cat) },
                                    label = { Text(if (cat == "All") "ყველა" else cat) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CryptoMatrixGreen,
                                        selectedLabelColor = CryptoSlateDark,
                                        containerColor = CryptoSurface,
                                        labelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        borderColor = CryptoBorderAccent,
                                        enabled = true,
                                        selected = isSelected
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = CryptoSurface,
                tonalElevation = 12.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("app_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = activeTab == "passwords",
                    onClick = { activeTab = "passwords" },
                    icon = { Icon(imageVector = Icons.Filled.LockOpen, contentDescription = "Passwords") },
                    label = { Text("პაროლები", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CryptoSlateDark,
                        selectedTextColor = CryptoMatrixGreen,
                        indicatorColor = CryptoMatrixGreen,
                        unselectedIconColor = CryptoMutedGrey,
                        unselectedTextColor = CryptoMutedGrey
                    )
                )

                NavigationBarItem(
                    selected = activeTab == "notes",
                    onClick = { activeTab = "notes" },
                    icon = { Icon(imageVector = Icons.Filled.Description, contentDescription = "Notes") },
                    label = { Text("ბლოკნოტი", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CryptoSlateDark,
                        selectedTextColor = CryptoMatrixGreen,
                        indicatorColor = CryptoMatrixGreen,
                        unselectedIconColor = CryptoMutedGrey,
                        unselectedTextColor = CryptoMutedGrey
                    )
                )

                NavigationBarItem(
                    selected = activeTab == "totp",
                    onClick = { activeTab = "totp" },
                    icon = { Icon(imageVector = Icons.Filled.Timelapse, contentDescription = "2FA") },
                    label = { Text("2FA", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CryptoSlateDark,
                        selectedTextColor = CryptoMatrixGreen,
                        indicatorColor = CryptoMatrixGreen,
                        unselectedIconColor = CryptoMutedGrey,
                        unselectedTextColor = CryptoMutedGrey
                    )
                )

                NavigationBarItem(
                    selected = activeTab == "trash",
                    onClick = { activeTab = "trash" },
                    icon = { Icon(imageVector = Icons.Filled.DeleteOutline, contentDescription = "Trash Bin") },
                    label = { Text("ურნა", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CryptoSlateDark,
                        selectedTextColor = CryptoMatrixGreen,
                        indicatorColor = CryptoMatrixGreen,
                        unselectedIconColor = CryptoMutedGrey,
                        unselectedTextColor = CryptoMutedGrey
                    )
                )

                NavigationBarItem(
                    selected = activeTab == "settings",
                    onClick = { activeTab = "settings" },
                    icon = { Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("მართვა", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CryptoSlateDark,
                        selectedTextColor = CryptoMatrixGreen,
                        indicatorColor = CryptoMatrixGreen,
                        unselectedIconColor = CryptoMutedGrey,
                        unselectedTextColor = CryptoMutedGrey
                    )
                )
            }
        },
        floatingActionButton = {
            if (activeTab == "passwords" || activeTab == "notes" || activeTab == "totp") {
                FloatingActionButton(
                    onClick = { isAddDialogVisible = true },
                    containerColor = CryptoMatrixGreen,
                    contentColor = CryptoSlateDark,
                    modifier = Modifier.testTag("add_item_fab")
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add Item")
                }
            }
        },
        containerColor = CryptoSlateDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                "passwords" -> PasswordsTab(viewModel = viewModel)
                "notes" -> NotesTab(viewModel = viewModel)
                "totp" -> TotpTab(viewModel = viewModel)
                "trash" -> TrashTab(viewModel = viewModel)
                "settings" -> SettingsTab(viewModel = viewModel)
            }
        }
    }

    if (isAddDialogVisible) {
        AddNewItemDialog(
            type = when (activeTab) {
                "passwords" -> "PASSWORD"
                "notes" -> "NOTE"
                else -> "TOTP"
            },
            onDismiss = { isAddDialogVisible = false },
            onSave = { title, username, password, note, secret, category ->
                when (activeTab) {
                    "passwords" -> viewModel.addPasswordItem(title, username, password, category)
                    "notes" -> viewModel.addNoteItem(title, note, category)
                    "totp" -> viewModel.addTotpItem(title, secret, category)
                }
                isAddDialogVisible = false
                Toast.makeText(context, "დამატება წარმატებით შესრულდა!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// --------------------------------------------------------------------------------------------------
// Bottom Nav Tab Implementations
// --------------------------------------------------------------------------------------------------

@Composable
fun PasswordsTab(viewModel: VaultViewModel) {
    val items by viewModel.filteredActiveItems.collectAsStateWithLifecycle()
    val passwordsOnly = items.filter { it.type == "PASSWORD" }
    val context = LocalContext.current

    if (passwordsOnly.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Outlined.LockOpen,
            title = "აქ არაფერია",
            subtitle = "დააჭირეთ '+' ღილაკს ახალი დაშიფრული პაროლის დასამატებლად"
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(passwordsOnly, key = { it.id }) { item ->
                var isExpanded by remember { mutableStateOf(false) }
                var isPasswordVisible by remember { mutableStateOf(false) }
                
                val decryptedUser = remember(item, isExpanded) {
                    if (isExpanded) viewModel.decryptField(item.encryptedUsername, item.ivUsername) else "••••••"
                }
                val decryptedPass = remember(item, isExpanded, isPasswordVisible) {
                    if (isExpanded && isPasswordVisible) viewModel.decryptField(item.encryptedPassword, item.ivPassword) else "••••••••••••"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                    border = BorderStroke(1.dp, CryptoBorderAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .testTag("password_item_${item.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CryptoBorderAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        tint = CryptoMatrixGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = item.category,
                                        color = CryptoMutedGrey,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = CryptoMutedGrey
                            )
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = CryptoBorderAccent)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Username
                            Text("მომხმარებლის სახელი / ელ.ფოსტა", color = CryptoMutedGrey, fontSize = 11.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = decryptedUser,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Username", decryptedUser)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Username კოპირებულია!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Username", tint = CryptoMatrixGreen, modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Password
                            Text("პაროლი", color = CryptoMutedGrey, fontSize = 11.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = decryptedPass,
                                    color = CryptoMatrixGreen,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                        Icon(
                                            imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                            contentDescription = "Show Password",
                                            tint = CryptoMutedGrey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = {
                                        val realPassword = viewModel.decryptField(item.encryptedPassword, item.ivPassword)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Password", realPassword)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "პაროლი კოპირებულია!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Password", tint = CryptoMatrixGreen, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = CryptoBorderAccent)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { 
                                        viewModel.moveToTrash(item)
                                        Toast.makeText(context, "გადატანილია სანაგვე ყუთში!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    border = BorderStroke(1.dp, CryptoTrashRed.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, tint = CryptoTrashRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ურნა", color = CryptoTrashRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotesTab(viewModel: VaultViewModel) {
    val items by viewModel.filteredActiveItems.collectAsStateWithLifecycle()
    val notesOnly = items.filter { it.type == "NOTE" }
    val context = LocalContext.current

    if (notesOnly.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Outlined.Description,
            title = "ბლოკნოტი ცარიელია",
            subtitle = "აქ შეგიძლიათ შეინახოთ უსაფრთხო ჩანაწერები, კოდები, დღიურები ან სხვა ტექსტი"
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notesOnly, key = { it.id }) { item ->
                var isExpanded by remember { mutableStateOf(false) }
                val decryptedNote = remember(item, isExpanded) {
                    if (isExpanded) viewModel.decryptField(item.encryptedNoteContent, item.ivNoteContent) else "••••••••••••••"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                    border = BorderStroke(1.dp, CryptoBorderAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .testTag("note_item_${item.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CryptoBorderAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.EditNote,
                                        contentDescription = null,
                                        tint = CryptoMatrixGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = item.category,
                                        color = CryptoMutedGrey,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = CryptoMutedGrey
                            )
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = CryptoBorderAccent)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = decryptedNote,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Secure Note", decryptedNote)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "ჩანაწერი კოპირებულია!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy Content", tint = CryptoMatrixGreen, modifier = Modifier.size(20.dp))
                                }

                                Button(
                                    onClick = { 
                                        viewModel.moveToTrash(item)
                                        Toast.makeText(context, "გადატანილია სანაგვე ყუთში!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    border = BorderStroke(1.dp, CryptoTrashRed.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null, tint = CryptoTrashRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ურნა", color = CryptoTrashRed, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TotpTab(viewModel: VaultViewModel) {
    val items by viewModel.filteredActiveItems.collectAsStateWithLifecycle()
    val totpOnly = items.filter { it.type == "TOTP" }
    val context = LocalContext.current

    // Ticker state representing remaining ticking seconds for 2fa
    var secondsRemaining by remember { mutableStateOf(30) }

    LaunchedEffect(Unit) {
        while (true) {
            val currentSecond = (System.currentTimeMillis() / 1000) % 30
            secondsRemaining = (30 - currentSecond).toInt()
            delay(1000)
        }
    }

    if (totpOnly.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Outlined.Timelapse,
            title = "2FA კოდები ცარიელია",
            subtitle = "დაამატეთ Base32 საიდუმლო გასაღებები თქვენი ანგარიშებისთვის მყისიერი 6-ნიშნა კოდების მისაღებად!"
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            // Live Indicator top row
            Card(
                colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                border = BorderStroke(1.dp, CryptoBorderAccent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            progress = { secondsRemaining / 30f },
                            color = if (secondsRemaining < 6) CryptoTrashRed else CryptoMatrixGreen,
                            strokeWidth = 4.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "კოდების განახლება ხდება ყოველ 30 წამში",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "$secondsRemaining წმ",
                        fontWeight = FontWeight.Bold,
                        color = if (secondsRemaining < 6) CryptoTrashRed else CryptoMatrixGreen,
                        fontSize = 14.sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(totpOnly, key = { it.id }) { item ->
                    // Decrypt core secret matching
                    val secretDecrypted = remember(item, secondsRemaining) {
                        viewModel.decryptField(item.encryptedTotpSecret, item.ivTotpSecret)
                    }
                    val totpPin = remember(secretDecrypted, secondsRemaining) {
                        if (secretDecrypted.isNotEmpty() && secretDecrypted != "Decryption Error") {
                            TotpUtils.generateTOTP(secretDecrypted)
                        } else {
                            "------"
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                        border = BorderStroke(1.dp, CryptoBorderAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("totp_item_${item.id}")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = item.category,
                                        color = CryptoMutedGrey,
                                        fontSize = 11.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = totpPin.chunked(3).joinToString(" "),
                                        color = CryptoMatrixGreen,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 2.sp,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )

                                    IconButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("2FA Token", totpPin)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "კოდი კოპირებულია!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = "Copy 2FA", tint = CryptoMatrixGreen, modifier = Modifier.size(20.dp))
                                    }

                                    IconButton(onClick = { 
                                        viewModel.moveToTrash(item)
                                        Toast.makeText(context, "გადატანილია სანაგვე ყუთში!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete 2FA", tint = CryptoTrashRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrashTab(viewModel: VaultViewModel) {
    val items by viewModel.trashItems.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (items.isEmpty()) {
        EmptyTabPlaceholder(
            icon = Icons.Outlined.DeleteOutline,
            title = "სანაგვე ყუთი ცარიელია",
            subtitle = "აქ გამოჩნდება წაშლილი პაროლები, ჩანაწერები და 2FA კოდები. საჭიროების შემთხვევაში შეგიძლიათ აღადგინოთ"
        )
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "სულ: ${items.size} ელემენტი",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Button(
                    onClick = {
                        viewModel.clearTrash()
                        Toast.makeText(context, "სანაგვე ყუთი სრულად გასუფთავდა!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CryptoTrashRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("empty_trash_button")
                ) {
                    Icon(imageVector = Icons.Filled.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ურნის დაცლა", color = Color.White, fontSize = 12.sp)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                        border = BorderStroke(1.dp, CryptoBorderAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val typeIcon = when (item.type) {
                                    "PASSWORD" -> Icons.Filled.Key
                                    "NOTE" -> Icons.Filled.EditNote
                                    else -> Icons.Filled.Timelapse
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CryptoBorderAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = typeIcon,
                                        contentDescription = null,
                                        tint = CryptoMutedGrey,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = item.title,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "ტიპი: ${item.type}",
                                        color = CryptoMutedGrey,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Row {
                                IconButton(onClick = {
                                    viewModel.restoreFromTrash(item)
                                    Toast.makeText(context, "აღდგენილია საცავში!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.Restore,
                                        contentDescription = "Restore Item",
                                        tint = CryptoMatrixGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(onClick = {
                                    viewModel.deletePermanently(item)
                                    Toast.makeText(context, "სამუდამოდ წაშლილია!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteForever,
                                        contentDescription = "Delete Permanently",
                                        tint = CryptoTrashRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(viewModel: VaultViewModel) {
    val biometricEnabled by viewModel.biometricEnabled.collectAsStateWithLifecycle()
    var masterPasswordVerify by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    // Backup export-import states
    var backupString by remember { mutableStateOf("") }
    var importString by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Biometrics Management Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                border = BorderStroke(1.dp, CryptoBorderAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ბიომეტრიული ავტორიზაცია",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "გამოიყენეთ მოწყობილობის თითის ანაბეჭდი საცავის სწრაფი და კომფორტული განბლოკვისთვის.",
                        color = CryptoMutedGrey,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    HorizontalDivider(color = CryptoBorderAccent, modifier = Modifier.padding(vertical = 8.dp))

                    if (biometricEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ბიომეტრიული შესვლა აქტიურია", color = CryptoMatrixGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Button(
                                onClick = {
                                    viewModel.disableBiometricUnlock()
                                    Toast.makeText(context, "ბიომეტრიული შესვლა გამორთულია!", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CryptoTrashRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("გამორთვა")
                            }
                        }
                    } else {
                        Column {
                            OutlinedTextField(
                                value = masterPasswordVerify,
                                onValueChange = { masterPasswordVerify = it },
                                label = { Text("სამაგისტრო პაროლის დასტური", color = CryptoMutedGrey) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CryptoMatrixGreen,
                                    unfocusedBorderColor = CryptoBorderAccent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (masterPasswordVerify.isEmpty()) {
                                        Toast.makeText(context, "შეიყვანეთ სამაგისტრო პაროლი დასადასტურებლად!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val success = viewModel.enableBiometricUnlock(masterPasswordVerify)
                                        if (success) {
                                            masterPasswordVerify = ""
                                            Toast.makeText(context, "ბიომეტრიული შესვლა გააქტიურებულია!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "პაროლი არასწორია!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CryptoMatrixGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("enable_biometrics_trigger")
                            ) {
                                Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null, tint = CryptoSlateDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("აქტივაცია ანაბეჭდით", fontWeight = FontWeight.Bold, color = CryptoSlateDark)
                            }
                        }
                    }
                }
            }
        }

        // Export/Import Local Encryption Backups
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CryptoSurface),
                border = BorderStroke(1.dp, CryptoBorderAccent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "მონაცემთა ექსპორტი / იმპორტი",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "თქვენი დაშიფრული ბაზის სრული რეზერვირება ოფლაინ ფორმატში. მონაცემები ექსპორტირდება AES-256 ფორმატით, რაც სრულიად უსაფრთხოს ხდის ნებისმიერ გაზიარებას.",
                        color = CryptoMutedGrey,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    HorizontalDivider(color = CryptoBorderAccent, modifier = Modifier.padding(vertical = 8.dp))

                    // Backup Export Block
                    Button(
                        onClick = {
                            backupString = viewModel.exportEncryptedBackup()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("CryptoVaultBackup", backupString)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "სარეზერვო კოდი კოპირებულია ქლიფბორდში!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoBorderAccent),
                        border = BorderStroke(1.dp, CryptoMatrixGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_backup_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Backup, contentDescription = null, tint = CryptoMatrixGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("სარეზერვო კოდის ექსპორტი", color = Color.White)
                    }

                    if (backupString.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = backupString,
                            onValueChange = {},
                            readOnly = true,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = CryptoMutedGrey,
                                unfocusedTextColor = CryptoMutedGrey,
                                containerColor = CryptoSlateDark,
                                focusedBorderColor = CryptoBorderAccent,
                                unfocusedBorderColor = CryptoBorderAccent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = CryptoBorderAccent, modifier = Modifier.padding(vertical = 8.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Backup Import Block
                    Text(
                        text = "მონაცემთა იმპორტი",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "ჩასვით ექსპორტირებული დაშიფრული JSON კოდი საცავში ინტეგრაციისთვის:",
                        color = CryptoMutedGrey,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    OutlinedTextField(
                        value = importString,
                        onValueChange = { importString = it },
                        placeholder = { Text("ჩაწერეთ აქ JSON მონაცემები...", color = CryptoMutedGrey) },
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            containerColor = CryptoSlateDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_backup_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (importString.isEmpty()) {
                                Toast.makeText(context, "შეიყვანეთ მონაცემები იმპორტისთვის!", Toast.LENGTH_SHORT).show()
                            } else {
                                val result = viewModel.importBackup(importString)
                                if (result.isSuccess) {
                                    val count = result.getOrNull() ?: 0
                                    importString = ""
                                    Toast.makeText(context, "იმპორტირებულია $count ელემენტი წარმატებით!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "იმპორტის შეცდომა: არასწორი ფორმატი!", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CryptoMatrixGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_submit_button")
                    ) {
                        Icon(imageVector = Icons.Filled.CloudUpload, contentDescription = null, tint = CryptoSlateDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("იმპორტის დადასტურება", fontWeight = FontWeight.Bold, color = CryptoSlateDark)
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// Additional Common Helper Composables
// --------------------------------------------------------------------------------------------------

@Composable
fun EmptyTabPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CryptoBorderAccent,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = CryptoMutedGrey,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewItemDialog(
    type: String, // PASSWORD, NOTE, TOTP
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var noteContent by remember { mutableStateOf("") }
    var totpSecret by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Personal") }

    val categories = listOf("Personal", "Work", "Finance", "Social")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (type) {
                    "PASSWORD" -> "ახალი პაროლის დამატება"
                    "NOTE" -> "ახალი ჩანაწერის შექმნა"
                    else -> "ახალი 2FA გასაღების დამატება"
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("სათაური / პროვაიდერი (მაგ: Google)", color = CryptoMutedGrey) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CryptoMatrixGreen,
                        unfocusedBorderColor = CryptoBorderAccent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_input_title")
                )

                // Render dynamic forms matching Vault specifications
                if (type == "PASSWORD") {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("მომხმარებლის სახელი / ელ.ფოსტა", color = CryptoMutedGrey) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_input_username")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("პაროლი", color = CryptoMutedGrey) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_input_password")
                    )
                }

                if (type == "NOTE") {
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("ტექსტური ჩანაწერი / კონტენტი", color = CryptoMutedGrey) },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_input_note")
                    )
                }

                if (type == "TOTP") {
                    OutlinedTextField(
                        value = totpSecret,
                        onValueChange = { totpSecret = it },
                        label = { Text("საიდუმლო გასაღები (Base32 format)", color = CryptoMutedGrey) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CryptoMatrixGreen,
                            unfocusedBorderColor = CryptoBorderAccent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_input_secret")
                    )
                    Text("მაგალითად: JBSWY3DPEHPK3PXP", color = CryptoMutedGrey, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("აირჩიეთ კატეგორია:", color = Color.White, fontSize = 12.sp)

                // Horizontal chips selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CryptoMatrixGreen,
                                selectedLabelColor = CryptoSlateDark,
                                containerColor = CryptoSurface,
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = CryptoBorderAccent,
                                enabled = true,
                                selected = isSelected
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty()) {
                        onSave(title, username, password, noteContent, totpSecret, selectedCategory)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CryptoMatrixGreen),
                enabled = title.isNotEmpty()
            ) {
                Text("შენახვა", color = CryptoSlateDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("გაუქმება", color = CryptoMutedGrey)
            }
        },
        containerColor = CryptoSurface,
        shape = RoundedCornerShape(16.dp)
    )
}
