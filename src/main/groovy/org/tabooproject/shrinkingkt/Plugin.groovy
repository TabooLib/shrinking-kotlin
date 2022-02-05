package org.tabooproject.shrinkingkt

import groovy.transform.Canonical
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.*

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipException

class Plugin implements org.gradle.api.Plugin<Project> {

    @Override
    void apply(Project project) {
        def shrinkingExt = project.extensions.create('shrinking', ShrinkingExt)
        def shrinkingTask = project.tasks.create('shrinkingJar', ShrinkingJar)
        project.afterEvaluate {
            def shadowPresent = project.plugins.hasPlugin('com.github.johnrengelman.shadow')
            if (shadowPresent) {
                shrinkingTask.dependsOn(project.tasks.getByName('shadowJar'))
            }
            project.tasks.jar.finalizedBy(shrinkingTask)
            def jarTask = project.tasks.jar as Jar
            shrinkingTask.configure { ShrinkingJar task ->
                task.ext = shrinkingExt
                task.jar = task.jar ?: jarTask.archivePath
            }
        }
    }
}

@Canonical
class ShrinkingExt {

    String annotation

    List<String> packages
}

class ShrinkingJar extends DefaultTask {

    @InputFile
    File jar

    @Input
    ShrinkingExt ext

    @TaskAction
    def relocate() {
        def temp = File.createTempFile(jar.name, ".jar")
        new JarOutputStream(new FileOutputStream(temp)).withCloseable { outJar ->
            int n
            def buf = new byte[32768]
            new JarFile(jar).withCloseable { jarFile ->
                jarFile.entries().each { jarEntry ->
                    jarFile.getInputStream(jarEntry).withCloseable {
                        def path = jarEntry.name
                        if (path.endsWith(".kotlin_module")) {
                            return true
                        }
                        if (ext.annotation != null && path == ext.annotation.replace('.', '/') + ".class") {
                            return true
                        }
                        try {
                            outJar.putNextEntry(new JarEntry(path))
                        } catch (ZipException zipException) {
                            println(zipException)
                            return true
                        }
                        if (path.endsWith(".class")) {
                            def reader = new ClassReader(it)
                            def writer = new ClassWriter(0)
                            def visitor = new ShrinkingClassVisitor(writer, project, ext, path)
                            reader.accept(visitor, 0)
                            outJar.write(writer.toByteArray())
                        } else {
                            while ((n = it.read(buf)) != -1) {
                                outJar.write(buf, 0, n)
                            }
                        }
                        null
                    }
                }
            }
        }
        Files.copy(temp.toPath(), jar.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

class ShrinkingClassVisitor extends ClassVisitor {

    Project project

    ShrinkingExt ext

    boolean exclude;

    ShrinkingClassVisitor(ClassVisitor classVisitor, Project project, ShrinkingExt ext, String name) {
        super(Opcodes.ASM9, classVisitor);
        this.project = project
        this.ext = ext
        if (ext.packages != null && ext.packages.any { name.startsWith(it.replace('.', '/')) }) {
            exclude = true
        }
    }

    @Override
    AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (ext.annotation != null && descriptor == "L" + ext.annotation.replace('.', '/') + ";") {
            exclude = true
            return null
        } else if (exclude && descriptor == "Lkotlin/Metadata;") {
            return null
        }
        return super.visitAnnotation(descriptor, visible)
    }

    @Override
    MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }

    @Override
    void visitSource(String source, String debug) {
        if (exclude) {
            return
        }
        super.visitSource(source, debug)
    }
}

class ShrinkingMethodVisitor extends MethodVisitor {

    ShrinkingMethodVisitor(MethodVisitor methodVisitor) {
        super(Opcodes.ASM9, methodVisitor)
    }

    @Override
    void visitVarInsn(int opcode, int i) {
        super.visitVarInsn(opcode, i)
    }

    @Override
    void visitLdcInsn(Object value) {
        super.visitLdcInsn(value)
    }

    @Override
    void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
        if (opcode == Opcodes.H_INVOKESTATIC) {

        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
}