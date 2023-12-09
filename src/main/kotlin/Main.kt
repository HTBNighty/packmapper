import imgui.ImGui
import imgui.app.Application
import imgui.app.Configuration
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImString
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWErrorCallback
import tv.wunderbox.nfd.FileDialog
import tv.wunderbox.nfd.FileDialogResult
import tv.wunderbox.nfd.nfd.NfdFileDialog
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

val SETTINGS = mapOf(
    0 to ("Input path" to ImString()),
    1 to ("Output path" to ImString()),
    2 to ("Diamonds instead of netherites" to ImBoolean()),
    3 to ("Keep diamonds" to ImBoolean()),
    4 to ("Edit models" to ImBoolean())
)

val MAPPINGS = mapOf(
    "textures/blocks" to "textures/block",
    "textures/entity/endercrystal" to "textures/entity/end_crystal",
    "textures/items" to "textures/item",
    "apple_golden.png" to "golden_apple.png",
    "totem.png" to "totem_of_undying.png"
)

val DIAMOND_TO_NETHERITE = "diamond_" to "netherite_"

fun main(
    args : Array<String>
) {
    val timestamp = System.currentTimeMillis()
    var counter = 0

    fun exit(
        message : String
    ) {
        println("$message\nUsage: <path to 1.12.2 zipped pack> <path to 1.20.1 zipped pack> <true/false: using diamond textures as netherite textures>")
        exitProcess(0)
    }

    fun mapName(
        name : String,
        d2n : Boolean
    ) : String {
        var mappedName = name

        for((old, new) in MAPPINGS) {
            mappedName = mappedName.replace(old, new)
        }

        if(d2n) {
            mappedName = mappedName.replace(DIAMOND_TO_NETHERITE.first, DIAMOND_TO_NETHERITE.second)
        }

        if(name != mappedName) {
            counter++
        }

        return mappedName
    }

    fun mapPack(
        config : IConfigProvider
    ) {
        if(config.inputPath.isEmpty() || config.outputPath.isEmpty()) {
            return
        }

        val inputFile = File(config.inputPath)
        val outputFile = File(config.outputPath)

        val inputZipFile = ZipFile(inputFile)

        if(outputFile.exists()) {
            println("Output file will be overwritten")

            outputFile.delete()
        }

        outputFile.createNewFile()

        val zos = ZipOutputStream(FileOutputStream(outputFile))

        for(inputEntry in inputZipFile.entries()) {
            val name = inputEntry.name
            val mappedName = mapName(name, config.d2n)
            val `is` = inputZipFile.getInputStream(inputEntry)
            val bytes = `is`.readBytes()
            val outputEntry = ZipEntry(mappedName)

            zos.putNextEntry(outputEntry)
            zos.write(bytes)
            zos.closeEntry()
        }

        zos.close()

        println("Mapped $counter zip entries! Everything took ${System.currentTimeMillis() - timestamp} ms!")
    }

    if(args.isEmpty()) {
        val config = GUIConfigProvider()

        val gui = object : Application() {
            override fun configure(
                config : Configuration
            ) {
                colorBg.set(0f, 0f, 0f, 0f)

                config.title = "packmapper"
                config.width = 500
                config.height = 300

                GLFWErrorCallback.createPrint(System.err).set()

                if(!GLFW.glfwInit()) {
                    throw IllegalStateException("Unable to initialize GLFW")
                }

                GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, 1)
                GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, 0)
            }

            override fun process() {
                ImGui.styleColorsDark()

                val style = ImGui.getStyle()

                style.frameBorderSize = 1f

                val window = style.getColor(ImGuiCol.WindowBg)

                style.setColor(ImGuiCol.WindowBg, window.x, window.y, window.z, 0.6f)

                val frame = style.getColor(ImGuiCol.FrameBg)

                style.setColor(ImGuiCol.FrameBg, frame.x, frame.y, frame.z, frame.w / 2f)

                ImGui.pushStyleColor(ImGuiCol.ChildBg, frame.x, frame.y, frame.z, frame.w / 2f)

                ImGui.begin("packmapper", ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoDecoration)

                val w = intArrayOf(1)
                val h = intArrayOf(1)

                GLFW.glfwGetWindowSize(handle, w, h)

                ImGui.setWindowPos(0f, 0f)
                ImGui.setWindowSize(w[0].toFloat(), h[0].toFloat())

                ImGui.text("Converts pack from 1.12.2 to 1.20.1")

                ImGui.beginChild("Configuration", 0f, 0f, true)

                for(setting in SETTINGS.values) {
                    val title = setting.first

                    when(
                        val value = setting.second
                    ) {
                        is ImString -> {
                            ImGui.inputText(title, value)

                            if(ImGui.button("Select")) {
                                val dialog = NfdFileDialog()
                                val result = dialog.pickFile(listOf(FileDialog.Filter("Archives", listOf("zip,rar"))))

                                if(result is FileDialogResult.Success) {
                                    val file = result.value
                                    val path = file.path

                                    value.set(path)
                                }
                            }
                        }
                        is ImBoolean -> ImGui.checkbox(title, value)
                    }
                }

                if(ImGui.button("Convert!")) {
                    mapPack(config)
                }

                ImGui.endChild()

                ImGui.end()
                ImGui.popStyleColor()
            }
        }

        Application.launch(gui)
    } else if(args.size == 3) {
        val config = CLIConfigProvider(args)

        mapPack(config)
    } else {
        exit("Not enough arguments!")
    }
}

interface IConfigProvider {
    val inputPath : String
    val outputPath : String
    val d2n : Boolean
    val keepDiamonds : Boolean
    val editModels : Boolean
}

class GUIConfigProvider : IConfigProvider {
    override val inputPath get() = (SETTINGS[0]!!.second as ImString).get()
    override val outputPath get() = (SETTINGS[1]!!.second as ImString).get()
    override val d2n get() = (SETTINGS[2]!!.second as ImBoolean).get()
    override val keepDiamonds get() = (SETTINGS[3]!!.second as ImBoolean).get()
    override val editModels get() = (SETTINGS[4]!!.second as ImBoolean).get()
}

class CLIConfigProvider(
    args : Array<String>
) : IConfigProvider {
    override val inputPath = args[0]
    override val outputPath = args[1]
    override val d2n = args[2].toBoolean()
    override val keepDiamonds = args[3].toBoolean()
    override val editModels = args[4].toBoolean()
}