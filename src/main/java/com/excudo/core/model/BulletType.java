package com.excudo.core.model;

/**
 * Bullet type for a text paragraph.
 */
public enum BulletType {
    NONE,
    CHARACTER,
    AUTONUMBER,
    /** Picture bullet (a:buBlip) — the glyph is an embedded image part. */
    PICTURE,
    INHERITED
}
