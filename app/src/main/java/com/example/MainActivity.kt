package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.FinanceRepository
import com.example.data.authStateFlow
import com.example.data.signOut
import com.example.model.Category
import com.example.model.Transaction
import com.example.model.TransactionType
import com.example.model.UiState
import com.example.ui.FinanceViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.components.CategoryEditorSheet
import com.example.ui.components.MonthConfigDialog
import com.example.ui.components.TransactionEditorSheet
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ReportScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.KasKuTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KasKuTheme {
                AppNavigation()
            }
        }
    }
}

private enum class KasKuTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS("transactions", R.string.nav_transactions, Icons.Rounded.ReceiptLong, Icons.Outlined.ReceiptLong),
    CATEGORIES("categories", R.string.nav_categories, Icons.Rounded.Category, Icons.Outlined.Category),
    REPORTS("reports", R.string.nav_reports, Icons.Rounded.PictureAsPdf, Icons.Outlined.PictureAsPdf)
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { /* authStateFlow reacts automatically */ })
    } else {
        key(user.uid) {
            KasKuAuthenticatedApp(
                currentUserId = user.uid,
                userDisplayName = user.displayName.orEmpty(),
                userEmail = user.email.orEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KasKuAuthenticatedApp(
    currentUserId: String,
    userDisplayName: String,
    userEmail: String
) {
    val viewModel: FinanceViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                FinanceViewModel(FinanceRepository(db), currentUserId)
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val reportState by viewModel.reportState.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val selectedYearMonth by viewModel.selectedYearMonth.collectAsStateWithLifecycle()
    val selectedCategoryFilter by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(KasKuTab.DASHBOARD) }

    // Modal editors
    var showTransactionSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showMonthConfigDialog by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var defaultCategoryType by remember { mutableStateOf(TransactionType.EXPENSE) }

    BackHandler(enabled = currentTab != KasKuTab.DASHBOARD) {
        currentTab = KasKuTab.DASHBOARD
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val loadedCategories = (categoriesState as? UiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(Modifier.height(16.dp))
                    KasKuTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationRailItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            modifier = Modifier.testTag("nav_rail_${tab.route}")
                        )
                    }
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (currentTab) {
                                        KasKuTab.DASHBOARD -> stringResource(R.string.app_name)
                                        KasKuTab.TRANSACTIONS -> stringResource(R.string.nav_transactions)
                                        KasKuTab.CATEGORIES -> stringResource(R.string.cat_title)
                                        KasKuTab.REPORTS -> stringResource(R.string.report_title)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.selectCurrentMonth() },
                                modifier = Modifier.testTag("jump_today_month_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Today,
                                    contentDescription = "Ke bulan ini"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            KasKuTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_bar_${tab.route}")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (currentTab == KasKuTab.DASHBOARD || currentTab == KasKuTab.TRANSACTIONS) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                editingTransaction = null
                                showTransactionSheet = true
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.cd_add_transaction)
                                )
                            },
                            text = { Text("Catat") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.testTag("fab_add_transaction")
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                    ) {
                        when (val state = reportState) {
                            is UiState.Loading -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        text = "Memuat catatan keuangan…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            is UiState.Error -> {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp)
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text(
                                            text = "Terjadi kendala sinkronisasi",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            is UiState.Success -> {
                                val report = state.data
                                when (currentTab) {
                                    KasKuTab.DASHBOARD -> DashboardScreen(
                                        report = report,
                                        categories = loadedCategories,
                                        onPreviousMonth = viewModel::selectPreviousMonth,
                                        onNextMonth = viewModel::selectNextMonth,
                                        onEditMonthConfig = { showMonthConfigDialog = true },
                                        onTransactionClick = { tx ->
                                            editingTransaction = tx
                                            showTransactionSheet = true
                                        },
                                        onSeeAllTransactions = {
                                            viewModel.setCategoryFilter(null)
                                            currentTab = KasKuTab.TRANSACTIONS
                                        }
                                    )

                                    KasKuTab.TRANSACTIONS -> TransactionsScreen(
                                        report = report,
                                        categories = loadedCategories,
                                        selectedCategoryId = selectedCategoryFilter,
                                        onSelectCategoryFilter = viewModel::setCategoryFilter,
                                        onPreviousMonth = viewModel::selectPreviousMonth,
                                        onNextMonth = viewModel::selectNextMonth,
                                        onTransactionClick = { tx ->
                                            editingTransaction = tx
                                            showTransactionSheet = true
                                        }
                                    )

                                    KasKuTab.CATEGORIES -> CategoriesScreen(
                                        report = report,
                                        categories = loadedCategories,
                                        onPreviousMonth = viewModel::selectPreviousMonth,
                                        onNextMonth = viewModel::selectNextMonth,
                                        onAddCategory = { defaultType ->
                                            editingCategory = null
                                            defaultCategoryType = defaultType
                                            showCategorySheet = true
                                        },
                                        onEditCategory = { cat ->
                                            editingCategory = cat
                                            defaultCategoryType = cat.parsedType
                                            showCategorySheet = true
                                        },
                                        onInspectCategoryTransactions = { catId ->
                                            viewModel.setCategoryFilter(catId)
                                            currentTab = KasKuTab.TRANSACTIONS
                                        }
                                    )

                                    KasKuTab.REPORTS -> ReportScreen(
                                        report = report,
                                        userDisplayName = userDisplayName,
                                        userEmail = userEmail,
                                        onPreviousMonth = viewModel::selectPreviousMonth,
                                        onNextMonth = viewModel::selectNextMonth,
                                        onNotify = viewModel::emitMessage,
                                        onSignOutClick = {
                                            signOut(credentialManager, scope)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTransactionSheet) {
        TransactionEditorSheet(
            editingTransaction = editingTransaction,
            defaultYearMonth = selectedYearMonth,
            categories = loadedCategories,
            onDismiss = { showTransactionSheet = false },
            onSave = { editId, title, amount, type, catId, catName, payment, note, dateMillis ->
                viewModel.saveTransaction(
                    editingId = editId,
                    title = title,
                    amount = amount,
                    type = type,
                    categoryId = catId,
                    categoryName = catName,
                    paymentMethod = payment,
                    note = note,
                    dateMillis = dateMillis,
                    onComplete = { showTransactionSheet = false }
                )
            },
            onDelete = { txId -> viewModel.deleteTransaction(txId) }
        )
    }

    val currentReport = (reportState as? UiState.Success)?.data
    if (showMonthConfigDialog && currentReport != null) {
        MonthConfigDialog(
            monthLabel = currentReport.monthLabel,
            initialOpeningBalance = currentReport.openingBalance,
            initialMonthlyBudget = currentReport.monthlyBudget,
            onDismiss = { showMonthConfigDialog = false },
            onSave = { opening, budget ->
                viewModel.saveMonthConfig(
                    openingBalance = opening,
                    monthlyBudget = budget,
                    onComplete = { showMonthConfigDialog = false }
                )
            }
        )
    }

    if (showCategorySheet) {
        CategoryEditorSheet(
            editingCategory = editingCategory,
            defaultType = defaultCategoryType,
            onDismiss = { showCategorySheet = false },
            onSave = { editId, name, type, iconKey, colorHex, budgetLimit ->
                viewModel.saveCategory(
                    editingId = editId,
                    name = name,
                    type = type,
                    iconKey = iconKey,
                    colorHex = colorHex,
                    budgetLimit = budgetLimit,
                    onComplete = { showCategorySheet = false }
                )
            },
            onDelete = { catId -> viewModel.deleteCategory(catId) }
        )
    }
}
