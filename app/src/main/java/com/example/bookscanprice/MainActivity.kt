package com.example.bookscanprice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot(vm: BookViewModel = viewModel()) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Scanning -> {
            if (hasCameraPermission) {
                ScannerScreen(onBarcode = vm::onBarcode)
            } else {
                CenteredMessage("Camera permission is required to scan barcodes.") {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
        is UiState.Loading -> CenteredMessage("Looking up book…", showSpinner = true)
        is UiState.Result -> ResultScreen(book = s.book, onScanAgain = vm::scanAgain)
    }
}

@Composable
fun CenteredMessage(text: String, showSpinner: Boolean = false, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showSpinner) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Grant permission") }
        }
    }
}

@Composable
fun ScannerScreen(onBarcode: (String) -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = Executors.newSingleThreadExecutor()
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(executor, BarcodeAnalyzer(onBarcode)) }
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp
        ) {
            Text(
                "Point the camera at the book's barcode (back cover)",
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun ResultScreen(book: BookInfo, onScanAgain: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun money(v: Double?) = v?.let { String.format(Locale.US, "$%.2f", it) } ?: "—"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        if (book.error != null) {
            Text("ISBN ${book.isbn}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(book.error, color = MaterialTheme.colorScheme.error)
        } else {
            Text(book.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (book.authors.isNotBlank()) Text(book.authors, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    book.publisher.ifBlank { null },
                    book.publishedDate.ifBlank { null },
                    book.pageCount?.let { "$it pages" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall
            )
            Text("ISBN ${book.isbn}", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(24.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Estimated sell price", style = MaterialTheme.typography.labelLarge)
                    Text(
                        money(book.estimatedSellPrice),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (book.ebayActiveMedian != null) {
                        Text("eBay active listings: ${book.ebayActiveCount} found, " +
                                "min ${money(book.ebayActiveMin)}, median ${money(book.ebayActiveMedian)}")
                        Text("Estimate = median ask × 0.85 (sold prices run below asks).",
                            style = MaterialTheme.typography.bodySmall)
                    } else if (book.googleRetailPrice != null || book.googleListPrice != null) {
                        Text("Google Books retail: ${money(book.googleRetailPrice ?: book.googleListPrice)}")
                        Text("Estimate = ~40% of retail (typical used-book range). " +
                                "Check the links below for real market prices.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("No pricing data from free sources. Use the links below to check " +
                                "actual sold prices.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Check real market prices", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    open("https://www.ebay.com/sch/i.html?_nkw=${book.isbn}&LH_Sold=1&LH_Complete=1")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("eBay SOLD listings (best signal)") }
            OutlinedButton(
                onClick = { open("https://www.ebay.com/sch/i.html?_nkw=${book.isbn}") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("eBay active listings") }
            OutlinedButton(
                onClick = { open("https://bookscouter.com/book/${book.isbn}") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("BookScouter buyback offers") }
            OutlinedButton(
                onClick = { open("https://www.abebooks.com/servlet/SearchResults?isbn=${book.isbn}") },
                modifier = Modifier.fillMaxWidth()
            ) { Text("AbeBooks used prices") }
        }

        Spacer(Modifier.height(28.dp))
        Button(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Scan another book")
        }
        Spacer(Modifier.height(24.dp))
    }
}
