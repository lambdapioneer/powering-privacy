import java.awt.*
import java.awt.image.BufferedImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min


class GraphView : Canvas() {

    private var stateDescription = ""
    private val padding = 50f

    var antialiasing = true
        set(value) {
            field = value
            mainPlotDirty = true
            repaint()
        }

    var yStartsAtZero = false
        set(value) {
            field = value
            mainPlotDirty = true
            repaint()
        }

    private var dataSource: DataSource = EmptyDataSource()

    private var crossHighlight: Pair<Int, Int>? = null
    private var currentDragGesture: DragGesture? = null

    private var mainPlotDirty = true
    private var bufferedImage: BufferedImage? = null
    private var bufferedImageOverlay: BufferedImage? = null

    private fun perhapsLateInit() {
        if (bufferedImage != null && !(bufferedImage?.width != width || bufferedImage?.height != height)) return

        createBufferStrategy(2)

        bufferedImage = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .createCompatibleImage(width, height)
        bufferedImageOverlay = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .defaultScreenDevice
            .defaultConfiguration
            .createCompatibleImage(width, height, Transparency.TRANSLUCENT)

        mainPlotDirty = true
    }

    override fun paint(g: Graphics?) {
        render()
    }

    private fun render() {
        val startNs = System.nanoTime()
        perhapsLateInit()

        // This is necessary: we want to be quick in redrawing the last state after the parent got redrawn, otherwise
        // we get an ugly flicker of the parent's background
        bufferStrategy.show()

        val transform = getGraphTransform()

        // update main buffer where necessary
        if (mainPlotDirty) {
            renderMainToBufferedImage(transform)
            mainPlotDirty = false
        }

        // render overlay ad-hoc
        renderOverlayToBufferedImage(transform)

        // then write everything to our buffer strategy and flip the newest state onto the screen
        val bufferGraphics = bufferStrategy.drawGraphics
        try {
            bufferGraphics.drawImage(bufferedImage, 0, 0, null)
            bufferGraphics.drawImage(bufferedImageOverlay, 0, 0, null)

            val deltaNs = System.nanoTime() - startNs
            renderDebugString(deltaNs, bufferGraphics as Graphics2D)
        } finally {
            bufferGraphics.dispose()
        }
        bufferStrategy.show()
    }

    private fun getGraphTransform() = GraphTransform(
        dataSource,
        0f + padding, height.toFloat() - padding, width.toFloat() - padding, 0f + padding,
        yStartsAtZero
    )

    private fun renderDebugString(deltaNs: Long, graphics: Graphics2D) {
        applyGraphicsProperties(graphics)
        graphics.font = Font(font.fontName, font.style, 12)
        graphics.color = Color.GRAY

        graphics.drawString(stateDescription, 10, 18)
        val timingString = "%.1f ms".format(deltaNs / 1_000_000.0)
        graphics.drawString("n=${dataSource.size()} ($timingString)", 10, 36)

        val energyString = "%.2f mJ".format(dataSource.integrate())
        graphics.drawString(energyString, 200, 18)
        val xMinMax = dataSource.getXMinMax()
        val timeString = "%.3fs -> %.3fs dx=%.3fs".format(
            xMinMax.first / 1000f,
            xMinMax.second / 1000f,
            (xMinMax.second - xMinMax.first) / 1000
        )
        graphics.drawString("($timeString)", 200, 36)
    }

    private fun renderOverlayToBufferedImage(transform: GraphTransform) {
        val g2d = bufferedImageOverlay!!.createGraphics()
        try {
            applyGraphicsProperties(g2d)
            g2d.background = Color(0, 0, 0, 0)
            g2d.clearRect(0, 0, width, height)

            val x0 = transform.dataToX(transform.xMin)
            val y0 = transform.dataToY(transform.yMin)
            val x1 = transform.dataToX(transform.xMax)
            val y1 = transform.dataToY(transform.yMax)

            crossHighlight?.also {
                g2d.color = Color.RED
                val xVal = transform.xToData(it.first)
                val yVal = transform.yToData(it.second)

                g2d.drawLine(transform.dataToX(xVal), y0, transform.dataToX(xVal), y1)
                g2d.drawLine(x0, transform.dataToY(yVal), x1, transform.dataToY(yVal))

                g2d.drawString("%.2f".format(xVal / 1000), transform.dataToX(xVal), y0 + 30)
                g2d.drawString("%.0f".format(yVal), x0 - 30, transform.dataToY(yVal))
            }

            currentDragGesture?.also {
                val leftVal = transform.xToData(it.left)
                val rightVal = transform.xToData(it.right)

                g2d.color = Color.RED
                g2d.drawLine(transform.dataToX(leftVal), y0, transform.dataToX(leftVal), y1)
                g2d.drawLine(transform.dataToX(rightVal), y0, transform.dataToX(rightVal), y1)

                g2d.color = Color(255, 0, 0, 64)
                g2d.fillRect(
                    transform.dataToX(leftVal),
                    min(y0, y1),
                    transform.dataToX(rightVal) - transform.dataToX(leftVal),
                    abs(y1 - y0)
                )
            }

        } finally {
            g2d.dispose()
        }
    }

    private fun renderMainToBufferedImage(transform: GraphTransform) {
        val g2d = bufferedImage!!.createGraphics()
        try {
            g2d.background = Color.WHITE
            g2d.clearRect(0, 0, width, height)

            applyGraphicsProperties(g2d)
            renderGrid(g2d, transform)
            renderLines(g2d, transform)
            renderAxes(g2d, transform)
        } finally {
            g2d.dispose()
        }
    }

    private fun renderGrid(g2d: Graphics2D, transform: GraphTransform) {
        g2d.color = Color(200, 200, 200)

        val x0 = transform.dataToX(transform.xMin)
        val y0 = transform.dataToY(transform.yMin)
        val x1 = transform.dataToX(transform.xMax)
        val y1 = transform.dataToY(transform.yMax)

        val grid = GridLines(transform)
        for (xMajor in grid.getXMajors()) g2d.drawLine(transform.dataToX(xMajor), y0, transform.dataToX(xMajor), y1)
        for (yMajor in grid.getYMajors()) g2d.drawLine(x0, transform.dataToY(yMajor), x1, transform.dataToY(yMajor))
    }

    private fun renderAxes(g2d: Graphics2D, transform: GraphTransform) {
        g2d.color = Color(0, 0, 0)

        val x0 = transform.dataToX(transform.xMin)
        val y0 = transform.dataToY(transform.yMin)
        val x1 = transform.dataToX(transform.xMax)
        val y1 = transform.dataToY(transform.yMax)

        g2d.drawRect(x0, min(y0, y1), x1 - x0, abs(y1 - y0))

        val grid = GridLines(transform)
        for (xMajor in grid.getXMajors()) {
            g2d.drawString("%.2f".format(xMajor / 1000), transform.dataToX(xMajor), y0 + 15)
        }
        for (yMajor in grid.getYMajors()) {
            g2d.drawString("%.0f".format(yMajor), x0 - 35, transform.dataToY(yMajor))
        }

    }

    /**
     * A highly optimized line renderer that draws a line between the min and max of each pixel. This is much faster
     * than drawing a line for each data point. It also draws a line between the last data point and the current
     * data point, so that the line is continuous. As a result we will not draw more than 2 lines per pixel.
     */
    private fun renderLines(g2d: Graphics2D, transform: GraphTransform) {
        g2d.color = Color(50, 50, 50)
        val data = dataSource.getData()

        var currX = Int.MIN_VALUE // in pixels
        var currMinY = Float.POSITIVE_INFINITY // in data units
        var currMaxY = Float.NEGATIVE_INFINITY // in data units
        var currLastY = 0f // in data units

        for (i in 0 until data.size - 1) {
            val d0 = data[i]
            val x0 = transform.dataToX(d0.x)

            if (x0 > currX) {
                val y0 = transform.dataToY(d0.y)
                if (currX > Int.MIN_VALUE) {
                    // if there are more than 1 data points in the same pixel, draw a line between the min and max
                    if (currMinY != Float.POSITIVE_INFINITY && currMinY != currMaxY) {
                        g2d.drawLine(
                            currX, transform.dataToY(currMinY),
                            currX, transform.dataToY(currMaxY),
                        )
                    }

                    // draw a line between the last data point for the previous x and the current data point
                    g2d.drawLine(currX, transform.dataToY(currLastY), x0, y0)
                }
                currX = x0
                currMinY = Float.POSITIVE_INFINITY
                currMaxY = Float.NEGATIVE_INFINITY
            }

            // update the min and max for the next round
            currMinY = min(currMinY, d0.y)
            currMaxY = max(currMaxY, d0.y)
            currLastY = d0.y
        }
    }

    fun setDataSource(dataSource: DataSource) {
        this.dataSource = dataSource
        mainPlotDirty = true
        repaint()
    }

    fun onDataUpdate() {
        mainPlotDirty = true
        repaint()
    }

    fun setCrossHighlight(x: Int, y: Int) {
        crossHighlight = Pair(x, y)
        repaint()
    }

    fun setDragGesture(dragGesture: DragGesture?) {
        if (dragGesture != null) crossHighlight = null
        currentDragGesture = dragGesture
        repaint()
    }

    fun translateCoordToData(x: Int): Float {
        return getGraphTransform().xToData(x)
    }

    fun setStateDescription(string: String) {
        stateDescription = string
    }

    private fun applyGraphicsProperties(graphics: Graphics2D) {
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
        graphics.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            if (antialiasing) RenderingHints.VALUE_ANTIALIAS_ON else RenderingHints.VALUE_ANTIALIAS_OFF
        )
        graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            if (antialiasing) RenderingHints.VALUE_TEXT_ANTIALIAS_ON else RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
        )
    }

}

@Suppress("PrivatePropertyName", "PropertyName")
class GridLines(private val transform: GraphTransform) {
    private val xDeltaCandidatesMs =
        arrayOf(500_000, 200_000, 100_000, 50_000, 20_000, 10_000, 5_000, 2_000, 1_000, 500, 200, 100, 50, 20, 10)
    private val xTargetNum = 5
    private val yDeltaCandidatesMs =
        arrayOf(100_000, 50_000, 20_000, 10_000, 5_000, 2_000, 1_000, 500, 200, 100, 50, 20, 10)
    private val yTargetNum = 5

    fun getXMajors(): FloatArray {
        var xDeltaMs = 0
        for (candidate in xDeltaCandidatesMs) {
            xDeltaMs = candidate
            val resultingLines = (transform.xMax - transform.xMin) / xDeltaMs
            if (resultingLines > xTargetNum) break
        }

        val result = arrayListOf<Float>()
        var curr = ceil(transform.xMin / xDeltaMs) * xDeltaMs
        while (curr <= transform.xMax) {
            result.add(curr)
            curr += xDeltaMs
        }
        return result.toFloatArray()
    }

    fun getYMajors(): FloatArray {
        var yDeltaMs = 0
        for (candidate in yDeltaCandidatesMs) {
            yDeltaMs = candidate
            val resultingLines = (transform.yMax - transform.yMin) / yDeltaMs
            if (resultingLines > yTargetNum) break
        }

        val result = arrayListOf<Float>()
        var curr = ceil(transform.yMin / yDeltaMs) * yDeltaMs
        while (curr <= transform.yMax) {
            result.add(curr)
            curr += yDeltaMs
        }
        return result.toFloatArray()
    }
}

class GraphTransform(
    data: DataSource,
    paint_x0: Float, paint_y0: Float,
    paint_x1: Float, paint_y1: Float,
    yStartsAtZero: Boolean = false
) {
    val xMin = data.getXMinMax().first
    val xMax = data.getXMinMax().second
    val yMin = if (yStartsAtZero) 0f else data.getYMinMax().first - 10
    val yMax = data.getYMinMax().second + 10

    private val xM = (paint_x1 - paint_x0) / (xMax - xMin)
    private val yM = (paint_y1 - paint_y0) / (yMax - yMin)
    private val xB = paint_x1 - xM * xMax
    private val yB = paint_y1 - yM * yMax

    fun dataToX(x: Float) = (xM * x + xB).toInt()
    fun dataToY(y: Float) = (yM * y + yB).toInt()

    fun xToData(x: Int) = (x - xB) / xM
    fun yToData(y: Int) = (y - yB) / yM
}
