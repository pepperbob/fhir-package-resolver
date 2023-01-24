import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class Dependency(name: String, version: String) {
    val name = name.lowercase()
    val version = version.trim()
    fun canonicalName() = "${name.trim()}-${version.trim()}"
    override fun toString(): String = "Depedency {$name, $version}"
}

class Simplifier {
    val simplifierDotNet = "https://packages.simplifier.net"
    val mapper = ObjectMapper()

    init {
        File("./build/packages/").mkdirs()
    }

    fun resolve(vararg dependencies: Dependency) {
        dependencies.map { getDependency(it) }
            .forEach { resolve(*it.toTypedArray()) }
    }

    private fun getDependency(dep: Dependency): List<Dependency> {
        println("Resolving: $dep")
        val archiveFile = Path.of("./build/packages/${dep.canonicalName()}.tar.gz")

        if (!archiveFile.toFile().exists()) {
            val url = "$simplifierDotNet/${dep.name}/${dep.version}"
            val req = Request.Builder().url(url).get().build()
            val resp = OkHttpClient().newCall(req).execute()
            if (!resp.isSuccessful) {
                throw RuntimeException("Dependency does not exists: $dep")
            }
            Files.write(archiveFile, resp.body()!!.bytes())
        }

        TarArchiveInputStream(GzipCompressorInputStream(archiveFile.toFile().inputStream())).use {
            while (it.nextTarEntry != null) {
                if (it.currentEntry.name.contains("package.json")) {
                    val dependencies = mapper.readTree(it.readAllBytes()).get("dependencies")
                    if (dependencies == null) {
                        return emptyList()
                    }
                    return dependencies.fields().asSequence()
                        .map { Dependency(it.key, it.value.asText().trim()) }
                        .toList()
                }
            }
            throw RuntimeException("Invalid Package $dep")
        }
    }
}

fun main() {
    val dep = Dependency("de.gematik.elektronische-versicherungsbescheinigung", "0.8.1-beta")
    Simplifier().resolve(dep)
}