# Third-party content vendored into this repository

## `src/main/resources/geometry/presetShapeDefinitions.xml`

- **What**: The complete ECMA-376 preset shape geometry definitions
  (187 presets: `avLst` adjust defaults, `gdLst` guide formulas, and
  `pathLst` path lists for every DrawingML `prstGeom` name). This is
  the normative geometry data from **ECMA-376 Part 1, §20.1.10.56
  (ST_ShapeType)** — the machine-readable form distributed with the
  standard and republished by Apache POI.
- **Source**: Apache POI repository, path
  `poi/src/main/resources/org/apache/poi/sl/draw/geom/presetShapeDefinitions.xml`
- **URL**:
  <https://raw.githubusercontent.com/apache/poi/trunk/poi/src/main/resources/org/apache/poi/sl/draw/geom/presetShapeDefinitions.xml>
- **Retrieved**: 2026-07-12 (POI trunk commit
  `dfa53082b3e04412a4440c77862f17061cb2e97f`)
- **License**: Apache License 2.0 (the Apache POI project). The
  underlying geometry data is defined by ECMA-376, available under
  ECMA's royalty-free patent policy; the XML serialization is
  distributed by POI under Apache-2.0.
- **Local modifications**: none — the file is vendored byte-for-byte.
- **Used by**: `com.excudo.core.geometry.PresetGeometryRegistry`, which
  parses it once (lazily) into `GeometryDefinition` objects that the
  renderer evaluates per shape.
