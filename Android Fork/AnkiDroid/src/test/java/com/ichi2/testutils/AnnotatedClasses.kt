// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

/** Finds project classes with the specified annotation by reading bytecode, without loading the classes. */
internal fun ClassLoader?.annotatedClassNames(
    annotation: Class<out Annotation>,
    projectRoot: File,
): Set<String> {
    val descriptor = "L${annotation.name.replace('.', '/')};"
    return projectClasspath(projectRoot).flatMap { it.annotatedClassNames(descriptor) }.toSet()
}

private fun ClassLoader?.projectClasspath(root: File): Sequence<File> =
    generateSequence(this) { it.parent }
        .filterIsInstance<URLClassLoader>()
        .flatMap { it.urLs.asSequence() }
        .filter { it.protocol == "file" }
        .map { File(it.toURI()) }
        .plus(
            System
                .getProperty("java.class.path")
                .orEmpty()
                .split(File.pathSeparator)
                .map(::File),
        ).filter { it.toPath().startsWith(root.toPath()) }
        .distinct()

private fun File.annotatedClassNames(descriptor: String): Set<String> =
    when {
        isDirectory ->
            walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .mapNotNull { annotatedClassName(it.readBytes(), descriptor) }
                .toSet()
        extension == "jar" ->
            JarFile(this).use { jar ->
                jar
                    .entries()
                    .asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") }
                    .mapNotNull { entry -> jar.getInputStream(entry).use { annotatedClassName(it.readBytes(), descriptor) } }
                    .toSet()
            }
        else -> emptySet()
    }

private fun annotatedClassName(
    bytes: ByteArray,
    annotationDescriptor: String,
): String? {
    val reader = ClassReader(bytes)
    var annotated = false
    reader.accept(
        object : ClassVisitor(Opcodes.ASM9) {
            override fun visitAnnotation(
                descriptor: String,
                visible: Boolean,
            ): AnnotationVisitor? {
                if (descriptor == annotationDescriptor) annotated = true
                return null
            }
        },
        ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
    )
    return if (annotated) reader.className.replace('/', '.') else null
}
