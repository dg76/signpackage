package com.dgunia.signpackage

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

fun main(args: Array<String>) {
    SignPackage(args).run()
}

class SignPackage(val args: Array<String>) {
    val tmpDir = File("tmpdir${System.currentTimeMillis()}")
    val threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    private val OPTION_DIR = "d"
    private val OPTION_SIGN_KEY = "k"
    private val OPTION_ENTITLEMENTS = "e"
    private val OPTION_RUNTIME = "r"
    private val OPTION_TIMESTAMP = "t"
    private val OPTION_EXCLUDE = "x"
    private var excludedFiles: Array<String> = emptyArray()

    fun run() {
        val options = Options()
        options.addOption(Option.builder(OPTION_DIR).longOpt("dir").desc("Directory to scan recursively").hasArg().required().build())
        options.addOption(Option.builder(OPTION_SIGN_KEY).longOpt("signing-key").desc("Key name (e.g. Developer ID Application: John Public (xxxxxxxxxxx))").hasArg().required().build())
        options.addOption(Option.builder(OPTION_ENTITLEMENTS).longOpt("entitlements").desc("Entitlements file").hasArg().build())
        options.addOption(Option.builder(OPTION_RUNTIME).longOpt("runtime").desc("Harden using runtime parameter").build())
        options.addOption(Option.builder(OPTION_TIMESTAMP).longOpt("timestamp").desc("Set secure timestamp using timestamp parameter").build())
        options.addOption(Option.builder(OPTION_EXCLUDE).longOpt("exclude").hasArgs().desc("Set secure timestamp using timestamp parameter").build())

        val parser = DefaultParser()
        try {
            val cmd = parser.parse(options, args)

            // Create a temporary directory
            if (!tmpDir.mkdirs()) {
                System.err.println("Could not create directory ${tmpDir.name}.")
                return
            }
            tmpDir.deleteOnExit()

            // Excluded files

            if (cmd.hasOption(OPTION_EXCLUDE)) {
                excludedFiles = cmd.getOptionValues(OPTION_EXCLUDE)
            }

            // Start scanning and signing the jar files
            scanRecursive(File(cmd.getOptionValue(OPTION_DIR)), cmd)
            threadPool.shutdown()
            threadPool.awaitTermination(1, TimeUnit.DAYS)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /**
     * Searches all files and subdirectories for files that have to be signed.
     */
    private fun scanRecursive(dir: File, cmd: CommandLine) {
        dir.listFiles()?.forEach { file ->
            if (!excludedFiles.contains(file.path)) {
                if (file.isDirectory) {
                    scanRecursive(file, cmd)
                } else if (file.name.endsWith(".jar")) {
                    threadPool.submit {
                        ZipFile(file).entries().asSequence().forEach { zipEntry ->
                            if (zipEntry.name.endsWith(".dylib")) {
                                // Extract, sign and compress the dylib file.
                                println("${file.absolutePath}: ${zipEntry.name}")
                                val dylibFile = File(tmpDir, File(zipEntry.name).name)
                                ZipUtil.unpackEntry(file, zipEntry.name, dylibFile)
                                signFile(dylibFile, cmd)
                                ZipUtil.replaceEntry(file, zipEntry.name, dylibFile)
                            }
                        }

                        // Sign the jar file
                        println(file.absolutePath)
                        signFile(file, cmd)
                    }
                } else if (file.name.endsWith(".dylib") || file.canExecute()) {
                    println(file.absolutePath)
                    threadPool.submit { signFile(file, cmd) }
                }
            }
        }
    }

    /**
     * Sign the file using codesign
     */
    private fun signFile(dylibFile: File, cmd: CommandLine) {
        val command = ArrayList<String>()
        command.add("codesign")
        if (cmd.hasOption(OPTION_TIMESTAMP)) command.add("--timestamp")
        if (cmd.hasOption(OPTION_RUNTIME)) command.addAll(listOf("--options", "runtime"))
        if (cmd.hasOption(OPTION_ENTITLEMENTS)) command.addAll(listOf("--entitlements", File(cmd.getOptionValue(OPTION_ENTITLEMENTS)).absolutePath))
        command.addAll(listOf("--deep", "-vvv", "-f"))
        command.add("--sign")
        command.add(cmd.getOptionValue(OPTION_SIGN_KEY))
        command.add(dylibFile.absolutePath)

        println(command.joinToString(" "))

        val resultCode = ProcessBuilder()
                .directory(dylibFile.parentFile)
                .inheritIO()
                .command(command)
                .start()
                .waitFor()
        if (resultCode != 0) {
            throw Exception("Resultcode $resultCode when executing ${command.joinToString(" ")}")
        }
    }
}
