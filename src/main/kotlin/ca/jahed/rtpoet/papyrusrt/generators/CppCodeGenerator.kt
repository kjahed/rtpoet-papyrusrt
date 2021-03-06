package ca.jahed.rtpoet.papyrusrt.generators

import ca.jahed.rtpoet.papyrusrt.PapyrusRTWriter
import ca.jahed.rtpoet.rtmodel.RTModel
import net.lingala.zip4j.io.inputstream.ZipInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class CppCodeGenerator(
    private var codegen: String? = null,
    private var plugins: String? = null,
) {
    init {
        if (codegen == null || plugins == null) {
            val codegenDir = File(System.getProperty("java.io.tmpdir"), CppCodeGenerator::class.java.simpleName)
            val codegenFile = File(codegenDir, "codegen/bin/umlrtgen.jar")
            val pluginsDir = File(codegenDir, "codegen/plugins")

            if (!codegenFile.exists() || pluginsDir.exists()) {
                codegenDir.delete()
                codegenDir.mkdirs()

                val zip = this::class.java.classLoader.getResourceAsStream("codegen.zip")
                extractWithZipInputStream(zip, codegenDir)
            }

            codegen = codegenFile.absolutePath
            plugins = pluginsDir.absolutePath
        }
    }

    companion object {
        @JvmStatic
        fun generate(model: RTModel): Boolean {
            return CppCodeGenerator().doGenerate(model, "code")
        }

        @JvmStatic
        fun generate(model: RTModel, outputPath: String): Boolean {
            return CppCodeGenerator().doGenerate(model, outputPath)
        }

        @JvmStatic
        fun generate(model: RTModel, outputPath: String, timeout: Long): Boolean {
            return CppCodeGenerator().doGenerate(model, outputPath, timeout)
        }
    }

    fun doGenerate(model: RTModel, outputPath: String = "code", timeout: Long = 0): Boolean {
        val outputDir = File(outputPath)
        val codeDir = File(outputDir, "${model.name}.cpp")
        codeDir.mkdirs()

        if (!codeDir.exists())
            throw RuntimeException("Cannnot create output directory ${outputDir.absolutePath}")

        val outputModel = File(codeDir, "${model.name}.uml")
        PapyrusRTWriter.write(outputModel.absolutePath, model)

        val result = """
            java -jar ${codegen} -p ${plugins} -o ${codeDir.absolutePath} ${outputModel.absolutePath}
        """.trim().runCommand(outputDir, timeout)

        outputModel.delete()
        return result
    }

    private fun String.runCommand(workingDir: File, timeout: Long): Boolean {
        val pb = ProcessBuilder(*split(" ").toTypedArray())
            .directory(workingDir)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

        return if (timeout == 0.toLong()) pb.waitFor() == 0
        else pb.waitFor(timeout, TimeUnit.SECONDS) && pb.exitValue() == 0
    }

    private fun extractWithZipInputStream(zipFile: InputStream, destination: File) {
        var readLen: Int
        val readBuffer = ByteArray(4096)

        val zipInputStream = ZipInputStream(zipFile)
        var localFileHeader = zipInputStream.nextEntry

        while (localFileHeader != null) {
            val extractedFile = File(destination, localFileHeader.fileName)
            if (localFileHeader.isDirectory) {
                extractedFile.mkdirs()
            } else {
                FileOutputStream(extractedFile).use { outputStream ->
                    while (zipInputStream.read(readBuffer).also { readLen = it } != -1) {
                        outputStream.write(readBuffer, 0, readLen)
                    }
                }
            }

            localFileHeader = zipInputStream.nextEntry
        }
    }
}