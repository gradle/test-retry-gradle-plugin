package org.gradle.testretry.internal.filter

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testretry.internal.testsreader.TestsReader
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import javax.annotation.Nullable

class AnnotationInspectorImplTest extends Specification {

    @Rule
    TemporaryFolder dir = new TemporaryFolder()

    AnnotationInspector inspector

    def setup() {
        def settingsFile = dir.newFile('settings.gradle')
        settingsFile << "rootProject.name = 'hello-world'"
        def buildFile = dir.newFile('build.gradle')
        buildFile << "plugins { id 'java' }"
    }

    def "finds annotations"() {
        given:
        nonInheritedAnnotation("AN1")
        nonInheritedAnnotation("AN2")
        nonInheritedAnnotation("AN3")
        inheritedAnnotation("AI1")
        inheritedAnnotation("AI2")
        inheritedAnnotation("AI3")

        classWithAnnotations("NoAnnotationBase", null)
        classWithAnnotations("IncludeAnnotationBase", null, "AN1", "AI1")
        classWithAnnotations("IncludeAnnotationChild", "IncludeAnnotationBase", "AN2", "AI2")
        classWithAnnotations("IncludeAnnotationChildChild", "IncludeAnnotationChild")
        classWithAnnotations("IncludeAnnotationChildChildChild", "IncludeAnnotationChildChild")

        expect:
        annotationsOf("NoAnnotationBase").empty
        annotationsOf("NotExist").empty
        annotationsOf("IncludeAnnotationBase") == ["AI1", "AN1"]
        annotationsOf("IncludeAnnotationChild") == ["AI1", "AI2", "AN2"]
        annotationsOf("IncludeAnnotationChildChild") == ["AI1", "AI2"]
        annotationsOf("IncludeAnnotationChildChildChild") == ["AI1", "AI2"]
    }

    List<String> annotationsOf(String className) {
        if (inspector == null) {
            inspector = inspector()
        }
        inspector.getClassAnnotations(className).toList().sort()
    }

    File file(String path) {
        def file = new File(dir.root, path)
        assert file.parentFile.mkdirs() || file.parentFile.directory
        assert file.createNewFile() || file.file
        file
    }

    AnnotationInspector inspector() {
        GradleRunner.create().withProjectDir(dir.root).withArguments("compileJava").build()
        def reader = new TestsReader([new File(dir.root, "build/classes/java/main")].toSet(), [])
        new AnnotationInspectorImpl(reader)
    }

    private void nonInheritedAnnotation(String name) {
        file("src/main/java/${name}.java") << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            public @interface $name { }
        """
    }

    private void inheritedAnnotation(String name) {
        file("src/main/java/${name}.java") << """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;
            
            @Retention(RetentionPolicy.RUNTIME)
            @Target({ ElementType.TYPE })
            @java.lang.annotation.Inherited
            public @interface $name { }
        """
    }

    private void classWithAnnotations(String name, @Nullable String superClass, String... annotations) {
        file("src/main/java/${name}.java") << """
            ${annotations.collect { "@$it" }.join("\n")}
            class $name ${superClass ? " extends $superClass" : ""} {}
        """

    }

}
