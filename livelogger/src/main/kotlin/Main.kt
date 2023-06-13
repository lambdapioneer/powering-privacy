import javax.swing.UIManager


fun main(args: Array<String>) {
    System.setProperty("sun.java2d.opengl", "True")
    System.setProperty("sun.awt.noerasebackground", "True");

    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())

    val ui = MainUi()
    ui.start()
}
