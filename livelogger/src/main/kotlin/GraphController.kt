import java.awt.event.*
import java.io.Closeable
import kotlin.math.abs
import kotlin.math.max

data class DragGesture(var left: Int, var right: Int)

class GraphController(private val graphView: GraphView, dataSource: ArrayListBackedDataSource) :
    DataSourceListener,
    MouseMotionListener,
    MouseWheelListener,
    MouseListener,
    KeyListener,
    Closeable {

    enum class Mode {
        HOME,
        ZOOM,
        TRAILING
    }

    private var mode = Mode.HOME
    private var zoomLeft = 0f
    private var zoomRight = 10_000f
    private var trailingWidth = 5_000f

    private var sampling = 1

    private var isPaused = false

    // data sources are wrapped in this order
    private var internalDataSource: ArrayListBackedDataSource
    private lateinit var pauseableDataSource: ArrayListBackedDataSource
    private lateinit var sampledDataSource: ArrayListBackedDataSource
    private lateinit var modeDataSource: DataSource

    private var currentDragGesture: DragGesture? = null

    init {
        graphView.addMouseListener(this)
        graphView.addMouseMotionListener(this)
        graphView.addMouseWheelListener(this)
        graphView.addKeyListener(this)

        internalDataSource = dataSource
        setPausingDataSource()
        setSamplingDataSource()
        setModeDataSource()
    }

    fun replaceDataSource(dataSource: ArrayListBackedDataSource) {
        internalDataSource.close()
        internalDataSource.clearListener()

        isPaused = false
        mode = Mode.HOME
        sampling = 1

        internalDataSource = dataSource
        setPausingDataSource()
        setSamplingDataSource()
        setModeDataSource()
    }

    fun getDataCopy(): ArrayList<DataPoint> = ArrayList(modeDataSource.getData())

    fun requestFocus() = graphView.requestFocusInWindow()

    private fun enterMode(newMode: Mode) {
        mode = newMode
        setModeDataSource()
    }

    private fun setModeDataSource() {
        modeDataSource = when (mode) {
            Mode.HOME -> sampledDataSource
            Mode.TRAILING -> TrailingDataSource(sampledDataSource, trailingWidth)
            Mode.ZOOM -> ZoomedDataSource(sampledDataSource, zoomLeft, zoomRight)
        }

        // mode data source is always the final one being set
        updateStateDescription()
        graphView.setDataSource(modeDataSource)
        modeDataSource.setListener(this)
    }

    private fun pause() {
        if (isPaused) return
        isPaused = true
        setPausingDataSource()
        setSamplingDataSource()
        setModeDataSource()
    }

    private fun unpause() {
        if (!isPaused) return
        isPaused = false
        setPausingDataSource()
        setSamplingDataSource()
        setModeDataSource()
    }

    private fun setPausingDataSource() {
        pauseableDataSource = if (isPaused) FrozenDataSource(internalDataSource) else internalDataSource
    }

    private fun zoomOut() {
        when (mode) {
            Mode.ZOOM -> {
                val w = zoomRight - zoomLeft
                val dw = (w * 0.5f) / 2f
                zoomLeft = max(0f, zoomLeft - dw)
                zoomRight += dw
            }
            Mode.TRAILING -> trailingWidth *= 1.5f
            else -> {
            }
        }
        enterMode(mode)
    }

    private fun zoomIn() {
        when (mode) {
            Mode.ZOOM -> {
                val w = zoomRight - zoomLeft
                val dw = ((1f / 1.5f - 1f) * w) / 2f
                zoomLeft -= dw
                zoomRight += dw
            }
            Mode.TRAILING -> if (trailingWidth > 1_000f) trailingWidth *= 1f / 1.5f
            else -> {
            }
        }
        enterMode(mode)
    }

    private fun increaseSample() {
        sampling *= 2
        setSamplingDataSource()
        setModeDataSource()
    }

    private fun decreaseSample() {
        if (sampling == 1) return
        sampling /= 2
        setSamplingDataSource()
        setModeDataSource()
    }

    private fun setSamplingDataSource() {
        sampledDataSource = if (sampling == 1) pauseableDataSource else SampledDataSource(pauseableDataSource, sampling)
    }

    override fun onDataSourceUpdate(dataSource: DataSource, dataPoint: DataPoint) {
        if (dataSource != modeDataSource) return
        graphView.onDataUpdate()
    }

    override fun mousePressed(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return
        currentDragGesture = DragGesture(e.x, e.x)
        graphView.setDragGesture(currentDragGesture)
    }

    override fun mouseDragged(e: MouseEvent) {
        currentDragGesture?.also {
            if (e.x < it.left) {
                it.left = e.x
            } else if (e.x > it.right) {
                it.right = e.x
            }
        }
        graphView.setDragGesture(currentDragGesture)
    }

    override fun mouseReleased(e: MouseEvent) {
        if (e.button != MouseEvent.BUTTON1) return

        currentDragGesture?.also {
            if (abs(it.right - it.left) > 10) {
                zoomLeft = graphView.translateCoordToData(it.left)
                zoomRight = graphView.translateCoordToData(it.right)
                enterMode(Mode.ZOOM)
            }
        }

        currentDragGesture = null
        graphView.setDragGesture(null)
        graphView.setCrossHighlight(e.x, e.y)
    }

    override fun mouseMoved(e: MouseEvent) {
        graphView.setCrossHighlight(x = e.x, y = e.y)
    }

    override fun mouseClicked(e: MouseEvent) {
        when (e.button) {
            MouseEvent.BUTTON3 -> enterMode(Mode.HOME)
        }
    }

    override fun keyPressed(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_END -> {
                enterMode(Mode.TRAILING)
            }
            KeyEvent.VK_HOME -> {
                unpause()
                enterMode(Mode.HOME)
            }
            KeyEvent.VK_SPACE -> {
                if (isPaused) unpause() else pause()
            }
            KeyEvent.VK_A -> {
                graphView.antialiasing = !graphView.antialiasing
            }
            KeyEvent.VK_RIGHT -> {
                if (mode == Mode.ZOOM) {
                    val w = zoomRight - zoomLeft
                    zoomLeft += w / 10
                    zoomRight += w / 10
                    enterMode(mode)
                }
            }
            KeyEvent.VK_LEFT -> {
                if (mode == Mode.ZOOM) {
                    val w = zoomRight - zoomLeft
                    zoomLeft -= w / 10
                    zoomRight -= w / 10
                    enterMode(mode)
                }
            }
            KeyEvent.VK_UP -> zoomIn()
            KeyEvent.VK_DOWN -> zoomOut()
            KeyEvent.VK_0 -> graphView.yStartsAtZero = !graphView.yStartsAtZero
            KeyEvent.VK_NUMPAD0 -> graphView.yStartsAtZero = !graphView.yStartsAtZero
            KeyEvent.VK_S -> {
                if (e.modifiers and KeyEvent.SHIFT_MASK == KeyEvent.SHIFT_MASK) {
                    increaseSample()
                } else {
                    decreaseSample()
                }
            }
        }
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (e.wheelRotation < 0) zoomIn() else zoomOut()
    }

    private fun updateStateDescription() {
        val pauseString = if (isPaused) "[paused]" else ""
        val modeString = when (mode) {
            Mode.HOME -> "HOME"
            Mode.ZOOM -> "ZOOM "
            Mode.TRAILING -> "TRAILING"
        }
        val samplingString = if (sampling > 1) "sampling=$sampling" else ""

        val stateDescription = "$modeString $pauseString $samplingString"
        println(stateDescription)
        graphView.setStateDescription(stateDescription)
    }

    override fun close() {
        internalDataSource.close()
    }

    override fun keyReleased(e: KeyEvent?) {
        // intentionally empty
    }

    override fun keyTyped(e: KeyEvent) {
        // intentionally empty
    }

    override fun mouseEntered(e: MouseEvent?) {
        // intentionally empty
    }

    override fun mouseExited(e: MouseEvent?) {
        // intentionally empty
    }
}
