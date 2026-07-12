package com.excudo.view.components;

import com.excudo.core.synthesis.spec.CommandSpec;
import javafx.scene.Node;

import java.util.function.Supplier;

/**
 * A reusable form payload for editing one {@link CommandSpec}: the
 * editable controls paired with a snapshot reader.
 *
 * <p>Built once by {@code SpecFormDialog.buildForm} and embedded by the
 * inline expandable row ({@code SpecRowView}), which adds {@link #node()} to
 * its content and calls {@link #read()} on commit.
 *
 * <p>{@code read} produces a fresh, immutable spec from the current
 * control values. It <b>throws</b> when those values don't form a valid
 * spec -- an unparseable number ({@link NumberFormatException}) or a
 * record's own constructor validation -- so callers can surface an inline
 * error and decline to write back rather than committing a bad value.
 *
 * @param node the editable controls (typically a {@code GridPane})
 * @param read snapshots {@code node}'s controls into a new {@code CommandSpec}
 */
public record SpecForm(Node node, Supplier<CommandSpec> read) {}
