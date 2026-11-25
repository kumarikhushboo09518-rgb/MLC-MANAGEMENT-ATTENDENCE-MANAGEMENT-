package com.example.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.model.AppSettings
import com.example.data.model.AttendanceRecord
import com.example.data.model.FineRecord
import com.example.data.model.MlcProfile
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.data.repository.BackupPayload
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    fun compressLogoToBase64(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val original = input.use { BitmapFactory.decodeStream(it) } ?: return null
            val maxDim = 144
            val scale = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
            val w = (original.width * scale).toInt().coerceAtLeast(1)
            val h = (original.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(original, w, h, true)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 78, baos)
            val bytes = baos.toByteArray()
            if (bytes.size > 40000) return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64Bitmap(base64: String?): Bitmap? {
        if (base64.isNullOrBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}

object ReportExporter {

    private fun csvEscape(value: String): String {
        val clean = value.replace("\"", "\"\"")
        return "\"$clean\""
    }

    fun buildCsv(headers: List<String>, rows: List<List<String>>): ByteArray {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for clean Excel Hindi & ₹ rendering
        sb.append(headers.joinToString(",") { csvEscape(it) }).append("\r\n")
        for (row in rows) {
            sb.append(row.joinToString(",") { csvEscape(it) }).append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun buildPdfReport(
        title: String,
        mlcName: String,
        teacherName: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>
    ): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - margin * 2

        val navyPaint = Paint().apply { color = Color.rgb(15, 36, 71); isAntiAlias = true }
        val greenPaint = Paint().apply { color = Color.rgb(16, 185, 129); isAntiAlias = true }
        val whiteTitlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val whiteSubPaint = Paint().apply {
            color = Color.rgb(220, 230, 245)
            textSize = 10.5f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.rgb(15, 36, 71)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 9.5f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(226, 232, 240)
            strokeWidth = 1f
        }

        val colCount = headers.size.coerceAtLeast(1)
        val colWidth = contentWidth / colCount

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        fun drawHeaderBand() {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), 96f, navyPaint)
            canvas.drawRect(0f, 92f, pageWidth.toFloat(), 96f, greenPaint)
            canvas.drawText("MALABAR GROUP • TUITION MANAGEMENT", margin, 32f, whiteSubPaint)
            canvas.drawText(title, margin, 56f, whiteTitlePaint)
            canvas.drawText("$mlcName  |  $teacherName  |  $subtitle", margin, 78f, whiteSubPaint)
        }

        fun drawTableHeaders(yStart: Float): Float {
            headers.forEachIndexed { idx, h ->
                val text = if (h.length > 20) h.take(18) + "…" else h
                canvas.drawText(text, margin + idx * colWidth, yStart, headerPaint)
            }
            canvas.drawLine(margin, yStart + 6f, pageWidth - margin, yStart + 6f, linePaint)
            return yStart + 22f
        }

        drawHeaderBand()
        var y = drawTableHeaders(124f)

        for (row in rows) {
            if (y > pageHeight - 48f) {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                drawHeaderBand()
                y = drawTableHeaders(124f)
            }
            row.forEachIndexed { idx, cell ->
                val text = if (cell.length > 22) cell.take(20) + "…" else cell
                canvas.drawText(text, margin + idx * colWidth, y, bodyPaint)
            }
            canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
            y += 22f
        }

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    fun serializeBackupJson(
        profile: MlcProfile?,
        settings: AppSettings?,
        students: List<Student>,
        attendance: List<AttendanceRecord>,
        fines: List<FineRecord>,
        payments: List<PaymentRecord>
    ): ByteArray {
        val root = JSONObject()
        root.put("app", "MALABAR_GROUP_TUITION_MANAGEMENT")
        root.put("version", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())

        if (profile != null) {
            root.put("mlcProfile", JSONObject().apply {
                put("teacherName", profile.teacherName)
                put("mlcName", profile.mlcName)
                put("mlcLocation", profile.mlcLocation)
                if (!profile.logoData.isNullOrBlank()) put("logoData", profile.logoData)
            })
        }

        if (settings != null) {
            root.put("appSettings", JSONObject().apply {
                put("languageCode", settings.languageCode)
                put("defaultFineAmount", settings.defaultFineAmount)
                put("sundayHolidayEnabled", settings.sundayHolidayEnabled)
                put("notificationsEnabled", settings.notificationsEnabled)
            })
        }

        root.put("students", JSONArray().apply {
            students.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("studentCode", s.studentCode)
                    put("fullName", s.fullName)
                    if (!s.parentName.isNullOrBlank()) put("parentName", s.parentName)
                    if (!s.contactNumber.isNullOrBlank()) put("contactNumber", s.contactNumber)
                    put("active", s.active)
                })
            }
        })

        root.put("attendanceRecords", JSONArray().apply {
            attendance.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("studentId", a.studentId)
                    put("studentName", a.studentName)
                    put("dateKey", a.dateKey)
                    put("status", a.status)
                })
            }
        })

        root.put("fineRecords", JSONArray().apply {
            fines.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    put("studentId", f.studentId)
                    put("studentName", f.studentName)
                    put("attendanceDate", f.attendanceDate)
                    put("amount", f.amount)
                    put("paidAmount", f.paidAmount)
                    put("status", f.status)
                })
            }
        })

        root.put("paymentRecords", JSONArray().apply {
            payments.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("studentId", p.studentId)
                    put("studentName", p.studentName)
                    put("amountPaid", p.amountPaid)
                    put("remainingAfter", p.remainingAfter)
                    put("paymentDateKey", p.paymentDateKey)
                    put("paymentTimeText", p.paymentTimeText)
                    if (!p.note.isNullOrBlank()) put("note", p.note)
                })
            }
        })

        return root.toString(2).toByteArray(Charsets.UTF_8)
    }

    fun parseBackupJson(rawJson: String): BackupPayload? {
        return try {
            val root = JSONObject(rawJson)
            if (root.optString("app") != "MALABAR_GROUP_TUITION_MANAGEMENT") return null

            val profile = root.optJSONObject("mlcProfile")?.let { obj ->
                MlcProfile(
                    id = "default",
                    teacherName = obj.optString("teacherName"),
                    mlcName = obj.optString("mlcName"),
                    mlcLocation = obj.optString("mlcLocation"),
                    logoData = obj.optString("logoData").takeIf { it.isNotBlank() }
                )
            }

            val settings = root.optJSONObject("appSettings")?.let { obj ->
                AppSettings(
                    id = "preferences",
                    languageCode = obj.optString("languageCode", "en"),
                    defaultFineAmount = obj.optLong("defaultFineAmount", 5L),
                    sundayHolidayEnabled = obj.optBoolean("sundayHolidayEnabled", true),
                    notificationsEnabled = obj.optBoolean("notificationsEnabled", true)
                )
            }

            val studentsArr = root.optJSONArray("students") ?: JSONArray()
            val students = (0 until studentsArr.length()).mapNotNull { i ->
                val obj = studentsArr.optJSONObject(i) ?: return@mapNotNull null
                Student(
                    id = obj.optString("id"),
                    studentCode = obj.optString("studentCode"),
                    fullName = obj.optString("fullName"),
                    parentName = obj.optString("parentName").takeIf { it.isNotBlank() },
                    contactNumber = obj.optString("contactNumber").takeIf { it.isNotBlank() },
                    active = obj.optBoolean("active", true)
                )
            }

            val attArr = root.optJSONArray("attendanceRecords") ?: JSONArray()
            val attendance = (0 until attArr.length()).mapNotNull { i ->
                val obj = attArr.optJSONObject(i) ?: return@mapNotNull null
                AttendanceRecord(
                    id = obj.optString("id"),
                    studentId = obj.optString("studentId"),
                    studentName = obj.optString("studentName"),
                    dateKey = obj.optString("dateKey"),
                    status = obj.optString("status")
                )
            }

            val fineArr = root.optJSONArray("fineRecords") ?: JSONArray()
            val fines = (0 until fineArr.length()).mapNotNull { i ->
                val obj = fineArr.optJSONObject(i) ?: return@mapNotNull null
                FineRecord(
                    id = obj.optString("id"),
                    studentId = obj.optString("studentId"),
                    studentName = obj.optString("studentName"),
                    attendanceDate = obj.optString("attendanceDate"),
                    amount = obj.optLong("amount", 5L),
                    paidAmount = obj.optLong("paidAmount", 0L),
                    status = obj.optString("status")
                )
            }

            val payArr = root.optJSONArray("paymentRecords") ?: JSONArray()
            val payments = (0 until payArr.length()).mapNotNull { i ->
                val obj = payArr.optJSONObject(i) ?: return@mapNotNull null
                PaymentRecord(
                    id = obj.optString("id"),
                    studentId = obj.optString("studentId"),
                    studentName = obj.optString("studentName"),
                    amountPaid = obj.optLong("amountPaid", 0L),
                    remainingAfter = obj.optLong("remainingAfter", 0L),
                    paymentDateKey = obj.optString("paymentDateKey"),
                    paymentTimeText = obj.optString("paymentTimeText"),
                    note = obj.optString("note").takeIf { it.isNotBlank() }
                )
            }

            BackupPayload(profile, settings, students, attendance, fines, payments)
        } catch (_: Exception) {
            null
        }
    }

    fun writeUriBytes(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readUriText(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    fun shareBytes(context: Context, fileName: String, mimeType: String, bytes: ByteArray) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, fileName))
    }
}
