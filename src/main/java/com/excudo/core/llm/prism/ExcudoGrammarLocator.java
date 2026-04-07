package com.excudo.core.llm.prism;

import io.noties.prism4j.GrammarLocator;
import io.noties.prism4j.Prism4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manual GrammarLocator for Prism4j. Maps language names and aliases
 * to their grammar factories. No annotation processing needed.
 */
public class ExcudoGrammarLocator implements GrammarLocator {

    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        ALIASES.put("py", "python");
        ALIASES.put("js", "javascript");
        ALIASES.put("ts", "javascript");
        ALIASES.put("typescript", "javascript");
        ALIASES.put("c++", "cpp");
        ALIASES.put("cs", "csharp");
        ALIASES.put("xml", "markup");
        ALIASES.put("html", "markup");
        ALIASES.put("svg", "markup");
        ALIASES.put("kt", "kotlin");
        ALIASES.put("yml", "yaml");
        ALIASES.put("tex", "latex");
        ALIASES.put("md", "markdown");
    }

    @Override
    public Prism4j.Grammar grammar(Prism4j prism4j, String language) {
        String resolved = ALIASES.getOrDefault(language, language);
        return switch (resolved) {
            case "clike"      -> Prism_clike.create(prism4j);
            case "c"          -> Prism_c.create(prism4j);
            case "cpp"        -> Prism_cpp.create(prism4j);
            case "csharp"     -> Prism_csharp.create(prism4j);
            case "java"       -> Prism_java.create(prism4j);
            case "javascript" -> Prism_javascript.create(prism4j);
            case "python"     -> Prism_python.create(prism4j);
            case "go"         -> Prism_go.create(prism4j);
            case "kotlin"     -> Prism_kotlin.create(prism4j);
            case "swift"      -> Prism_swift.create(prism4j);
            case "scala"      -> Prism_scala.create(prism4j);
            case "dart"       -> Prism_dart.create(prism4j);
            case "json"       -> Prism_json.create(prism4j);
            case "yaml"       -> Prism_yaml.create(prism4j);
            case "sql"        -> Prism_sql.create(prism4j);
            case "css"        -> Prism_css.create(prism4j);
            case "markup"     -> Prism_markup.create(prism4j);
            case "markdown"   -> Prism_markdown.create(prism4j);
            case "latex"      -> Prism_latex.create(prism4j);
            case "groovy"     -> Prism_groovy.create(prism4j);
            case "makefile"   -> Prism_makefile.create(prism4j);
            case "clojure"    -> Prism_clojure.create(prism4j);
            case "brainfuck"  -> Prism_brainfuck.create(prism4j);
            case "git"        -> Prism_git.create(prism4j);
            default           -> null;
        };
    }

    @Override
    public Set<String> languages() {
        return Set.of(
            "brainfuck", "c", "clike", "clojure", "cpp", "csharp", "css",
            "dart", "git", "go", "groovy", "java", "javascript", "json",
            "kotlin", "latex", "makefile", "markdown", "markup", "python",
            "scala", "sql", "swift", "yaml"
        );
    }
}
