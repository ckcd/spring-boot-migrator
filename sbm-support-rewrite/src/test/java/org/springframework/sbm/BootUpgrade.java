package org.springframework.sbm;

import lombok.extern.slf4j.Slf4j;
import org.openrewrite.*;
import org.openrewrite.config.CompositeRecipe;
import org.openrewrite.internal.InMemoryLargeSourceSet;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.kotlin.KotlinParser;
import org.openrewrite.maven.ResourceParser;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.xml.tree.Xml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.Resource;
import org.springframework.sbm.parsers.ProjectScanner;
import org.springframework.sbm.parsers.RewriteMavenProjectParser;
import org.springframework.sbm.parsers.RewriteProjectParsingResult;
import org.springframework.sbm.parsers.Slf4jToMavenLoggerAdapter;
import org.springframework.sbm.recipes.RewriteRecipeDiscovery;

import java.nio.file.Path;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * @author Fabian Krüger
 */
@SpringBootApplication
@Slf4j
public class BootUpgrade implements CommandLineRunner {
  public static void main(String[] args) {
    SpringApplication.run(BootUpgrade.class, args);
  }

  @Autowired
  ProjectScanner scanner;
  @Autowired
  RewriteMavenProjectParser parser;
  @Autowired
  RewriteRecipeDiscovery discovery;

  @Override
  public void run(String... args) throws Exception {

    // String path  = "/Users/kuchang/java/dryrun/demo-spring-song-app";
    // String path = "/Users/kuchang/java/mktassetstore-3";
    String path = "/Users/kuchang/java/dryrun/seorulesvc-5";
    Path baseDir = Path.of(path ).toAbsolutePath().normalize();
    System.out.println(baseDir);
    if(!baseDir.toFile().exists() || !baseDir.toFile().isDirectory()) {
      throw new IllegalArgumentException("Given path '%s' does not exist or is not a directory.".formatted(path));
    }

//    JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder = JavaParser.fromJavaVersion()
//        .logCompilationWarningsAndErrors(false).classpathFromResources(new InMemoryExecutionContext(),
//            "spring-batch-core-4.3.+", "spring-batch-infrastructure-4.3.+", "spring-beans-4.3.30.RELEASE");

    JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(false).classpathFromResources(new InMemoryExecutionContext(),
            "spring-beans", "spring-context", "spring-boot", "spring-security", "spring-security-config-5.+", "spring-web", "spring-core");

    // todo, add styles from autoDetect
    KotlinParser.Builder kotlinParserBuilder = KotlinParser.builder();
    ResourceParser rp = new ResourceParser(baseDir, new Slf4jToMavenLoggerAdapter(log), Set.of(), getPlainTextMasks(), -1, Set.of(),
        javaParserBuilder.clone());

    // List<Resource> resources = scanner.scan(baseDir, Set.of("**/.idea/**", "**/.DS_Store", "**/.git/**"));
    ExecutionContext ctx = new InMemoryExecutionContext(t -> {throw new RuntimeException(t);});
    // RewriteProjectParsingResult parsingResult = parser.parse(baseDir);

     Set<Path> alreadyParsed = new HashSet<>();
     Stream<SourceFile> sourceFileStream = rp.parse(baseDir, alreadyParsed);
     List<SourceFile> sourceFiles = sourcesWithAutoDetectedStyles(sourceFileStream);
     for (SourceFile sss : sourceFiles) {
       if (sss.getSourcePath().endsWith("ElasticsearchAssetWriter.java")) {
         System.out.println("sss = " + sss);
       }
       if (sss.getSourcePath().endsWith("SecurityConfig.java")) {
         System.out.println("sss = " + sss);
       }
     }
    // String recipeName = "org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_1";
    // String recipeName = "org.openrewrite.java.spring.boot3.SpringBatch4To5Migration";
    String recipeName = "org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter";

     Set<String> recipeNames = Set.of(
         "org.openrewrite.java.spring.security5.WebSecurityConfigurerAdapter",
         "org.openrewrite.java.spring.security5.UseNewRequestMatchers",
         "org.openrewrite.java.spring.boot2.HttpSecurityLambdaDsl"
         );
    List<Recipe> recipes = discovery.discoverRecipes();
    List<Recipe> selectedRecipes = recipes.stream()
        .filter(r -> recipeNames.contains(r.getName()))
        .collect(Collectors.toList());
    CompositeRecipe compositeRecipe = new CompositeRecipe(selectedRecipes);
    LargeSourceSet lss = new InMemoryLargeSourceSet(sourceFiles);
    RecipeRun recipeRun = compositeRecipe.run(lss, ctx);
    recipeRun.getChangeset().getAllResults().stream()
        .map(Result::diff)
        .forEach(System.out::println);

//    recipes.stream()
//        .filter(r -> recipeName.equals(r.getName()))
//        .forEach(r -> {
//          System.out.println("Applying recipe '%s'".formatted(r.getName()));
//          LargeSourceSet lss = new InMemoryLargeSourceSet(sourceFiles);
//          RecipeRun recipeRun = r.run(lss, ctx);
//          recipeRun.getChangeset().getAllResults().stream()
//              .map(Result::diff)
//              .forEach(System.out::println);
//        });
  }

  protected Set<String> getPlainTextMasks() {
    //If not defined, use a default set of masks
    return new HashSet<>(Arrays.asList(
        "**/*.adoc",
        "**/*.bash",
        "**/*.bat",
        "**/CODEOWNERS",
        "**/*.css",
        "**/*.config",
        "**/Dockerfile*",
        "**/.gitattributes",
        "**/.gitignore",
        "**/*.htm*",
        "**/gradlew",
        "**/.java-version",
        "**/*.jsp",
        "**/*.ksh",
        "**/lombok.config",
        "**/*.md",
        "**/*.mf",
        "**/META-INF/services/**",
        "**/META-INF/spring/**",
        "**/META-INF/spring.factories",
        "**/mvnw",
        "**/mvnw.cmd",
        "**/*.qute.java",
        "**/.sdkmanrc",
        "**/*.sh",
        "**/*.sql",
        "**/*.svg",
        "**/*.txt",
        "**/*.py"
    ));
  }

  List<SourceFile> sourcesWithAutoDetectedStyles(Stream<SourceFile> sourceFiles) {
    org.openrewrite.java.style.Autodetect.Detector javaDetector = org.openrewrite.java.style.Autodetect.detector();
    org.openrewrite.xml.style.Autodetect.Detector xmlDetector = org.openrewrite.xml.style.Autodetect.detector();
    List<SourceFile> sourceFileList = sourceFiles
        .peek(javaDetector::sample)
        .peek(xmlDetector::sample)
        .collect(toList());

    Map<Class<? extends Tree>, NamedStyles> stylesByType = new HashMap<>();
    stylesByType.put(JavaSourceFile.class, javaDetector.build());
    stylesByType.put(Xml.Document.class, xmlDetector.build());

    return ListUtils.map(sourceFileList, applyAutodetectedStyle(stylesByType));
  }

  // copied from OpenRewrite for now, TODO: remove and reuse
  UnaryOperator<SourceFile> applyAutodetectedStyle(Map<Class<? extends Tree>, NamedStyles> stylesByType) {
    return before -> {
      for (Map.Entry<Class<? extends Tree>, NamedStyles> styleTypeEntry : stylesByType.entrySet()) {
        if (styleTypeEntry.getKey().isAssignableFrom(before.getClass())) {
          before = before.withMarkers(before.getMarkers().add(styleTypeEntry.getValue()));
        }
      }
      return before;
    };
  }
}
