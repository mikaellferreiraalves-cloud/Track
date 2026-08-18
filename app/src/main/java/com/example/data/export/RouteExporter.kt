package com.example.data.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.database.LocationPoint
import com.example.data.database.TrackingSession
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RouteExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
    private val kmlDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    /**
     * Generates CSV string format:
     * timestamp,latitude,longitude,altitude,accuracy,speed,bearing
     */
    fun generateCsv(points: List<LocationPoint>): String {
        val builder = StringBuilder()
        builder.append("timestamp,latitude,longitude,altitude,accuracy,speed,bearing\n")
        for (point in points) {
            builder.append(point.timestamp).append(",")
                .append(point.latitude).append(",")
                .append(point.longitude).append(",")
                .append(point.altitude ?: "").append(",")
                .append(point.accuracy ?: "").append(",")
                .append(point.speed ?: "").append(",")
                .append(point.bearing ?: "").append("\n")
        }
        return builder.toString()
    }

    /**
     * Generates KML XML format with LineString coordinates.
     */
    fun generateKml(session: TrackingSession, points: List<LocationPoint>): String {
        val sessionName = session.title ?: "Trajeto_${session.id.take(8)}"
        val startDateStr = kmlDateFormat.format(Date(session.startTime))

        val coordBuilder = StringBuilder()
        for (point in points) {
            val alt = point.altitude ?: 0.0
            coordBuilder.append("${point.longitude},${point.latitude},$alt ")
        }

        return """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>$sessionName</name>
    <description>Trajeto gravado em $startDateStr - Distância: ${String.format(Locale.US, "%.2f", session.distanceMeters / 1000.0)} km</description>
    <Style id="routeStyle">
      <LineStyle>
        <color>ff00ffff</color>
        <width>4</width>
      </LineStyle>
    </Style>
    <Placemark>
      <name>$sessionName</name>
      <styleUrl>#routeStyle</styleUrl>
      <LineString>
        <extrude>1</extrude>
        <tessellate>1</tessellate>
        <altitudeMode>clampToGround</altitudeMode>
        <coordinates>
          ${coordBuilder.toString().trim()}
        </coordinates>
      </LineString>
    </Placemark>
  </Document>
</kml>
""".trimIndent()
    }

    /**
     * Exports CSV or KML file and launches the official Android file sharing sheet.
     */
    fun shareRouteFile(
        context: Context,
        session: TrackingSession,
        points: List<LocationPoint>,
        isKml: Boolean
    ): Boolean {
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val dateTag = dateFormat.format(Date(session.startTime))
            val extension = if (isKml) "kml" else "csv"
            val mimeType = if (isKml) "application/vnd.google-earth.kml+xml" else "text/csv"

            val fileName = "trajeto_${session.id.take(6)}_$dateTag.$extension"
            val file = File(exportDir, fileName)

            val content = if (isKml) generateKml(session, points) else generateCsv(points)

            FileWriter(file).use { writer ->
                writer.write(content)
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Exportação de Trajeto - ${session.title ?: dateTag}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartilhar Trajeto ($extension)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
