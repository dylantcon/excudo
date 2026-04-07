# Excudo

**Programmatic PowerPoint creation with the precision of a human designer.**

Excudo is a Java library and CLI for creating, editing, and
reasoning about PowerPoint files at the OOXML level. The long-term goal is
agentic, end-to-end slide deck generation -- where you describe a topic and a
style, and an AI agent assembles a presentation that looks like someone *worked
hard on it*.

We are not there yet. Right now we are pouring the foundation: a library that
produces PPTX files PowerPoint accepts without repair, across every operation
the format supports. Everything else -- the agent, the visual reasoning, the
video pipeline -- depends on getting this layer right.

---

## Vision

Most AI slide-deck tools produce output that looks obviously generated. They
miss the subtle details that distinguish a hand-crafted deck: deliberate
animation choreography, intentional visual hierarchy, motion that guides
attention rather than decorating it. The best presentations communicate that
the creator *cared*. Current tools can't emulate that.

Excudo is being built to close that gap. The roadmap, in
order of dependency:

### 1. OOXML Foundation (current focus)

A PowerPoint manipulation library with **proven correctness** -- every
operation produces output structurally identical to native PowerPoint. This
means full CRUD (create, read, update, delete) coverage across six domains:

| Domain | What it covers | Status |
|--------|---------------|--------|
| **Animations** | 39 types, 13 factories, entrance/exit/emphasis/motion | Factory + XSD coverage complete; PowerPoint validation in progress |
| **Slides** | Create, delete, reorder, duplicate | Plumbing exists; systematic correctness proof needed |
| **Shapes** | Add, move, resize, style, geometry | Plumbing exists; systematic correctness proof needed |
| **Text Elements** | Content, formatting, paragraphs, lists | Plumbing exists; systematic correctness proof needed |
| **Themes** | Colors, fonts, layouts, slide masters | Plumbing exists; systematic correctness proof needed |
| **Slide Notes** | Speaker notes, icon attribution | Functional; needs correctness hardening |

Correctness is verified through a three-layer validation pipeline:

1. **ECMA-376 XSD schema validation** -- proves XML structure is spec-compliant
2. **LibreOffice headless conversion** -- catches structural issues the schema misses
3. **PowerPoint round-trip** -- the gold standard; diff raw vs. repaired output to find deviations

### 2. Abstraction Layer

Once the foundation is solid, build higher-level primitives:

- **Animation sequences** -- not individual animations, but choreographed
  motion that communicates meaning (e.g., a mouse cursor shape tracing a path
  across a slide to illustrate DOM events)
- **Layout reasoning** -- understand how shapes, text, and whitespace interact
  to create visual hierarchy
- **Theme coherence** -- enforce color theory, contrast ratios, and typographic
  consistency across an entire deck
- **Slide narrative** -- model the flow from slide to slide as a story arc,
  not just a list of pages

### 3. Visual Intelligence

Integrate vision-language models (VLMs) to:

- **Evaluate slide aesthetics** -- programmatically assess whether a slide
  *looks good*, not just whether it's structurally valid
- **Compare against references** -- heuristic scoring against known
  high-quality decks
- **Generate improvement reports** -- "the title contrast ratio is too low",
  "this animation sequence draws attention away from the key content"
- **Guide the agent** -- close the loop between generation and evaluation

### 4. Agentic Generation

The endgame: describe a topic, specify a duration, optionally state stylistic
preferences, and watch an agent build the deck. The agent would:

- Research and outline content
- Select layouts, themes, and color palettes
- Place and style shapes with deliberate visual hierarchy
- Choreograph animations that serve the narrative
- Self-evaluate using the VLM layer and iterate
- Produce output indistinguishable from expert hand-crafted work

### 5. Video Pipeline

PresentationML and DrawingML are flexible enough to describe rich visual
sequences. Combined with FFmpeg for rendering and TTS for narration, the same
infrastructure that produces slide decks can produce **videos** -- agentic,
generative video creation for any topic, assembled from programmatically
described presentation frames.

---

## Current State

~92k lines of code (87k Java, 4.3k Python). 78 test classes, all passing.

### What works today

- **Console REPL** -- load a PPTX, add slides, inject shapes, apply
  animations, save. All operations go through a persistent Java session with
  full OOXML access.
- **Animation engine** -- 39 animation types across 13 factories. Entrance,
  exit, emphasis, and motion path animations. Paragraph-level targeting.
  Oracle-validated against 156 slides of native PowerPoint output.
- **LLM integration** -- natural language editing via Claude. "Add a fade
  animation to shape 3 on slide 1." Multi-provider support (Anthropic,
  OpenAI, Ollama, Gemini).
- **3-layer validation** -- XSD schema, LibreOffice, and PowerPoint
  validation. Automated via CLI and MCP tooling.
- **Custom XML serializer** -- PowerPoint enforces XSD attribute declaration
  order, which Java's Transformer doesn't preserve. Custom serializer handles
  this correctly.

### Architecture

```
pc.py (Python CLI)
  |
  v
ConsoleEngine (Java REPL)
  |
  +-- PPTXOrchestrator ---- session management, file I/O
  |     +-- AnimationOrchestrationManager
  |     +-- ShapeOrchestrationManager
  |
  +-- SlideXMLParser ------- OOXML -> Java domain model
  |     +-- ParsedSlideData (ShapeRegistry, TimingTree, AnimationBindings)
  |
  +-- SlideXMLWriter ------- Java domain model -> OOXML
  |     +-- AnimationInjector
  |     +-- ShapeWriter
  |     +-- SlideInsertionPipeline
  |
  +-- AnimationFactoryRegistry
  |     +-- 13 factories (Fade, Fly, Wipe, Zoom, Appear, Filter-based,
  |         Rotation, Scale, Color, Emphasis, Composite, MotionPath)
  |
  +-- OOXMLSchemaValidator - ECMA-376 XSD validation
  +-- OOXMLAttributeOrder -- custom serializer for PowerPoint compliance
  +-- LLMClient ------------ multi-provider AI integration
```

### Test file organization

```
test-pptx-samples/
  generalist_test_file.pptx           # primary template (5 slides)
  all_anim_type_subtype_per_slide.pptx # oracle (156 slides, native PPT)
  animations-crud/
    native/     # reference files created in PowerPoint
    raw/        # pipeline output (gitignored)
    repaired/   # PowerPoint repair output (gitignored)
  slide-crud/{native,raw,repaired}
  shapes-crud/{native,raw,repaired}
  textel-crud/{native,raw,repaired}
  slidenotes-crud/{native,raw,repaired}
  theme-crud/{native,raw,repaired}
```

Each category follows the same pattern: generate raw output with our pipeline,
validate through all three layers, diff against native PowerPoint output to
find deviations. The `CRUDCoverageInventoryTest` reports file counts per
category as a progress dashboard.

---

## Getting Started

### Prerequisites

- Java 21 (OpenJDK)
- Python 3.8+
- LibreOffice (optional, for Layer 2 validation)

### Build and Test

```bash
python3 pc.py build
python3 pc.py test --parallel
```

### Interactive Console

```bash
python3 pc.py run console --headless

pptx> load test-pptx-samples/generalist_test_file.pptx
pptx> list
pptx> add-animation 1 3 fade in on-click
pptx> add-animation 1 4 wipe-left in after-previous
pptx> save output.pptx
```

### Validation

```bash
# XSD schema validation
python3 pc.py validate --ooxml output.pptx

# LibreOffice structural validation
python3 pc.py validate --libreoffice output.pptx

# PowerPoint validation (requires Windows machine via taildrop)
bash tools/send-to-ppt.sh output.pptx
```

---

## License

MIT
