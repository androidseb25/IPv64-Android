package de.rpicloud.ipv64net.main.views.monitoring

import de.sr.ipv64.node64.model.Monitor64HostChart
import de.sr.ipv64.node64.model.Monitor64HostChartSeries
import de.sr.ipv64.node64.model.Monitor64HostDetail
import de.sr.ipv64.node64.model.Monitor64HostService
import de.sr.ipv64.node64.model.Monitor64Metric
import de.sr.ipv64.node64.model.Monitor64SeriesEntry
import de.sr.ipv64.node64.model.Monitor64SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class Monitor64ChartsTest {
    @Test
    fun addsNumericServiceMetricsWithoutDuplicatingConfiguredSeries() {
        val disk = Monitor64HostService(
            key = "disk",
            name = "Disk",
            metrics = listOf(
                Monitor64Metric(name = "used", value = 12.0),
                Monitor64Metric(name = "temperature", value = 0.0, unit = "°C"),
                Monitor64Metric(name = "temperature", value = 1.0, unit = "°C"),
                Monitor64Metric(name = "ignored", value = Double.NaN),
                Monitor64Metric(name = "missing"),
                Monitor64Metric(value = 10.0)
            )
        )
        val detail = Monitor64HostDetail(
            charts = listOf(Monitor64HostChart(
                id = "disk",
                series = listOf(Monitor64HostChartSeries(svc = "disk", metric = "used"))
            )),
            services = listOf(disk, disk.copy(key = "old", vanished = true))
        )

        val charts = monitor64Charts(detail)

        assertEquals(listOf("DISK", "Disk · temperature"), charts.map { it.id })
        assertEquals(listOf("used", "temperature"), charts.flatMap { it.series }.map { it.metric })
        assertEquals("°C", charts.last().unit)
    }

    @Test
    fun chartPointsRequireTwoDistinctFiniteMeasurementsAndKeepTheirTimeOrder() {
        val entry = Monitor64SeriesEntry(points = listOf(
            Monitor64SeriesPoint(t = 23, avg = 0.0),
            Monitor64SeriesPoint(t = 11, avg = Double.POSITIVE_INFINITY),
            Monitor64SeriesPoint(t = 12, avg = -2.5),
            Monitor64SeriesPoint(t = 12, avg = 7.0),
            Monitor64SeriesPoint(t = 21),
            Monitor64SeriesPoint(t = Long.MAX_VALUE, avg = 9.0)
        ))

        assertEquals(listOf(12_000L to -2.5, 23_000L to 0.0), entry.chartPoints())
        assertEquals(emptyList<Pair<Long, Double>>(), entry.copy(points = entry.points.drop(1)).chartPoints())
    }
}
