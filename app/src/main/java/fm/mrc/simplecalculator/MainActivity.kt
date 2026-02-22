package fm.mrc.simplecalculator

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.SoundEffectConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import fm.mrc.simplecalculator.ui.theme.SimpleCalculatorTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Stack
import kotlin.random.Random

object HistoryManager {
    private const val HISTORY_KEY = "calculator_history"
    private const val PREFS_NAME = "CalculatorPrefs"
    private const val SEPARATOR = "|||"

    suspend fun saveHistory(context: Context, history: List<String>) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyString = history.joinToString(SEPARATOR)
        prefs.edit { putString(HISTORY_KEY, historyString) }
    }

    suspend fun loadHistory(context: Context): List<String> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyString = prefs.getString(HISTORY_KEY, null)
        historyString?.split(SEPARATOR)?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun clearHistory(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(HISTORY_KEY) }
    }
}

fun evaluateExpression(expression: String): Double {
    try {
        // Strip trailing non-digit/parenthesis characters to handle partial expressions like "2+"
        val sanitizedExpression = expression.replace(Regex("[^0-9.]+$"), "")
        if (sanitizedExpression.isEmpty()) return 0.0

        val tokens = sanitizedExpression.replace(" ", "").split(Regex("(?<=[+×÷-])|(?=[+×÷-])"))

        val values = Stack<Double>()
        val ops = Stack<Char>()

        for (i in tokens.indices) {
            val token = tokens[i]
            if (token.isEmpty()) continue
            if (token[0] in '0'..'9') {
                values.push(token.toDouble())
            } else {
                while (ops.isNotEmpty() && hasPrecedence(token[0], ops.peek())) {
                    if (values.size >= 2) {
                        values.push(applyOp(ops.pop(), values.pop(), values.pop()))
                    } else {
                        ops.pop() // Discard invalid operator if stack insufficient
                    }
                }
                ops.push(token[0])
            }
        }

        while (ops.isNotEmpty()) {
             if (values.size >= 2) {
                values.push(applyOp(ops.pop(), values.pop(), values.pop()))
             } else {
                 ops.pop()
             }
        }

        return if (values.isNotEmpty()) values.pop() else 0.0
    } catch (_: Exception) {
        return Double.NaN
    }
}

fun hasPrecedence(op1: Char, op2: Char): Boolean {
    return !((op1 == '×' || op1 == '÷') && (op2 == '+' || op2 == '-'))
}

fun applyOp(op: Char, b: Double, a: Double): Double {
    return when (op) {
        '+' -> a + b
        '-' -> a - b
        '×' -> a * b
        '÷' -> if (b != 0.0) a / b else Double.NaN
        else -> 0.0
    }
}

fun formatResult(result: Double): String {
    return if (result.isNaN() || result.isInfinite()) {
        "Error"
    } else if (result == result.toLong().toDouble()) {
        result.toLong().toString()
    } else {
        String.format(Locale.US, "%.10f", result).trimEnd('0').trimEnd('.')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private lateinit var calculatorState: CalculatorState

    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.getOrNull(0)?.let {
                calculatorState.processVoiceInput(it)
            }
        } else {
            calculatorState.onVoiceRecognitionError(result.resultCode)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        isGranted: Boolean ->
        if (isGranted) {
            launchSpeechRecognizer()
        } else {
            calculatorState.onMicPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            calculatorState = rememberCalculatorState()
            SimpleCalculatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalculatorScreen(calculatorState, onVoiceInput = {
                        requestPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    })
                }
            }
        }
    }

    private fun isSpeechRecognizerAvailable(): Boolean {
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val activities = packageManager.queryIntentActivities(recognizerIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return activities.isNotEmpty()
    }

    private fun launchSpeechRecognizer() {
        if (!isSpeechRecognizerAvailable()) {
            calculatorState.onSpeechRecognizerNotFound()
            return
        }

        val examples = listOf(
            "25 multiplied by 45",
            "100 divided by 4",
            "12 plus 48",
            "90 minus 15",
            "10 over 2",
            "30 times 5",
            "add 5 to 10",
            "subtract 20 from 50"
        )
        val randomExample = examples[Random.nextInt(examples.size)]
        val prompt = "Speak now\nEg: \"$randomExample\""

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (_: Exception) {
            calculatorState.onVoiceRecognitionError(0) // Generic error
        }
    }
}

@Composable
fun rememberCalculatorState(): CalculatorState {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalContext.current as? LifecycleOwner

    val state = remember { CalculatorState(context, coroutineScope) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.reloadHistory()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }
    return state
}

class CalculatorState(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    var display by mutableStateOf("0")
    var expression by mutableStateOf("")
    var history by mutableStateOf<List<String>>(emptyList())
    var isResultShown by mutableStateOf(false)

    init {
        reloadHistory()
    }

    fun reloadHistory() {
        coroutineScope.launch {
            history = HistoryManager.loadHistory(context)
        }
    }

    private fun updateDisplayWithResult() {
        val result = evaluateExpression(expression)
        display = formatResult(result)
    }

    fun handleNumberInput(number: String) {
        if (isResultShown) {
             expression = ""
             isResultShown = false
        }
        expression += number
        updateDisplayWithResult()
    }

    fun handleOperator(op: String) {
        if (isResultShown) {
            // Use the previous result from the expression "2+3=5" as the start of the new expression
            val lastResult = expression.split("=").lastOrNull() ?: "0"
            expression = lastResult
            isResultShown = false
        }
        if (expression.isNotEmpty() && expression.last().toString().matches(Regex("[+×÷-]"))) {

             expression = expression.dropLast(1)
        }
        expression += op
        updateDisplayWithResult()
    }

    fun handleEquals() {
        if (expression.isEmpty() || isResultShown) return
        val result = evaluateExpression(expression)
        val resultString = formatResult(result)
        val calculationString = "$expression=$resultString"
        val newHistoryItemWithDate = "${System.currentTimeMillis()};$calculationString"
        val newHistory = listOf(newHistoryItemWithDate) + history
        history = newHistory
        coroutineScope.launch {
            HistoryManager.saveHistory(context, newHistory)
        }

        expression = calculationString 
        display = resultString         

        isResultShown = true
    }

    fun handleClear() {
        display = "0"
        expression = ""
        isResultShown = false
    }

    fun handleDecimal() {
        if (isResultShown) {
             expression = "0"
             isResultShown = false
        }
        // Check last number segment
        val lastNumber = expression.split(Regex("[+×÷-]")).lastOrNull() ?: ""
        if (!lastNumber.contains(".")) {
             if (expression.isEmpty()) expression = "0"
             expression += "."
             updateDisplayWithResult()
        }
    }

    fun handleBackspace() {
        if (isResultShown) {
             handleClear()
             return
        }
        if (expression.isNotEmpty()) {
            expression = expression.dropLast(1)
            if (expression.isEmpty()) {
                display = "0"
            } else {
                updateDisplayWithResult()
            }
        }
    }

    fun onMicPermissionDenied() {
        display = "Mic permission needed"
        expression = ""
    }

    fun onSpeechRecognizerNotFound() {
        display = "Speech recognizer not found"
        expression = ""
    }

    fun onVoiceRecognitionError(errorCode: Int) {
        val message = when (errorCode) {
            RecognizerIntent.RESULT_AUDIO_ERROR -> "Audio error"
            RecognizerIntent.RESULT_CLIENT_ERROR -> "Client error"
            RecognizerIntent.RESULT_NETWORK_ERROR -> "Network error"
            RecognizerIntent.RESULT_NO_MATCH -> "No match found"
            RecognizerIntent.RESULT_SERVER_ERROR -> "Server error"
            else -> "Recognition failed"
        }
        display = message
        expression = ""
    }

    fun processVoiceInput(input: String) {
        var processed = input.lowercase(Locale.getDefault())
            .replace(":", "") // Handle 3:22 -> 322
            .replace("one", "1")
            .replace("two", "2")
            .replace("to", "2")
            .replace("three", "3")
            .replace("four", "4")
            .replace("for", "4")
            .replace("five", "5")
            .replace("six", "6")
            .replace("seven", "7")
            .replace("eight", "8")
            .replace("nine", "9")
            .replace("zero", "0")
            
            // Addition
            .replace("plus", "+")
            .replace("add", "+")
            .replace("sum", "+")
            .replace("and", "+")
            
            // Subtraction
            .replace("minus", "-")
            .replace("subtract", "-")
            .replace("take away", "-")
            .replace("less", "-")
            
            // Multiplication
            .replace("times", "×")
            .replace("x", "×")
            .replace("multiplied by", "×")
            .replace("multiplied", "×")
            .replace("into", "×")
            .replace("*", "×")
            
            // Division
            .replace("divided by", "÷")
            .replace("divide by", "÷")
            .replace("divide", "÷")
            .replace("over", "÷")
            .replace("/", "÷")
            .replace("by", "÷")

        // Handle cases like "add 5 to 10" which results in "+ 5 2 10" (because of 'to' -> '2')
        // Or "subtract 20 from 50"
        // Let's refine the regex split later if needed. For now, just strip extra spaces.
        processed = processed.replace(Regex("\\s+"), "")

        expression = processed
        isResultShown = false
        handleEquals()
    }
}

@Composable
fun CalculatorButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    backgroundColor: Color? = null,
    textColor: Color? = null
) {
    val defaultBackgroundColor = Color(0xFF3C3C3C)
    val defaultTextColor = Color.White
    val buttonBgColor = backgroundColor ?: defaultBackgroundColor
    val buttonTextColor = textColor ?: defaultTextColor
    val view = LocalView.current

    Button(
        onClick = {
            view.playSoundEffect(SoundEffectConstants.CLICK)
            onClick()
        },
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonBgColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 4.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = buttonTextColor
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CalculatorIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    backgroundColor: Color? = null,
    tint: Color? = null
) {
    val defaultBackgroundColor = Color(0xFF3C3C3C)
    val defaultTint = Color.White
    val buttonBgColor = backgroundColor ?: defaultBackgroundColor
    val iconTint = tint ?: defaultTint
    val view = LocalView.current

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState()
    ) {
        Button(
            onClick = {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                onClick()
            },
            modifier = modifier.height(78.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonBgColor
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 4.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CalculatorScreen(calculatorState: CalculatorState, onVoiceInput: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when expression or display changes
    LaunchedEffect(calculatorState.expression, calculatorState.display) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calculator") },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text("History for calculator user") } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { context.startActivity(Intent(context, HistoryActivity::class.java)) }) {
                            Icon(Icons.Default.History, contentDescription = "History", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1C))
                .padding(it)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp), // Slightly reduced padding to save space
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.2f), // Increased weight for display area
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2C2C2C)
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 12.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        val isResult = calculatorState.isResultShown
                        val expressionColor = if (isResult) Color(0xFFB0B0B0) else Color.White
                        val displayColor = if (isResult) Color.White else Color(0xFFB0B0B0)
                        
                        // Local state to manage font size dynamically based on line count
                        var currentExpressionSize by remember { mutableStateOf(52.sp) }
                        
                        // Reset size when expression is cleared or starts fresh
                        LaunchedEffect(calculatorState.expression) {
                            if (calculatorState.expression.isEmpty()) {
                                currentExpressionSize = 52.sp
                            }
                        }

                        val expressionSize = if (isResult) 24.sp else currentExpressionSize
                        val displaySize = if (isResult) 52.sp else 24.sp

                        Text(
                            text = calculatorState.expression,
                            fontSize = expressionSize,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            color = expressionColor,
                            lineHeight = (expressionSize.value * 1.1).sp,
                            onTextLayout = { textLayoutResult ->
                                if (!isResult && textLayoutResult.lineCount > 3 && currentExpressionSize > 24.sp) {
                                    currentExpressionSize = 24.sp
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = calculatorState.display,
                            fontSize = displaySize,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.End,
                            color = displayColor,
                            lineHeight = (displaySize.value * 1.1).sp
                        )
                    }
                }

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Clear, Backspace, Divide
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalculatorButton(
                            text = "C",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleClear() },
                            backgroundColor = Color(0xFFD32F2F),
                            textColor = Color.White
                        )
                        CalculatorIconButton(
                            icon = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = "Backspace",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleBackspace() },
                            backgroundColor = Color(0xFFD32F2F),
                            tint = Color.White
                        )
                        CalculatorButton(
                            text = "÷",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleOperator("÷") },
                            backgroundColor = Color(0xFFFF9800),
                            textColor = Color.White
                        )
                    }

                    // Row 2: 7, 8, 9, ×
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalculatorButton(
                            text = "7",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("7") }
                        )
                        CalculatorButton(
                            text = "8",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("8") }
                        )
                        CalculatorButton(
                            text = "9",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("9") }
                        )
                        CalculatorButton(
                            text = "×",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleOperator("×") },
                            backgroundColor = Color(0xFFFF9800),
                            textColor = Color.White
                        )
                    }

                    // Row 3: 4, 5, 6, -
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalculatorButton(
                            text = "4",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("4") }
                        )
                        CalculatorButton(
                            text = "5",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("5") }
                        )
                        CalculatorButton(
                            text = "6",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("6") }
                        )
                        CalculatorButton(
                            text = "-",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleOperator("-") },
                            backgroundColor = Color(0xFFFF9800),
                            textColor = Color.White
                        )
                    }

                    // Row 4: 1, 2, 3, +
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalculatorButton(
                            text = "1",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("1") }
                        )
                        CalculatorButton(
                            text = "2",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("2") }
                        )
                        CalculatorButton(
                            text = "3",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("3") }
                        )
                        CalculatorButton(
                            text = "+",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleOperator("+") },
                            backgroundColor = Color(0xFFFF9800),
                            textColor = Color.White
                        )
                    }

                    // Row 5: 0, Mic, ., =
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CalculatorButton(
                            text = "0",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleNumberInput("0") }
                        )
                        CalculatorIconButton(
                            icon = Icons.Default.Mic,
                            contentDescription = "Mic message",
                            modifier = Modifier.weight(1f),
                            onClick = onVoiceInput,
                            backgroundColor = Color(0xFF2196F3)
                        )
                        CalculatorButton(
                            text = ".",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleDecimal() }
                        )
                        CalculatorButton(
                            text = "=",
                            modifier = Modifier.weight(1f),
                            onClick = { calculatorState.handleEquals() },
                            backgroundColor = Color(0xFF4CAF50),
                            textColor = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun CalculatorPreview() {
    SimpleCalculatorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            // In preview, we provide a dummy state and onVoiceInput
            CalculatorScreen(rememberCalculatorState(), onVoiceInput = {})
        }
    }
}
