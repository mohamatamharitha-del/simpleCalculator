package fm.mrc.simplecalculator

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap

class HistoryActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val coroutineScope = rememberCoroutineScope()
            HistoryScreen(onBack = { finish() }, coroutineScope = coroutineScope)
        }
    }
}

@Composable
fun DateHeader(date: Date) {
    val formatter = remember {
        SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
    }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    val dateText = when {
        android.text.format.DateUtils.isToday(date.time) -> "Today (${formatter.format(date)})"
        android.text.format.DateUtils.isToday(date.time + android.text.format.DateUtils.DAY_IN_MILLIS) -> "Yesterday (${formatter.format(date)})"
        else -> formatter.format(date)
    }

    Text(
        text = dateText,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2C2C2C))
            .padding(vertical = 4.dp),
        color = Color.White,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier, onBack: () -> Unit, coroutineScope: CoroutineScope) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf<Date?>(null) }
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var groupedHistory by remember { mutableStateOf<Map<Date, List<String>>>(emptyMap()) }

    fun clearHistory() {
        coroutineScope.launch {
            HistoryManager.clearHistory(context)
            groupedHistory = emptyMap()
        }
    }

    LaunchedEffect(searchQuery, selectedDate) {
        withContext(Dispatchers.IO) {
            val history = HistoryManager.loadHistory(context)
            val filteredHistory = history.filter { item ->
                val parts = item.split(";", limit = 2)
                val timestamp = parts.getOrNull(0)?.toLongOrNull()
                val calculation = parts.getOrNull(1)

                val matchesSearch = calculation?.contains(searchQuery, ignoreCase = true) ?: false

                val matchesDate = if (timestamp != null && selectedDate != null) {
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    val selectedCal = Calendar.getInstance().apply { time = selectedDate!! }
                    calendar.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR) &&
                            calendar.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR)
                } else {
                    selectedDate == null
                }

                matchesSearch && matchesDate
            }

            val map = TreeMap<Date, MutableList<String>>(compareByDescending { it })
            filteredHistory.forEach { item ->
                val parts = item.split(";", limit = 2)
                val timestamp = parts.getOrNull(0)?.toLongOrNull()
                val calculation = parts.getOrNull(1)
                if (timestamp != null && calculation != null) {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = timestamp
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val date = cal.time
                    map.getOrPut(date) { mutableListOf() }.add(calculation)
                }
            }
            withContext(Dispatchers.Main) {
                groupedHistory = map
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to clear all history? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    clearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                    IconButton(onClick = { showClearHistoryDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1C1C1C),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF1C1C1C)
    ) {
        Column(modifier = Modifier.padding(it)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                label = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF2C2C2C),
                    unfocusedContainerColor = Color(0xFF2C2C2C),
                    cursorColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray
                )
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let {
                                selectedDate = Date(it)
                            }
                            showDatePicker = false
                        }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showDatePicker = false
                        }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.End,
                reverseLayout = true
            ) {
                groupedHistory.keys.reversed().forEach { date ->
                    val calculations = groupedHistory[date].orEmpty()
                    stickyHeader {
                        DateHeader(date = date)
                    }
                    items(calculations.reversed()) { calculation ->
                        Text(
                            text = calculation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            color = Color.White,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
        }
    }
}
