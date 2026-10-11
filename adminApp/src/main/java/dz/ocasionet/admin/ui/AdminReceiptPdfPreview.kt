package dz.ocasionet.admin.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.min

private const val MAX_RECEIPT_PDF_BYTES = 5L * 1024L * 1024L
private const val MAX_RECEIPT_PDF_PAGES = 8
private const val MAX_RENDER_WIDTH_PX = 1_000
private const val MAX_RENDER_HEIGHT_PX = 1_400

@Composable
internal fun AdminReceiptPdfPreview(
    signedUrl: String,
    onAutoRefresh: () -> Boolean,
    onManualRefresh: () -> Unit
) {
    val context = LocalContext.current
    var pages by remember(signedUrl) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pageCount by remember(signedUrl) { mutableStateOf(0) }
    var isLoading by remember(signedUrl) { mutableStateOf(true) }
    var errorMessage by remember(signedUrl) { mutableStateOf<String?>(null) }

    LaunchedEffect(signedUrl) {
        isLoading = true
        errorMessage = null
        try {
            val rendered = withContext(Dispatchers.IO) {
                renderPrivatePdf(context.cacheDir, signedUrl)
            }
            pages = rendered.bitmaps
            pageCount = rendered.totalPages
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            errorMessage = if (onAutoRefresh()) {
                null
            } else {
                "تعذر فتح ملف PDF. تحقّق من سلامة الإيصال أو أعد المحاولة."
            }
        } finally {
            isLoading = false
        }
    }

    DisposableEffect(pages) {
        onDispose {
            pages.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            isLoading -> CircularProgressIndicator()
            errorMessage != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(errorMessage ?: "تعذر تحميل الملف.", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onManualRefresh) {
                    Text("إعادة تحميل الإيصال")
                }
            }
            pages.isEmpty() -> Text("ملف PDF لا يحتوي صفحات قابلة للعرض.")
            else -> {
                Text(
                    if (pageCount > pages.size) {
                        "معاينة الصفحات ${pages.size} الأولى من أصل $pageCount (الحد الأقصى للمعاينة $MAX_RECEIPT_PDF_PAGES صفحات)."
                    } else {
                        "صفحات الإيصال: $pageCount"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(520.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(pages) { index, bitmap ->
                        Column {
                            Text("الصفحة ${index + 1}", style = MaterialTheme.typography.labelLarge)
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "صفحة ${index + 1} من إيصال الدفع PDF",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(460.dp)
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class RenderedPdf(val bitmaps: List<Bitmap>, val totalPages: Int)

private suspend fun renderPrivatePdf(cacheDirectory: File, signedUrl: String): RenderedPdf {
    val tempFile = File.createTempFile("occasionet-receipt-", ".pdf", cacheDirectory)
    val bitmaps = mutableListOf<Bitmap>()
    var totalPages = 0
    try {
        val request = Request.Builder().url(signedUrl).get().build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Receipt download failed")
            val body = response.body ?: throw IOException("Receipt response is empty")
            if (body.contentLength() > MAX_RECEIPT_PDF_BYTES) throw IOException("Receipt exceeds the permitted size")
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_RECEIPT_PDF_BYTES) throw IOException("Receipt exceeds the permitted size")
                        output.write(buffer, 0, count)
                    }
                }
            }
        }

        ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                totalPages = renderer.pageCount
                val pageLimit = min(totalPages, MAX_RECEIPT_PDF_PAGES)
                for (pageIndex in 0 until pageLimit) {
                    renderer.openPage(pageIndex).use { page ->
                        val scale = min(
                            min(MAX_RENDER_WIDTH_PX.toDouble() / page.width, MAX_RENDER_HEIGHT_PX.toDouble() / page.height),
                            1.0
                        )
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps += bitmap
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return RenderedPdf(bitmaps, totalPages)
    } catch (error: Exception) {
        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        throw error
    } finally {
        tempFile.delete()
    }
}
