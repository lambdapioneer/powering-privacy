import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.*

private val FRAME_DIMENSIONS = Dimension(1200, 800)
private val DIALOG_DIMENSIONS = Dimension(800, 600)

class MainUi {
    private val frame = JFrame("LiveLogger")
    private lateinit var graphController: GraphController

    fun start() {
        val controlPanel = createControlPanel()
        val graphView = GraphView()
        val helpText = createHelpText()

        val pane = frame.contentPane
        pane.apply {
            add(controlPanel, BorderLayout.PAGE_START)
            add(graphView, BorderLayout.CENTER)
            add(helpText, BorderLayout.PAGE_END)
        }

        frame.apply {
            isVisible = true
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            size = FRAME_DIMENSIONS
            setLocationRelativeTo(null)
            addWindowStateListener { if (it.id == WindowEvent.WINDOW_CLOSED) graphController.close() }
        }

        graphController = GraphController(graphView, EmptyDataSource())
        graphView.requestFocusInWindow()
    }

    private fun createControlPanel(): JPanel {
        val panel = JPanel()

        val buttonCsvOpen = JButton("Read from CSV file...")
        val buttonCsvTail = JButton("Tail CSV file...")
        val buttonCsvSave = JButton("Save visible to CSV file...")

        panel.add(buttonCsvOpen)
        panel.add(buttonCsvTail)
        panel.add(buttonCsvSave)

        buttonCsvOpen.addActionListener {
            val file = showOpenFileDialog()
            file?.also {
                val csvDataSource = readCsv(it)
                graphController.replaceDataSource(csvDataSource)
                graphController.requestFocus()
            }
        }

        buttonCsvTail.addActionListener {
            val file = showOpenFileDialog()
            file?.also {
                val csvDataSource = FileTrailingDataSource(file)
                csvDataSource.start()
                graphController.replaceDataSource(csvDataSource)
                graphController.requestFocus()
            }
        }

        buttonCsvSave.addActionListener {
            val file = showSaveFileDialog()
            file?.also {
                writeCsv(graphController.getDataCopy(), file)
                graphController.requestFocus()
            }
        }

        return panel
    }

    private fun showOpenFileDialog(): File? {
        val fileChooser = JFileChooser()
        fileChooser.currentDirectory = getCsvDir()
        fileChooser.preferredSize = DIALOG_DIMENSIONS
        val result = fileChooser.showOpenDialog(frame)
        return if (result == JFileChooser.APPROVE_OPTION) fileChooser.selectedFile else null
    }

    private fun showSaveFileDialog(): File? {
        val fileChooser = JFileChooser()
        fileChooser.currentDirectory = getCsvDir()
        fileChooser.preferredSize = DIALOG_DIMENSIONS
        val result = fileChooser.showSaveDialog(frame)
        return if (result == JFileChooser.APPROVE_OPTION) fileChooser.selectedFile else null
    }

    private fun getCsvDir(): File {
        val workingDir = File(System.getProperty("user.dir"))
        return File(workingDir.parent, "measurements")
    }

    private fun createHelpText(): JTextPane {
        val helpText = JTextPane()
        helpText.contentType = "text/html"
        helpText.text = HELP_TEXT_HTML
        helpText.isEditable = false
        return helpText
    }
}

private val HELP_TEXT_HTML = """
    <html>
    <b>Usage</b> <tt>[home]</tt> or <tt>[right-click]</tt> resets to full data view.
    <tt>[end]</tt> enters trailing mode. 
    <tt>[space]</tt> freezes/unfreezes the data.
    <tt>[left-click-drag]</tt> for zooming into selected area.
    <tt>[mouse wheel]</tt> for zooming in and out in trailing and zoomed-in mode.
    <tt>[arrow keys]</tt> for zooming and panning in trailing/zoomed-in mode.
    <tt>[0]</tt> sets 0 as minimum for Y-Axis.
    <tt>[a]</tt> toggle anti-alias.
    <tt>[S]/[s]</tt> increase/decrease sampling.
    </html>
""".trimIndent()
