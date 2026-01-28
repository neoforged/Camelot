package net.neoforged.camelot.ap;

import org.intellij.lang.annotations.Language;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

@SupportedSourceVersion(SourceVersion.RELEASE_25)
@SupportedAnnotationTypes("net.neoforged.camelot.ap.RegisterCamelotModule")
public class AnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            var providerNames = new ArrayList<String>();
            for (Element element : roundEnv.getElementsAnnotatedWith(RegisterCamelotModule.class)) {
                var moduleClass = (TypeElement) element;
                var name = moduleClass.getQualifiedName() + "_Provider";
                providerNames.add(name);
                var file = processingEnv.getFiler().createSourceFile(name);
                try (var writer = file.openWriter()) {
                    @Language("java")
                    String content = """
                            package %s;
                            
                            import net.neoforged.camelot.ModuleProvider;
                            import net.neoforged.camelot.module.api.CamelotModule;
                            
                            public class %s_Provider implements ModuleProvider {
                                @Override
                                public CamelotModule<?> provide(ModuleProvider.Context context) {
                                    return new %s(context);
                                }
                            }""".formatted(((PackageElement) moduleClass.getEnclosingElement()).getQualifiedName(), moduleClass.getSimpleName(), moduleClass.getSimpleName());
                    writer.append(content);
                }
            }

            if (!providerNames.isEmpty()) {
                try (var writer = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/net.neoforged.camelot.ModuleProvider").openWriter()) {
                    for (String providerName : providerNames) {
                        writer.append(providerName).append('\n');
                    }
                }
            }

            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
