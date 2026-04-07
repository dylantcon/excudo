package com.excudo.core.llm.prism;


import io.noties.prism4j.Prism4j;

import static java.util.regex.Pattern.compile;
import static io.noties.prism4j.Prism4j.grammar;
import static io.noties.prism4j.Prism4j.pattern;
import static io.noties.prism4j.Prism4j.token;

@SuppressWarnings("unused")
public class Prism_brainfuck {

  
  public static Prism4j.Grammar create( Prism4j prism4j) {
    return grammar("brainfuck",
      token("pointer", pattern(compile("<|>"), false, false, "keyword")),
      token("increment", pattern(compile("\\+"), false, false, "inserted")),
      token("decrement", pattern(compile("-"), false, false, "deleted")),
      token("branching", pattern(compile("\\[|\\]"), false, false, "important")),
      token("operator", pattern(compile("[.,]"))),
      token("comment", pattern(compile("\\S+")))
    );
  }
}
